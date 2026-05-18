import chisel3._
import chisel3.util._
import ponte.Ponte


class PicoMeshBigTop(bootBinPath: String, romBinPath: String) extends Module {
  val io= IO(new Bundle {
    val ponteTx = Output(Bool())
    val ponteRx = Input(Bool())
  })

  val ponte = Module(new Ponte(10_000_000, 9600))

  val mesh = Module(new PicoMeshBig(PicoRvConfig(), bootBinPath, romBinPath))

  ponte.io.uart.rx := io.ponteRx
  io.ponteTx := ponte.io.uart.tx
  mesh.io.pontePort <> ponte.io.port

}

object PicoMeshBigTop extends App {
  emitVerilog(new PicoMeshBigTop("build/bootloader/bootloader.bin", "build/rom/rom.bin"), Array("--target-dir", "generated"))
}