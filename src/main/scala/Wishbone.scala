
import chisel3._
import chisel3.util._

class WishbonePort extends Bundle {
  val cyc = Output(Bool())
  val stb = Output(Bool())
  val we = Output(Bool())
  val sel = Output(UInt(4.W))
  val adr = Output(UInt(32.W))
  val wdata = Output(UInt(32.W))
  val rdata = Input(UInt(32.W))
  val ack = Input(Bool())
}
