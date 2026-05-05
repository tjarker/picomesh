
import chisel3._
import chisel3.util._
import Util._

class PicoMesh(c: PicoRvConfig) extends Module {

  val io = IO(new Bundle {
    val cyc = Output(Bool())
  })
  

  val s4nocReq = Module(new S4NoC(4, new MemoryRequest))
  val s4nocResp = Module(new S4NoC(4, new MemoryResponse))

  val core = Module(new PicoNode(c.copy(
    progAddrReset = 0x2000_0000,
  ), 0))
  val mem = Module(new MemoryNode)
  val rom = Module(new RomNode(Util.Binary.load("program.bin")))


  io.cyc := core.io.networkPortReq.tx.valid


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
  s4nocReq.io.networkPort(0) <> core.io.networkPortReq
  s4nocReq.io.networkPort(2) <> rom.io.networkPortReq
  s4nocReq.io.networkPort(3) <> mem.io.networkPortReq
  s4nocResp.io.networkPort(0) <> core.io.networkPortResp
  s4nocResp.io.networkPort(2) <> rom.io.networkPortResp
  s4nocResp.io.networkPort(3) <> mem.io.networkPortResp

}