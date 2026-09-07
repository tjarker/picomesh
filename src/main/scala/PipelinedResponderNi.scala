
import chisel3._
import chisel3.util._
import s4noc.Schedule
import s4noc.SingleChannelIO
import Util._

class PipelinedResponderNi(id: Int, reqSched: Schedule, respSched: InvertedSchedule, validEndPoints: Seq[Int]) extends Module {

  val io = IO(new Bundle {
    val reqEgress = Input(new SingleChannelIO(new MemoryRequest))
    val reqLookAhead = Input(new SingleChannelIO(new MemoryRequest))
    val respIngress = Output(new SingleChannelIO(new MemoryResponse))
    val reqSlot = Input(UInt(log2Ceil(reqSched.len).W))
    val respSlot = Input(UInt(log2Ceil(respSched.len).W))

    val syncMemReq = new Bundle {
      val valid = Output(Bool())
      val addr = Output(UInt(32.W))
      val wrData = Output(UInt(32.W))
      val wr = Output(Bool())
    }

    val comMemReq = new Bundle {
      val valid = Output(Bool())
      val addr = Output(UInt(32.W))
      val wrData = Output(UInt(32.W))
      val wr = Output(Bool())
    }

    val rdData = Input(UInt(32.W))
  })

  val translationTableReqRcv = VecInit.tabulate(reqSched.len) { i =>
    val src = reqSched.timeToSource(id, i)
    if (src != -1) src.U else 0.U
  }
  val translationTableRespSend = VecInit.tabulate(respSched.len) { i =>
    val dest = respSched.timeToDest(id, i).dest
    if (dest != -1) dest.U else 0.U
  }
  val validRespSlot = VecInit.tabulate(respSched.len) { i =>
    val dest = respSched.timeToDest(id, i).dest
    if (dest != -1) true.B else false.B
  }

  val recvSlotFrom = WireDefault(translationTableReqRcv(io.reqSlot))
  val sendSlotTo = WireDefault(translationTableRespSend(io.respSlot))
  val validSendSlot = WireDefault(validRespSlot(io.respSlot))

  io.syncMemReq.expand(
    _.valid := io.reqLookAhead.valid,
    _.addr := io.reqLookAhead.data.addr,
    _.wrData := io.reqLookAhead.data.data,
    _.wr := io.reqLookAhead.data.write
  )

  io.comMemReq.expand(  
    _.valid := io.reqEgress.valid,
    _.addr := io.reqEgress.data.addr,
    _.wrData := io.reqEgress.data.data,
    _.wr := io.reqEgress.data.write
  )


  val (splitValids, splitBuffers) = validEndPoints.map { i =>
    val buffer = Reg(UInt(32.W))
    val valid = RegInit(false.B)

    val isActiveSender = sendSlotTo === i.U
    val isReceiver = recvSlotFrom === i.U

    // when a request arrives, we store it in the buffer and mark it as valid
    // when a send slot arrives we assume the packet is sent
    when(isReceiver && io.reqEgress.valid) {
      buffer := io.rdData
      valid := 1.B
    }.elsewhen(isActiveSender && validSendSlot) {
      valid := 0.B
    }

    // mask buffer output with isActiveSender
    (valid && isActiveSender, buffer & Fill(32, isActiveSender))
  }.unzip


  io.respIngress.valid := splitValids.reduce(_ || _)
  io.respIngress.data.data := splitBuffers.reduce(_ | _)


}
