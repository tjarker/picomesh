import chisel3._
import soc.ReadyValidChannelsIO
import s4noc.Entry

import Util._

class MemoryNode extends Module with NocNode {
  val io = IO(new Bundle {
    val networkPortReq = new ReadyValidChannelsIO(Entry(new MemoryRequest))
    val networkPortResp = new ReadyValidChannelsIO(Entry(new MemoryResponse))
  })

  val mem = SyncReadMem(1024, UInt(32.W))

  val readData = mem.read(io.networkPortReq.rx.bits.data.addr(31, 2))

  io.networkPortReq.rx.ready := io.networkPortResp.tx.ready
  io.networkPortResp.tx.expand(
    _.valid := RegNext(io.networkPortReq.rx.valid && !io.networkPortReq.rx.bits.data.write, 0.B),
    _.bits.expand(
      _.core := RegNext(io.networkPortReq.rx.bits.core),
      _.data.data := readData
    )
  )

  // write 
  when(io.networkPortReq.rx.valid && io.networkPortReq.rx.bits.data.write) {
    mem.write(
      io.networkPortReq.rx.bits.data.addr(31, 2),
      io.networkPortReq.rx.bits.data.data
    )
  }

  io.networkPortReq.tx.expand(
    _.valid := 0.B,
    _.bits.expand(
      _.core := 0.U,
      _.data := DontCare
    )
  )
  io.networkPortResp.rx.ready := 0.B

}

class OpenRamMemoryNode extends Module with NocNode {
  val io = IO(new Bundle {
    val networkPortReq = new ReadyValidChannelsIO(Entry(new MemoryRequest))
    val networkPortResp = new ReadyValidChannelsIO(Entry(new MemoryResponse))
  })

  val mem = Module(new sky130_sram_1kbyte_1rw1r_32x256_8)

  mem.io.clk0 := clock
  mem.io.csb0 := 0.B
  mem.io.web0 := !io.networkPortReq.rx.bits.data.write && io.networkPortReq.rx.valid
  mem.io.wmask0 := io.networkPortReq.rx.bits.data.mask
  mem.io.addr0 := io.networkPortReq.rx.bits.data.addr(9, 2)
  mem.io.din0 := io.networkPortReq.rx.bits.data.data
  val readData = mem.io.dout0

  mem.io.clk1 := 0.B.asClock
  mem.io.csb1 := 1.B // not used
  mem.io.addr1 := 0.U

  val reqReg = RegInit(0.B)

  when(reqReg && io.networkPortResp.tx.ready) {
    reqReg := 0.B
  }.elsewhen(io.networkPortReq.rx.valid && !io.networkPortReq.rx.bits.data.write) {
    reqReg := 1.B
  }

  io.networkPortReq.rx.ready := io.networkPortResp.tx.ready && reqReg
  io.networkPortResp.tx.expand(
    _.valid := reqReg,
    _.bits.expand(
      _.core := io.networkPortReq.rx.bits.core,
      _.data.data := readData
    )
  )

  io.networkPortReq.tx.expand(
    _.valid := 0.B,
    _.bits.expand(
      _.core := 0.U,
      _.data := DontCare
    )
  )
  io.networkPortResp.rx.ready := 0.B

}

class OpenRamAndRomMemoryNode extends Module with NocNode {
  val io = IO(new Bundle {
    val networkPortReq = new ReadyValidChannelsIO(Entry(new MemoryRequest))
    val networkPortResp = new ReadyValidChannelsIO(Entry(new MemoryResponse))
  })

  val mem = Module(new sky130_sram_1kbyte_1rw1r_32x256_8)

  val rom = VecInit(Util.Binary.load("build/prog/program.bin").map(_.U(32.W)))
  val romOut = RegNext(rom(io.networkPortReq.rx.bits.data.addr(9, 2)))

  val ramAccess = io.networkPortReq.rx.bits.data.addr(27, 10) === 1.U
  val romAccess = io.networkPortReq.rx.bits.data.addr(27, 10) === 0.U

  mem.io.clk0 := clock
  mem.io.csb0 := 0.B
  mem.io.web0 := !io.networkPortReq.rx.bits.data.write && io.networkPortReq.rx.valid && ramAccess
  mem.io.wmask0 := io.networkPortReq.rx.bits.data.mask
  mem.io.addr0 := io.networkPortReq.rx.bits.data.addr(9, 2)
  mem.io.din0 := io.networkPortReq.rx.bits.data.data
  val readData = mem.io.dout0

  mem.io.clk1 := 0.B.asClock
  mem.io.csb1 := 1.B // not used
  mem.io.addr1 := 0.U

  io.networkPortReq.rx.ready := io.networkPortResp.tx.ready
  io.networkPortResp.tx.expand(
    _.valid := RegNext(io.networkPortReq.rx.valid && !io.networkPortReq.rx.bits.data.write, 0.B),
    _.bits.expand(
      _.core := RegNext(io.networkPortReq.rx.bits.core),
      _.data.data := Mux(RegNext(romAccess), romOut, readData)
    )
  )

  io.networkPortReq.tx.expand(
    _.valid := 0.B,
    _.bits.expand(
      _.core := 0.U,
      _.data := DontCare
    )
  )
  io.networkPortResp.rx.ready := 0.B

}