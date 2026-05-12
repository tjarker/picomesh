
import chisel3._
import chisel3.util._
import Util._

class PicoMesh(c: PicoRvConfig) extends Module {

  val io = IO(new Bundle {
    val pontePort = Flipped(new ponte.PonteAccessPort)
  })
  

  val s4nocReq = Module(new S4NoC(4, new MemoryRequest))
  val s4nocResp = Module(new S4NoC(4, new MemoryResponse))

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
  

  val s4nocReq = Module(new S4NoC(16, new MemoryRequest))
  val s4nocResp = Module(new S4NoC(16, new MemoryResponse))

  val picoConf = c.copy(
    progAddrReset = 0x0000_0000,
    stackAddr = 0x60000400
  )

  for (i <- 0 until 16) {
    s4nocReq.io.networkPort(i).tx.expand(
      _.valid := 0.B,
      _.bits.expand(
        _.core := DontCare,
        _.data := DontCare
      )
    )
    s4nocReq.io.networkPort(i).rx.ready := 1.B

    s4nocResp.io.networkPort(i).tx.expand(
      _.valid := 0.B,
      _.bits.expand(
        _.core := DontCare,
        _.data := DontCare
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