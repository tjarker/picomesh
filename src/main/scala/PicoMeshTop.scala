import chisel3._
import chisel3.util._
import ponte.Ponte
import _root_.circt.stage.ChiselStage

class PicoMeshTop extends Module {
  val io= IO(new Bundle {
    val ponteTx = Output(Bool())
    val ponteRx = Input(Bool())
  })

  val ponte = Module(new Ponte(10_000_000, 9600))

  val mesh = Module(new PicoMesh(PicoRvConfig()))

  ponte.io.uart.rx := io.ponteRx
  io.ponteTx := ponte.io.uart.tx
  mesh.io.pontePort <> ponte.io.port

}


object PicoMeshTop extends App {
  emitVerilog(new PicoMeshTop, Array("--target-dir", "generated"))
}

class PicoMeshBigTop extends Module {
  val io= IO(new Bundle {
    val ponteTx = Output(Bool())
    val ponteRx = Input(Bool())
  })

  val ponte = Module(new Ponte(10_000_000, 9600))

  val mesh = Module(new PicoMeshBig(PicoRvConfig()))

  ponte.io.uart.rx := io.ponteRx
  io.ponteTx := ponte.io.uart.tx
  mesh.io.pontePort <> ponte.io.port

}

object PicoMeshBigTop extends App {
  emitVerilog(new PicoMeshBigTop, Array("--target-dir", "generated"))
}