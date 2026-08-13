
import chisel3._
import Util._

class MemoryTile(id: Int, conf: s4noc.Config, schedule: Array[Array[Int]]) extends Tile(id, conf, schedule) {
  

  val mem = Module(new sky130_sram_1kbyte_1rw1r_32x256_8)

  mem.io.clk0 := clock
  mem.io.csb0 := 0.B
  mem.io.web0 := !(reqPort.rx.bits.data.write && reqPort.rx.valid)
  mem.io.wmask0 := "b1111".U
  mem.io.addr0 := reqPort.rx.bits.data.addr(9, 2)
  mem.io.din0 := reqPort.rx.bits.data.data
  val readData = mem.io.dout0

  mem.io.clk1 := 0.B.asClock
  mem.io.csb1 := 1.B // not used
  mem.io.addr1 := 0.U

  val reqReg = RegInit(0.B)

  when(reqReg && respPort.tx.ready) {
    reqReg := 0.B
  }.elsewhen(reqPort.rx.valid && !reqPort.rx.bits.data.write) {
    reqReg := 1.B
  }

  reqPort.rx.ready := (respPort.tx.ready && reqReg) || (!reqReg && reqPort.rx.bits.data.write)
  respPort.tx.expand(
    _.valid := reqReg,
    _.bits.expand(
      _.core := reqPort.rx.bits.core,
      _.data.data := readData
    )
  )

  reqPort.tx.expand(
    _.valid := 0.B,
    _.bits.expand(
      _.core := 0.U,
      _.data := DontCare
    )
  )
  respPort.rx.ready := 0.B



}