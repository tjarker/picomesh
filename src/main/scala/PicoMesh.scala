
import chisel3._
import chisel3.util._
import Util._
import routing.simple.SimpleNocMesh
import routing.simple.SimpleNocParams
import routing.simple.SingleRegBuffer
import routing.simple.MaskedPriorityArbiter
import routing.simple.XYRouting
import soc.ReadyValidChannelsIO
import s4noc.Entry
import routing.simple.SimpleRouterPort
import routing.Local
import routing.Coord

class PicoMesh(c: PicoRvConfig) extends Module {

  val io = IO(new Bundle {
    val pontePort = Flipped(new ponte.PonteAccessPort)
  })
  

  val s4nocReq = Module(new CustomS4NoC(4, new MemoryRequest))
  val s4nocResp = Module(new CustomS4NoC(4, new MemoryResponse))

  val picoConf = c.copy(
    progAddrReset = 0x2000_0000,
    stackAddr = 0x30000100
  )

  val core0 = Module(new PicoNode(picoConf))
  val core1 = Module(new PicoNode(picoConf))

  core0.coreId := 0.U
  core1.coreId := 1.U
  val mem = Module(new OpenRamAndRomMemoryNode)
  val accessNode = Module(new AccessNode)

  io.pontePort <> accessNode.io.pontePort


  for (i <- 0 until 2 * 2) {
    s4nocReq.io.networkPort(i).tx.expand(
      _.valid := 0.B,
      _.bits.expand(
        _.core := 0.U,
        _.data := DontCare
      )
    )
    s4nocReq.io.networkPort(i).rx.ready := 1.B

    s4nocResp.io.networkPort(i).tx.expand(
      _.valid := 0.B,
      _.bits.expand(
        _.core := 0.U,
        _.data := DontCare
      )
    )
    s4nocResp.io.networkPort(i).rx.ready := 1.B
  }

  // connect core to s4nocReq
  s4nocReq.io.networkPort(0) <> core0.io.networkPortReq
  s4nocResp.io.networkPort(0) <> core0.io.networkPortResp

  s4nocReq.io.networkPort(1) <> core1.io.networkPortReq
  s4nocResp.io.networkPort(1) <> core1.io.networkPortResp

  s4nocReq.io.networkPort(2) <> mem.io.networkPortReq
  s4nocResp.io.networkPort(2) <> mem.io.networkPortResp


  s4nocReq.io.networkPort(3) <> accessNode.io.networkPortReq
  s4nocResp.io.networkPort(3) <> accessNode.io.networkPortResp

}


class PicoMeshBig(c: PicoRvConfig) extends Module {

  val io = IO(new Bundle {
    val pontePort = Flipped(new ponte.PonteAccessPort)
  })
  

  val s4nocReq = Module(new CustomS4NoC(16, new MemoryRequest))
  val s4nocResp = Module(new CustomS4NoC(16, new MemoryResponse))

  val picoConf = c.copy(
    progAddrReset = 0x0000_0000,
    stackAddr = 0x60000400
  )

  for (i <- Seq(1,2,3,12,13,14,15)) {
    s4nocReq.io.networkPort(i).tx.expand(
      _.valid := 0.B,
      _.bits.expand(
        _.core := DontCare,
        _.data := DontCare
      )
    )
    s4nocReq.io.networkPort(i).rx.ready := 1.B

    s4nocResp.io.networkPort(i).tx.expand(
      _.valid := s4nocReq.io.networkPort(i).rx.valid && !s4nocReq.io.networkPort(i).rx.bits.data.write,
      _.bits.expand(
        _.core := s4nocReq.io.networkPort(i).rx.bits.core,
        _.data.data := 0.U
      )
    )
    s4nocResp.io.networkPort(i).rx.ready := 1.B
  }

  val cores = Seq.fill(6)(Module(new PicoNode(picoConf)))

  val coreToCoreidMap = Map(
    0 -> 4.U,
    1 -> 7.U,
    2 -> 8.U,
    3 -> 9.U,
    4 -> 10.U,
    5 -> 11.U
  )

  for ((i, coreId) <- coreToCoreidMap) {
    cores(i).coreId := coreId
    s4nocReq.io.networkPort(coreId) <> cores(i).io.networkPortReq
    s4nocResp.io.networkPort(coreId) <> cores(i).io.networkPortResp
  }

  // val romNode = Module(new RomNode(Util.Binary.load("build/prog/program.bin")))
  val memLow = Module(new OpenRamMemoryNode)
  val memHigh = Module(new OpenRamAndRomMemoryNode)
  val accessNode = Module(new AccessNode)
  io.pontePort <> accessNode.io.pontePort


  // connect core to s4nocReq
  s4nocReq.io.networkPort(0) <> accessNode.io.networkPortReq
  s4nocResp.io.networkPort(0) <> accessNode.io.networkPortResp

  // s4nocReq.io.networkPort(12) <> romNode.io.networkPortReq
  // s4nocResp.io.networkPort(12) <> romNode.io.networkPortResp

  s4nocReq.io.networkPort(5) <> memLow.io.networkPortReq
  s4nocResp.io.networkPort(5) <> memLow.io.networkPortResp

  s4nocReq.io.networkPort(6) <> memHigh.io.networkPortReq
  s4nocResp.io.networkPort(6) <> memHigh.io.networkPortResp

}


object PicoMesh extends App {
  emitVerilog(new PicoMesh(PicoRvConfig.small), Array("--target-dir", "generated"))
}


