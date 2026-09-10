import chisel3._
import chisel3.util._


class MemoryRequest extends Bundle {
  val addr = UInt(28.W)
  val data = UInt(32.W)
  val write = Bool()
}

class MemoryResponse(words: Int) extends Bundle {
  val data = UInt((32 * words).W)
}


class MemoryPort extends Bundle {
  val req = Decoupled(new MemoryRequest)
  val resp = Flipped(Decoupled(new MemoryResponse(1)))
}