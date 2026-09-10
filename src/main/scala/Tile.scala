
import chisel3._
import chisel3.util._
import s4noc.Const
import s4noc.ChannelIO
import s4noc.S4Router
import s4noc.Config
import s4noc.Schedule

class Tile[A <: Data, B <: Data](id: Int, conf: Config, reqSched: Schedule, respSched: InvertedSchedule, responseWords: Int) extends Module {

  val io = IO(new Bundle {
    val req = Vec(Const.NR_OF_PORTS - 1, new ChannelIO(new MemoryRequest))
    val resp = Vec(Const.NR_OF_PORTS - 1, new ChannelIO(new MemoryResponse(responseWords)))
  })

  val reqRouter = Module(new CustomS4Router(reqSched.schedule, new MemoryRequest))
  val respRouter = Module(new CustomS4Router(respSched.schedule, new MemoryResponse(responseWords)))

  val reqSlotCounter = RegInit(0.U(log2Ceil(reqSched.schedule.length).W))
  val endReqCnt = reqSlotCounter === (reqSched.schedule.length - 1).U
  reqSlotCounter := Mux(endReqCnt, 0.U, reqSlotCounter + 1.U)

  val respSlotCounter = RegInit(6.U(log2Ceil(respSched.schedule.length).W))
  val endRespCnt = respSlotCounter === (respSched.schedule.length - 1).U
  respSlotCounter := Mux(endRespCnt, 0.U, respSlotCounter + 1.U)

  reqRouter.slotCountPort := reqSlotCounter
  respRouter.slotCountPort := respSlotCounter

  // N = 0
  // E = 1
  // S = 2
  // W = 3
  // L = 4 is not connected to the io
  for (i <- 0 until Const.NR_OF_PORTS - 1) {
    reqRouter.io.ports(i) <> io.req(i)
    respRouter.io.ports(i) <> io.resp(i)
  }

  val reqLocal = reqRouter.io.ports(Const.LOCAL)
  val respLocal = respRouter.io.ports(Const.LOCAL)

}