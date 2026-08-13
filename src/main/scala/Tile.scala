
import chisel3._
import s4noc.Const
import s4noc.ChannelIO
import s4noc.S4Router
import s4noc.Config

class Tile[A <: Data, B <: Data](id: Int, conf: Config, schedule: Array[Array[Int]]) extends Module {

  val io = IO(new Bundle {
    val req = Vec(Const.NR_OF_PORTS - 1, new ChannelIO(new MemoryRequest))
    val resp = Vec(Const.NR_OF_PORTS - 1, new ChannelIO(new MemoryResponse))
  })

  val reqRouter = Module(new S4Router(schedule, new MemoryRequest))
  val respRouter = Module(new S4Router(schedule, new MemoryResponse))

  // N = 0
  // E = 1
  // S = 2
  // W = 3
  // L = 4 is not connected to the io
  for (i <- 0 until Const.NR_OF_PORTS - 1) {
    reqRouter.io.ports(i) <> io.req(i)
    respRouter.io.ports(i) <> io.resp(i)
  }

  val reqNi = Module(new CustomNetworkInterface(id, conf, new MemoryRequest))
  val respNi = Module(new CustomNetworkInterface(id, conf, new MemoryResponse))

  reqRouter.io.ports(Const.LOCAL) <> reqNi.io.local
  respRouter.io.ports(Const.LOCAL) <> respNi.io.local

  val reqPort = reqNi.io.networkPort
  val respPort = respNi.io.networkPort

}