class PortAdapter[T <: Data](c: Coord)(implicit p: SimpleNocParams[T]) extends Module {
  val io = IO(new Bundle {
    val s4noc = Flipped(new ReadyValidChannelsIO(Entry(p.payloadType)))
    val slim = Flipped(new SimpleRouterPort(c, Local))
  })

  val reqX = io.s4noc.tx.bits.core(1,0)
  val reqY = io.s4noc.tx.bits.core(3,2)

  io.slim.ingress.valid := io.s4noc.tx.valid
  io.s4noc.tx.ready := io.slim.ingress.ready
  io.slim.ingress.bits.expand(
    _.dest.x := reqX,
    _.dest.y := reqY,
    _.dest.southbound := reqY > c.y.U,
    _.dest.eastbound := reqX > c.x.U,
    _.payload := io.s4noc.tx.bits.data
  )

  io.s4noc.rx.valid := io.slim.egress.valid
  io.slim.egress.ready := io.s4noc.rx.ready
  io.s4noc.rx.bits.expand(
    _.core := io.slim.egress.bits.dest.y ## io.slim.egress.bits.dest.x,
    _.data := io.slim.egress.bits.payload
  )

}

class PicoMeshSlimflit extends Module {
  val io = IO(new Bundle {
    val pontePort = Flipped(new ponte.PonteAccessPort)
  })

  val picoConf = PicoRvConfig.small.copy(
    progAddrReset = 0x0000_0000,
    stackAddr = 0x60000400
  )

  val reqNocParams = SimpleNocParams(
    nx = 4,
    ny = 3,
    payloadGen = () => new MemoryRequest,
    bufferFactory = SingleRegBuffer,
    arbiterFactory = MaskedPriorityArbiter,
    routingPolicy = XYRouting,
    wrappedTopology = false,
    debug = false
  )
  val reqNoc = Module(new SimpleNocMesh()(reqNocParams))

  val respNocParams = SimpleNocParams(
    nx = 4,
    ny = 3,
    payloadGen = () => new MemoryResponse,
    bufferFactory = SingleRegBuffer,
    arbiterFactory = MaskedPriorityArbiter,
    routingPolicy = XYRouting,
    wrappedTopology = false,
    debug = false
  )
  val respNoc = Module(new SimpleNocMesh()(respNocParams))


  for (i <- Seq(1,2,3)) {
    val c = Coord(i % 4, i / 4)


    reqNoc.ports(c).ingress.expand(
      _.valid := 0.B,
      _.bits := DontCare
    )
    reqNoc.ports(c).egress.ready := 1.B

    respNoc.ports(c).ingress.expand(
      _.valid := 0.B,
      _.bits := DontCare
    )
    respNoc.ports(c).egress.ready := 1.B
  }

  val cores = Seq.fill(6)(Module(new PicoNode(picoConf)))

  val coreToCoreidMap = Seq(
    0 -> 4,
    1 -> 7,
    2 -> 8,
    3 -> 9,
    4 -> 10,
    5 -> 11
  )

  for ((coreId, coreTag) <- coreToCoreidMap) {

    val c = Coord(coreTag % 4, coreTag / 4)
    println(s"Connecting core $coreId to coord $c")
    cores(coreId).coreId := coreTag.U

    val reqAdapter = Module(new PortAdapter(c)(reqNocParams))
    reqAdapter.io.s4noc <> cores(coreId).io.networkPortReq
    reqNoc.ports(c) <> reqAdapter.io.slim

    val respAdapter = Module(new PortAdapter(c)(respNocParams))
    respAdapter.io.s4noc <> cores(coreId).io.networkPortResp
    respNoc.ports(c) <> respAdapter.io.slim

  }

  // val romNode = Module(new RomNode(Util.Binary.load("build/prog/program.bin")))
  val memLow = Module(new OpenRamMemoryNode)
  val memHigh = Module(new OpenRamAndRomMemoryNode)
  val accessNode = Module(new AccessNode)
  io.pontePort <> accessNode.io.pontePort


  // connect core to s4nocReq
  val accessNodeCoord = Coord(0,0)
  val accessNodeReqAdapter = Module(new PortAdapter(accessNodeCoord)(reqNocParams))
  accessNodeReqAdapter.io.s4noc <> accessNode.io.networkPortReq
  reqNoc.ports(accessNodeCoord) <> accessNodeReqAdapter.io.slim
  val accessNodeRespAdapter = Module(new PortAdapter(accessNodeCoord)(respNocParams))
  accessNodeRespAdapter.io.s4noc <> accessNode.io.networkPortResp
  respNoc.ports(accessNodeCoord) <> accessNodeRespAdapter.io.slim

  val memLowCoord = Coord(1,1)
  val memLowReqAdapter = Module(new PortAdapter(memLowCoord)(reqNocParams))
  memLowReqAdapter.io.s4noc <> memLow.io.networkPortReq
  reqNoc.ports(memLowCoord) <> memLowReqAdapter.io.slim
  val memLowRespAdapter = Module(new PortAdapter(memLowCoord)(respNocParams))
  memLowRespAdapter.io.s4noc <> memLow.io.networkPortResp
  respNoc.ports(memLowCoord) <> memLowRespAdapter.io.slim

  val memHighCoord = Coord(2,1)
  val memHighReqAdapter = Module(new PortAdapter(memHighCoord)(reqNocParams))
  memHighReqAdapter.io.s4noc <> memHigh.io.networkPortReq
  reqNoc.ports(memHighCoord) <> memHighReqAdapter.io.slim
  val memHighRespAdapter = Module(new PortAdapter(memHighCoord)(respNocParams))
  memHighRespAdapter.io.s4noc <> memHigh.io.networkPortResp
  respNoc.ports(memHighCoord) <> memHighRespAdapter.io.slim


}