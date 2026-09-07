import chisel3._
import chisel3.util._
import s4noc.Schedule
import s4noc.SingleChannelIO

class BlockingRequesterNi(id: Int, reqSched: Schedule, respSched: InvertedSchedule) extends Module {

  val io = IO(new Bundle {
    val reqIngress = Output(new SingleChannelIO(new MemoryRequest))
    val respEgress = Input(new SingleChannelIO(new MemoryResponse))
    val reqSlot = Input(UInt(log2Ceil(reqSched.len).W))
    val respSlot = Input(UInt(log2Ceil(respSched.len).W))

    val wb = Flipped(new WishbonePort)
  })

  val translationTableReqSend = VecInit.tabulate(reqSched.len) { i =>
    val dest = reqSched.timeToDest(id, i).dest
    if (dest != -1) dest.U else 0.U
  }
  val validReqSlot = VecInit.tabulate(reqSched.len) { i =>
    val dest = reqSched.timeToDest(id, i).dest
    if (dest != -1) true.B else false.B
  }
  val translationTableRespRcv = VecInit.tabulate(respSched.len) { i =>
    val src = respSched.timeToSource(id, i)
    if (src != -1) src.U else 0.U
  }

  val sendSlotTo = translationTableReqSend(io.reqSlot)
  val validSendSlot = validReqSlot(io.reqSlot)
  val recvSlotFrom = translationTableRespRcv(io.respSlot)

  val destId = io.wb.adr(31, 28)

  object State extends ChiselEnum {
    val Idle, WaitForReqSlot, WaitForResp = Value
  }

  val stateReg = RegInit(State.Idle)

  val sendSlotMatch = validSendSlot && sendSlotTo === destId

  val receiveSlotMatchAndValid = io.respEgress.valid && recvSlotFrom === destId

  io.reqIngress.valid := 0.B
  io.reqIngress.data.addr := io.wb.adr(27, 0)
  io.reqIngress.data.data := io.wb.wdata
  io.reqIngress.data.write := io.wb.we
  io.wb.rdata := io.respEgress.data.data
  io.wb.ack := 0.B


  switch(stateReg) {
    is(State.Idle) {
      when(io.wb.cyc) {
        stateReg := State.WaitForReqSlot
      }
      when(io.wb.cyc) {
        io.reqIngress.valid := sendSlotMatch
        when(sendSlotMatch) {
          stateReg := State.WaitForResp
        }.otherwise {
          stateReg := State.WaitForReqSlot
        }
      }
    }
    is(State.WaitForReqSlot) {
      io.reqIngress.valid := sendSlotMatch
      when(sendSlotMatch) {
        stateReg := State.WaitForResp
      }
    }
    is(State.WaitForResp) {
      when(receiveSlotMatchAndValid) {
        stateReg := State.Idle
        io.wb.ack := 1.B
      }.otherwise {
        stateReg := State.WaitForResp
      }
    }
  }



}
