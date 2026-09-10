
import chisel3._
import chisel3.util._
import Util._
import spi.SerialSpiDriver.s
import os.read

case class PicoRvConfig(
  enableCounters: Boolean = true,
  enableCounters64: Boolean = true,
  enableRegs16_31: Boolean = true,
  enableRegsDualPort: Boolean = true,
  twoStageShift: Boolean = true,
  barrelShifter: Boolean = false,
  twoCycleCompare: Boolean = false,
  twoCycleAlu: Boolean = false,
  compressedIsa: Boolean = false,
  catchMisaligned: Boolean = true,
  catchIllegalInstruction: Boolean = true,
  enablePcpi: Boolean = false,
  enableMul: Boolean = false,
  enableFastMul: Boolean = false,
  enableDiv: Boolean = false,
  enableIrq: Boolean = false,
  enableIrqQregs: Boolean = true,
  enableIrqTimer: Boolean = true,
  enableTrace: Boolean = false,
  regsInitZero: Boolean = false,
  maskedIrq: Int = 0,
  latchedIrq: Int = 0xFFFFFFFF,
  progAddrReset: Int = 0x00000000,
  progAddrIrq: Int = 0x00000010,
  stackAddr: Int = 0xFFFFFFFF
)

object PicoRvConfig {
  def default: PicoRvConfig = PicoRvConfig()

  def small: PicoRvConfig = default.copy(
    enableCounters = true,
    enableCounters64 = false,
    enableRegs16_31 = false,
    enableRegsDualPort = false,
    twoStageShift = false,
    barrelShifter = true,
    twoCycleCompare = false,
    twoCycleAlu = false,
    enableIrqQregs = false,
    catchMisaligned = false,
    catchIllegalInstruction = false,
    enableIrqTimer = false,
  )
}

object PicoRvWb {
  implicit class BoolToInt(val b: Boolean) extends AnyVal {
    def toInt: Int = if (b) 1 else 0
  }
}

import PicoRvWb._


/**
  * A wrapper around picorv32_wb with a local scratchpad (4x32 bit), boot address register, and reset control register.
  * 
  * Internal memory map (local to each core):
  * 0xFFFF_0000: Core ID (read-only)
  * 0x?100_0000: Scratchpad[0]
  * 0x?100_0004: Scratchpad[1]
  * 0x?100_0008: Scratchpad[2]
  * 0x?100_000C: Scratchpad[3]
  * 0x?100_0010: Boot Address (read/write)
  * 0x?100_0014: Config (read/write)
  * 
  * The `remoteWb` interface allows external accesses to the core's local memory map. External accesses take priority over the core's own accesses.
  *
  * @param c config
  */
class PicoRvWb(id: Int, c: PicoRvConfig) extends Module {

  val io = IO(new Bundle {
    val wb = new WishbonePort
    val remoteWb = Flipped(new WishbonePort)
    val barrierArrived = Output(Bool())
    val barrierRelease = Input(Bool())
  })

  io.barrierArrived := 0.B // default

  /* 
      0xFFFF_0000: Core ID (read-only)
      0xFFFF_0004: Barrier (write-only, blocks until all cores have writte to their local barrier register)
      0xFFFF_0008: Barrier enable (read/write, 1=enable, 0=disable)
      0x?100_0000: Scratchpad[0]
      0x?100_0004: Scratchpad[1]
      0x?100_0008: Scratchpad[2]
      0x?100_000C: Scratchpad[3]
      0x?100_0010: Boot Address (read/write)
      0x?000_0014: Config (read/write)
   */

  val core = Module(new PicoRvWbBlackBox(c))

  val configReg = RegInit(0.U(1.W))

  val scratchPad = Mem(4, UInt(32.W))

  val bootAddr = RegInit(0x0800_0000.U(32.W))

  val barrierEn = RegInit(0.B)

  core.io.pcpi_wait := 0.B
  core.io.pcpi_wr := 0.B
  core.io.pcpi_ready := 0.B
  core.io.pcpi_rd := 0.U
  core.io.wb_clk_i := clock
  core.io.wb_rst_i := reset.asBool || configReg(0) 
  core.io.irq := 0.U


  val isLocalAccess = core.io.wbm_adr_o(31, 28) === id.U || core.io.wbm_adr_o(31, 16) === 0xFFFFL.U


  // pico to remote wishbone interface
  io.wb.cyc := !isLocalAccess && core.io.wbm_cyc_o
  io.wb.stb := !isLocalAccess && core.io.wbm_cyc_o
  io.wb.we := core.io.wbm_we_o
  io.wb.adr := core.io.wbm_adr_o
  io.wb.wdata := core.io.wbm_dat_o
  io.wb.sel := core.io.wbm_sel_o


  // access from remote core
  val remoteScratchPadAccess = io.remoteWb.adr(27, 4) === 0x100_000.U
  val remoteBootAddrAccess = io.remoteWb.adr(27, 0) === 0x100_0010.U
  val remoteConfigAccess = io.remoteWb.adr(27, 0) === 0x100_0014.U

  io.remoteWb.ack := io.remoteWb.cyc
  io.remoteWb.rdata := MuxCase(0.U, Seq(
    remoteScratchPadAccess -> scratchPad.read(io.remoteWb.adr(3, 2)),
    remoteBootAddrAccess -> bootAddr,
    remoteConfigAccess -> configReg
  ))


  // local access from pico core
  val scratchPadAccess = core.io.wbm_adr_o(27, 4) === 0x100_000.U
  val coreIdAccess = core.io.wbm_adr_o(31, 0) === 0xFFFF_0000L.U
  val bootAddrAccess = core.io.wbm_adr_o(27, 0) === 0x100_0010.U
  val configAccess = core.io.wbm_adr_o(27, 0) === 0x100_0014.U
  val barrierAccess = core.io.wbm_adr_o(31, 0) === 0xFFFF_0004L.U
  val barrierEnAccess = core.io.wbm_adr_o(31, 0) === 0xFFFF_0008L.U


  val readData = MuxCase(0.U, Seq(
    scratchPadAccess -> scratchPad.read(core.io.wbm_adr_o(3, 2)),
    coreIdAccess -> id.U,
    bootAddrAccess -> bootAddr,
    configAccess -> configReg,
    barrierEnAccess -> barrierEn
  ))
  core.io.wbm_dat_i := Mux(isLocalAccess, readData, io.wb.rdata)
  

  // writes to local state
  // remote write takes priority over local write
  val remoteWrite = io.remoteWb.cyc && io.remoteWb.we
  val picoLocalWrite = core.io.wbm_cyc_o && isLocalAccess && core.io.wbm_we_o

  when(remoteWrite && remoteScratchPadAccess) {
    scratchPad.write(io.remoteWb.adr(3, 2), io.remoteWb.wdata)
  }.elsewhen(picoLocalWrite && scratchPadAccess) {
    scratchPad.write(core.io.wbm_adr_o(3, 2), core.io.wbm_dat_o)
  }

  when(remoteWrite && remoteBootAddrAccess) {
    bootAddr := io.remoteWb.wdata
  }.elsewhen(picoLocalWrite && bootAddrAccess) {
    bootAddr := core.io.wbm_dat_o
  }

  when(remoteWrite && remoteConfigAccess) {
    configReg := io.remoteWb.wdata
  }.elsewhen(picoLocalWrite && configAccess) {
    configReg := core.io.wbm_dat_o
  }

  when(picoLocalWrite && barrierEnAccess) {
    barrierEn := core.io.wbm_dat_o(0)
  }

  io.barrierArrived := Mux(barrierEn, picoLocalWrite && barrierAccess, 0.B)

  core.io.wbm_ack_i := Mux(barrierEn && picoLocalWrite && barrierAccess, io.barrierRelease, Mux(isLocalAccess, core.io.wbm_cyc_o, io.wb.ack))

}

class PicoRvWbBlackBox(c: PicoRvConfig) extends BlackBox(Map(
  "ENABLE_COUNTERS" -> c.enableCounters.toInt,
  "ENABLE_COUNTERS64" -> c.enableCounters64.toInt,
  "ENABLE_REGS_16_31" -> c.enableRegs16_31.toInt,
  "ENABLE_REGS_DUALPORT" -> c.enableRegsDualPort.toInt,
  "TWO_STAGE_SHIFT" -> c.twoStageShift.toInt,
  "BARREL_SHIFTER" -> c.barrelShifter.toInt,
  "TWO_CYCLE_COMPARE" -> c.twoCycleCompare.toInt,
  "TWO_CYCLE_ALU" -> c.twoCycleAlu.toInt,
  "COMPRESSED_ISA" -> c.compressedIsa.toInt,
  "CATCH_MISALIGN" -> c.catchMisaligned.toInt,
  "CATCH_ILLINSN" -> c.catchIllegalInstruction.toInt,
  "ENABLE_PCPI" -> c.enablePcpi.toInt,
  "ENABLE_MUL" -> c.enableMul.toInt,
  "ENABLE_FAST_MUL" -> c.enableFastMul.toInt,
  "ENABLE_DIV" -> c.enableDiv.toInt,
  "ENABLE_IRQ" -> c.enableIrq.toInt,
  "ENABLE_IRQ_QREGS" -> c.enableIrqQregs.toInt,
  "ENABLE_IRQ_TIMER" -> c.enableIrqTimer.toInt,
  "ENABLE_TRACE" -> c.enableTrace.toInt,
  "REGS_INIT_ZERO" -> c.regsInitZero.toInt,
  "MASKED_IRQ" -> c.maskedIrq,
  "LATCHED_IRQ" -> c.latchedIrq,
  "PROGADDR_RESET" -> c.progAddrReset,
  "PROGADDR_IRQ" -> c.progAddrIrq,
  "STACKADDR" -> c.stackAddr

)) with HasBlackBoxPath {
  val io = IO(new Bundle {

    val trap = Output(Bool())

    val wb_rst_i = Input(Bool())
    val wb_clk_i = Input(Clock())

    val wbm_cyc_o = Output(Bool())
    val wbm_stb_o = Output(Bool())
    val wbm_we_o = Output(Bool())
    val wbm_sel_o = Output(UInt(4.W))
    val wbm_adr_o = Output(UInt(32.W))
    val wbm_dat_o = Output(UInt(32.W))
    val wbm_dat_i = Input(UInt(32.W))
    val wbm_ack_i = Input(Bool())

    val pcpi_valid = Output(Bool())
    val pcpi_insn = Output(UInt(32.W))
    val pcpi_rs1 = Output(UInt(32.W))
    val pcpi_rs2 = Output(UInt(32.W))
    val pcpi_wr = Input(Bool())
    val pcpi_rd = Input(UInt(32.W))
    val pcpi_wait = Input(Bool())
    val pcpi_ready = Input(Bool())

    val irq = Input(UInt(32.W))
    val eoi = Output(UInt(32.W))

    val trace_valid = Output(Bool())
    val trace_data = Output(UInt(36.W))

    val mem_instr = Output(Bool())
  })

  override val desiredName: String = "picorv32_wb"
  addPath("src/verilog/picorv32.v")
}


class PicoRvBlackBox(c: PicoRvConfig) extends BlackBox(Map(
  "ENABLE_COUNTERS" -> c.enableCounters.toInt,
  "ENABLE_COUNTERS64" -> c.enableCounters64.toInt,
  "ENABLE_REGS_16_31" -> c.enableRegs16_31.toInt,
  "ENABLE_REGS_DUALPORT" -> c.enableRegsDualPort.toInt,
  "LATCHED_MEM_RDATA" -> 0,
  "TWO_STAGE_SHIFT" -> c.twoStageShift.toInt,
  "BARREL_SHIFTER" -> c.barrelShifter.toInt,
  "TWO_CYCLE_COMPARE" -> c.twoCycleCompare.toInt,
  "TWO_CYCLE_ALU" -> c.twoCycleAlu.toInt,
  "COMPRESSED_ISA" -> c.compressedIsa.toInt,
  "CATCH_MISALIGN" -> c.catchMisaligned.toInt,
  "CATCH_ILLINSN" -> c.catchIllegalInstruction.toInt,
  "ENABLE_PCPI" -> c.enablePcpi.toInt,
  "ENABLE_MUL" -> c.enableMul.toInt,
  "ENABLE_FAST_MUL" -> c.enableFastMul.toInt,
  "ENABLE_DIV" -> c.enableDiv.toInt,
  "ENABLE_IRQ" -> c.enableIrq.toInt,
  "ENABLE_IRQ_QREGS" -> c.enableIrqQregs.toInt,
  "ENABLE_IRQ_TIMER" -> c.enableIrqTimer.toInt,
  "ENABLE_TRACE" -> c.enableTrace.toInt,
  "REGS_INIT_ZERO" -> c.regsInitZero.toInt,
  "MASKED_IRQ" -> c.maskedIrq,
  "LATCHED_IRQ" -> c.latchedIrq,
  "PROGADDR_RESET" -> c.progAddrReset,
  "PROGADDR_IRQ" -> c.progAddrIrq,
  "STACKADDR" -> c.stackAddr

)) with HasBlackBoxPath {
  val io = IO(new Bundle {

    val trap = Output(Bool())

    val clk = Input(Clock())
    val resetn = Input(Bool())

    val mem_valid = Output(Bool())
    val mem_instr = Output(Bool())
    val mem_ready = Input(Bool())

    val mem_addr = Output(UInt(32.W))
    val mem_wdata = Output(UInt(32.W))
    val mem_wstrb = Output(UInt(4.W))
    val mem_rdata = Input(UInt(32.W))

    val mem_la_read = Output(Bool())
    val mem_la_write = Output(Bool())
    val mem_la_addr = Output(UInt(32.W))
    val mem_la_wdata = Output(UInt(32.W))
    val mem_la_wstrb = Output(UInt(4.W))

    val pcpi_valid = Output(Bool())
    val pcpi_insn = Output(UInt(32.W))
    val pcpi_rs1 = Output(UInt(32.W))
    val pcpi_rs2 = Output(UInt(32.W))
    val pcpi_wr = Input(Bool())
    val pcpi_rd = Input(UInt(32.W))
    val pcpi_wait = Input(Bool())
    val pcpi_ready = Input(Bool())

    val irq = Input(UInt(32.W))
    val eoi = Output(UInt(32.W))

    val trace_valid = Output(Bool())
    val trace_data = Output(UInt(36.W))
  })

  override val desiredName: String = "picorv32"
  addPath("src/verilog/picorv32.v")
}


/**
  * A wrapper around picorv32_wb with a local scratchpad (4x32 bit), boot address register, and reset control register.
  * 
  * Internal memory map (local to each core):
  * 0xFFFF_0000: Core ID (read-only)
  * 0x?100_0000: Scratchpad[0]
  * 0x?100_0004: Scratchpad[1]
  * 0x?100_0008: Scratchpad[2]
  * 0x?100_000C: Scratchpad[3]
  * 0x?100_0010: Boot Address (read/write)
  * 0x?100_0014: Config (read/write)
  * 
  * The `remoteWb` interface allows external accesses to the core's local memory map. External accesses take priority over the core's own accesses.
  *
  * @param c config
  */
class PicoRv(id: Int, c: PicoRvConfig) extends Module {

  val io = IO(new Bundle {
    val wb = new WishbonePort
    val remoteWb = Flipped(new WishbonePort)
    val barrierArrived = Output(Bool())
    val barrierRelease = Input(Bool())
  })

  io.barrierArrived := 0.B // default

  /* 
      0xFFFF_0000: Core ID (read-only)
      0xFFFF_0004: Barrier (write-only, blocks until all cores have writte to their local barrier register)
      0xFFFF_0008: Barrier enable (read/write, 1=enable, 0=disable)
      0x?100_0000: Scratchpad[0]
      0x?100_0004: Scratchpad[1]
      0x?100_0008: Scratchpad[2]
      0x?100_000C: Scratchpad[3]
      0x?100_0010: Boot Address (read/write)
      0x?000_0014: Config (read/write)
   */

  val core = Module(new PicoRvBlackBox(c))

  val configReg = RegInit(0.U(1.W))

  val scratchPad = Mem(4, UInt(32.W))

  val bootAddr = RegInit(0x0800_0000.U(32.W))

  val barrierEn = RegInit(0.B)

  core.io.pcpi_wait := 0.B
  core.io.pcpi_wr := 0.B
  core.io.pcpi_ready := 0.B
  core.io.pcpi_rd := 0.U
  core.io.clk := clock
  core.io.resetn := !(reset.asBool || configReg(0))
  core.io.irq := 0.U


  val isLocalAccess = core.io.mem_addr(31, 28) === id.U || core.io.mem_addr(31, 16) === 0xFFFFL.U


  // pico to remote wishbone interface
  io.wb.cyc := !isLocalAccess && core.io.mem_valid
  io.wb.stb := !isLocalAccess && core.io.mem_valid
  io.wb.we := core.io.mem_wstrb =/= 0.U
  io.wb.adr := core.io.mem_addr
  io.wb.wdata := core.io.mem_wdata
  io.wb.sel := core.io.mem_wstrb


  // access from remote core
  val remoteScratchPadAccess = io.remoteWb.adr(27, 4) === 0x100_000.U
  val remoteBootAddrAccess = io.remoteWb.adr(27, 0) === 0x100_0010.U
  val remoteConfigAccess = io.remoteWb.adr(27, 0) === 0x100_0014.U

  io.remoteWb.ack := io.remoteWb.cyc
  io.remoteWb.rdata := MuxCase(0.U, Seq(
    remoteScratchPadAccess -> scratchPad.read(io.remoteWb.adr(3, 2)),
    remoteBootAddrAccess -> bootAddr,
    remoteConfigAccess -> configReg
  ))


  // local access from pico core
  val scratchPadAccess = core.io.mem_addr(27, 4) === 0x100_000.U
  val coreIdAccess = core.io.mem_addr(31, 0) === 0xFFFF_0000L.U
  val bootAddrAccess = core.io.mem_addr(27, 0) === 0x100_0010.U
  val configAccess = core.io.mem_addr(27, 0) === 0x100_0014.U
  val barrierAccess = core.io.mem_addr(31, 0) === 0xFFFF_0004L.U
  val barrierEnAccess = core.io.mem_addr(31, 0) === 0xFFFF_0008L.U


  val readData = MuxCase(0.U, Seq(
    scratchPadAccess -> scratchPad.read(core.io.mem_addr(3, 2)),
    coreIdAccess -> id.U,
    bootAddrAccess -> bootAddr,
    configAccess -> configReg,
    barrierEnAccess -> barrierEn
  ))
  core.io.mem_rdata := Mux(isLocalAccess, readData, io.wb.rdata)
  

  // writes to local state
  // remote write takes priority over local write
  val remoteWrite = io.remoteWb.cyc && io.remoteWb.we
  val picoLocalWrite = core.io.mem_valid && isLocalAccess && core.io.mem_wstrb =/= 0.U

  when(remoteWrite && remoteScratchPadAccess) {
    scratchPad.write(io.remoteWb.adr(3, 2), io.remoteWb.wdata)
  }.elsewhen(picoLocalWrite && scratchPadAccess) {
    scratchPad.write(core.io.mem_addr(3, 2), core.io.mem_wdata)
  }

  when(remoteWrite && remoteBootAddrAccess) {
    bootAddr := io.remoteWb.wdata
  }.elsewhen(picoLocalWrite && bootAddrAccess) {
    bootAddr := core.io.mem_wdata
  }

  when(remoteWrite && remoteConfigAccess) {
    configReg := io.remoteWb.wdata
  }.elsewhen(picoLocalWrite && configAccess) {
    configReg := core.io.mem_wdata
  }

  when(picoLocalWrite && barrierEnAccess) {
    barrierEn := core.io.mem_wdata(0)
  }

  io.barrierArrived := Mux(barrierEn, picoLocalWrite && barrierAccess, 0.B)

  core.io.mem_ready := Mux(barrierEn && picoLocalWrite && barrierAccess, io.barrierRelease, Mux(isLocalAccess, 1.B, io.wb.ack))

}


/**
  * A wrapper around picorv32_wb with a local scratchpad (4x32 bit), boot address register, and reset control register.
  * 
  * Internal memory map (local to each core):
  * 0xFFFF_0000: Core ID (read-only)
  * 0x?100_0000: Scratchpad[0]
  * 0x?100_0004: Scratchpad[1]
  * 0x?100_0008: Scratchpad[2]
  * 0x?100_000C: Scratchpad[3]
  * 0x?100_0010: Boot Address (read/write)
  * 0x?100_0014: Config (read/write)
  * 
  * The `remoteWb` interface allows external accesses to the core's local memory map. External accesses take priority over the core's own accesses.
  *
  * @param c config
  */
class PrefetchPicoRv(id: Int, c: PicoRvConfig) extends Module {

  val io = IO(new Bundle {
    val wb = new WishbonePort(1)
    val instrPort = new Bundle {
      val valid = Output(Bool())
      val accepted = Input(Bool())
      val ready = Input(Bool())
      val addr = Output(UInt(32.W))
      val bundle = Input(UInt(128.W))
    }
    val remoteWb = Flipped(new WishbonePort)
    val barrierArrived = Output(Bool())
    val barrierRelease = Input(Bool())
    val instrCheck = new Bundle {
      val addr = Output(UInt(32.W))
      val instr = Input(UInt(32.W))
    }
  })

  io.barrierArrived := 0.B // default

  /* 
      0xFFFF_0000: Core ID (read-only)
      0xFFFF_0004: Barrier (write-only, blocks until all cores have writte to their local barrier register)
      0xFFFF_0008: Barrier enable (read/write, 1=enable, 0=disable)
      0x?100_0000: Scratchpad[0]
      0x?100_0004: Scratchpad[1]
      0x?100_0008: Scratchpad[2]
      0x?100_000C: Scratchpad[3]
      0x?100_0010: Boot Address (read/write)
      0x?000_0014: Config (read/write)
   */

  val core = Module(new PicoRvBlackBox(c))

  val prefetcher = Module(new Prefetcher())

  val configReg = RegInit(0.U(1.W))

  val scratchPad = Mem(4, UInt(32.W))

  val bootAddr = RegInit(0x0800_0000.U(32.W))

  val barrierEn = RegInit(0.B)

  core.io.pcpi_wait := 0.B
  core.io.pcpi_wr := 0.B
  core.io.pcpi_ready := 0.B
  core.io.pcpi_rd := 0.U
  core.io.clk := clock
  core.io.resetn := !(reset.asBool || configReg(0))
  core.io.irq := 0.U


  val isLocalAccess = core.io.mem_addr(31, 28) === id.U || core.io.mem_addr(31, 16) === 0xFFFFL.U


  // pico to remote wishbone interface
  io.wb.cyc := !isLocalAccess && core.io.mem_valid && !core.io.mem_instr
  io.wb.stb := !isLocalAccess && core.io.mem_valid && !core.io.mem_instr
  io.wb.we := core.io.mem_wstrb =/= 0.U
  io.wb.adr := core.io.mem_addr
  io.wb.wdata := core.io.mem_wdata
  io.wb.sel := core.io.mem_wstrb

  prefetcher.io.toCore.valid := core.io.mem_valid && core.io.mem_instr
  prefetcher.io.toCore.addr := core.io.mem_addr
  prefetcher.io.toNi.ready := io.instrPort.ready
  prefetcher.io.toNi.accepted := io.instrPort.accepted
  prefetcher.io.toNi.bundle := io.instrPort.bundle
  io.instrPort.valid := prefetcher.io.toNi.valid
  io.instrPort.addr := prefetcher.io.toNi.addr

  io.instrCheck.addr := core.io.mem_addr
  val wrongInstr = WireDefault(0.B)
  dontTouch(wrongInstr)
  when(core.io.mem_valid && core.io.mem_instr && prefetcher.io.toCore.ready) {
    wrongInstr := io.instrCheck.instr =/= prefetcher.io.toCore.instr
  }

  // access from remote core
  val remoteScratchPadAccess = io.remoteWb.adr(27, 4) === 0x100_000.U
  val remoteBootAddrAccess = io.remoteWb.adr(27, 0) === 0x100_0010.U
  val remoteConfigAccess = io.remoteWb.adr(27, 0) === 0x100_0014.U

  io.remoteWb.ack := io.remoteWb.cyc
  io.remoteWb.rdata := MuxCase(0.U, Seq(
    remoteScratchPadAccess -> scratchPad.read(io.remoteWb.adr(3, 2)),
    remoteBootAddrAccess -> bootAddr,
    remoteConfigAccess -> configReg
  ))


  // local access from pico core
  val scratchPadAccess = core.io.mem_addr(27, 4) === 0x100_000.U
  val coreIdAccess = core.io.mem_addr(31, 0) === 0xFFFF_0000L.U
  val bootAddrAccess = core.io.mem_addr(27, 0) === 0x100_0010.U
  val configAccess = core.io.mem_addr(27, 0) === 0x100_0014.U
  val barrierAccess = core.io.mem_addr(31, 0) === 0xFFFF_0004L.U
  val barrierEnAccess = core.io.mem_addr(31, 0) === 0xFFFF_0008L.U


  val localReadData = MuxCase(0.U, Seq(
    scratchPadAccess -> scratchPad.read(core.io.mem_addr(3, 2)),
    coreIdAccess -> id.U,
    bootAddrAccess -> bootAddr,
    configAccess -> configReg,
    barrierEnAccess -> barrierEn
  ))


  core.io.mem_rdata := Mux(core.io.mem_instr, prefetcher.io.toCore.instr, Mux(isLocalAccess, localReadData, io.wb.rdata))
  

  // writes to local state
  // remote write takes priority over local write
  val remoteWrite = io.remoteWb.cyc && io.remoteWb.we
  val picoLocalWrite = core.io.mem_valid && isLocalAccess && core.io.mem_wstrb =/= 0.U

  when(remoteWrite && remoteScratchPadAccess) {
    scratchPad.write(io.remoteWb.adr(3, 2), io.remoteWb.wdata)
  }.elsewhen(picoLocalWrite && scratchPadAccess) {
    scratchPad.write(core.io.mem_addr(3, 2), core.io.mem_wdata)
  }

  when(remoteWrite && remoteBootAddrAccess) {
    bootAddr := io.remoteWb.wdata
  }.elsewhen(picoLocalWrite && bootAddrAccess) {
    bootAddr := core.io.mem_wdata
  }

  when(remoteWrite && remoteConfigAccess) {
    configReg := io.remoteWb.wdata
  }.elsewhen(picoLocalWrite && configAccess) {
    configReg := core.io.mem_wdata
  }

  when(picoLocalWrite && barrierEnAccess) {
    barrierEn := core.io.mem_wdata(0)
  }

  io.barrierArrived := Mux(barrierEn, picoLocalWrite && barrierAccess, 0.B)

  core.io.mem_ready := Mux(barrierEn && picoLocalWrite && barrierAccess, io.barrierRelease, Mux(core.io.mem_instr, prefetcher.io.toCore.ready, Mux(isLocalAccess, 1.B, io.wb.ack)))

}


class Prefetcher() extends Module {

  val io = IO(new Bundle {

    val toCore = new Bundle {
      val valid = Input(Bool())
      val addr = Input(UInt(32.W))
      val instr = Output(UInt(32.W))
      val ready = Output(Bool())
    }

    val toNi = new Bundle {
      val valid = Output(Bool())
      val accepted = Input(Bool())
      val ready = Input(Bool())
      val addr = Output(UInt(32.W))
      val bundle = Input(UInt(128.W))
    }


  })

  object PrefetchState extends ChiselEnum {
    val waitAccept, waitResponse = Value
  }

  object FetchState extends ChiselEnum {
    val idle, fetch = Value
  }

  val fetchState = RegInit(FetchState.fetch)
  val prefetchState = RegInit(PrefetchState.waitAccept)

  val currentPc = RegInit(0xFFFF_FFF.U(28.W))
  val prefetchPc = RegInit(0xFFFF_FFF.U(28.W))
  val instrBundle = Reg(UInt(128.W))
  val prefetchValid = RegInit(0.B)
  val prefetchBundle = Reg(UInt(128.W))

  val hit = io.toCore.addr(31, 4) === currentPc

  val prefetcherHit = io.toCore.addr(31, 4) === prefetchPc
  val instrOffset = io.toCore.addr(3, 2)
  val lastAccess = instrOffset === 3.U

  val instrs = VecInit(
    instrBundle(31, 0),
    instrBundle(63, 32),
    instrBundle(95, 64),
    instrBundle(127, 96)
  )
  io.toCore.instr := instrs(instrOffset)
  io.toCore.ready := hit

  val prevAccessPc = RegInit(0xFFFF_FFFFL.U(32.W))
  when(io.toCore.valid && io.toCore.ready) {
    prevAccessPc := io.toCore.addr
  }
  val unexpectedPrefetchMiss = WireDefault(0.B)
  dontTouch(unexpectedPrefetchMiss)
  val sameBundleAsPrevAccess = io.toCore.addr(31, 4) === prevAccessPc(31, 4)
  val noBackwardsJump = io.toCore.addr(3, 2) > prevAccessPc(3, 2)
  val nextBundleFromPrev = io.toCore.addr(31, 4) === (prevAccessPc + 1.U)
  when(io.toCore.valid && !hit && ((sameBundleAsPrevAccess && noBackwardsJump) || nextBundleFromPrev)) {
    unexpectedPrefetchMiss := 1.B
  }


  // consume the prefetched bundle and make it the current bundle
  when(io.toCore.valid && lastAccess && prefetchValid) {
    currentPc := currentPc + 1.U
    instrBundle := prefetchBundle
    prefetchValid := 0.B
  }

  val fetcherClaimsReq = WireDefault(0.B)
  val fetcherReqValid = WireDefault(0.B)
  val prefetcherReqValid = WireDefault(0.B)
  val fetcherReqAddr = WireDefault(0.U(32.W))
  val prefetcherReqAddr = WireDefault(0.U(32.W))
  io.toNi.valid := Mux(fetcherClaimsReq, fetcherReqValid, prefetcherReqValid)
  io.toNi.addr := Mux(fetcherClaimsReq, fetcherReqAddr, prefetcherReqAddr)

  fetcherReqAddr := Cat(io.toCore.addr(31, 4), 0.U(4.W))
  prefetcherReqAddr := Cat(prefetchPc, 0.U(4.W))
  prefetcherReqValid := 1.B

  switch(prefetchState) {
    is(PrefetchState.waitAccept) {
      when(io.toNi.accepted && !fetcherClaimsReq) {
        prefetchState := PrefetchState.waitResponse
      }
    }
    is(PrefetchState.waitResponse) {
      when(io.toNi.ready) {
        when(!prefetchValid) {
          prefetchBundle := io.toNi.bundle
          prefetchValid := 1.B
          prefetchPc := prefetchPc + 1.U
        }
        prefetchState := PrefetchState.waitAccept
      }
    }
  }

  // todo check if prefetcher is already fetching the next bundle. then we just have to wait for the response
  switch(fetchState) {
    is(FetchState.idle) { // normal operation
      
      when(io.toCore.valid && !hit) { // either we miss
        fetcherClaimsReq := 1.B
        fetcherReqValid := 1.B
        when(prefetcherHit && prefetchState === PrefetchState.waitResponse) { // we are already fetching the next bundle, so we just have to wait for the response
          fetchState := FetchState.fetch
        }.elsewhen(io.toNi.accepted) {
          fetchState := FetchState.fetch
        }
  
        prefetchValid := 0.B // useless
        prefetchState := PrefetchState.waitAccept
        

      }.elsewhen(io.toCore.valid && hit && lastAccess && prefetchValid) { // or we need to move to the next bundle
        currentPc := currentPc + 1.U
        instrBundle := prefetchBundle
        prefetchValid := 0.B // consumed
      } // or we do nothing
    }
    is(FetchState.fetch) {
      fetcherClaimsReq := 1.B
      fetcherReqValid := 1.B
      instrBundle := io.toNi.bundle
      when(io.toNi.ready) {
        currentPc := io.toCore.addr(31, 4)
        fetchState := FetchState.idle
        prefetchPc := io.toCore.addr(31, 4) + 1.U
        prefetchValid := 0.B // useless
        prefetchState := PrefetchState.waitAccept
      }
    }
  }


}

/* 
prefetcher spec

1. cold start
  - no instructions in buffer
  - no prefetched instructions
  - no requests in transit
  - action:
    - issue fetch request for incoming instruction request
    - alternative: prefetch blindly address 0x00 since that is the boot address

2. 



 */


/* 


class Prefetcher() extends Module {

  val io = IO(new Bundle {

    val toCore = new Bundle {
      val valid = Input(Bool())
      val addr = Input(UInt(32.W))
      val instr = Output(UInt(32.W))
      val ready = Output(Bool())
    }

    val toNi = new Bundle {
      val valid = Output(Bool())
      val accepted = Input(Bool())
      val ready = Input(Bool())
      val addr = Output(UInt(32.W))
      val bundle = Input(UInt(128.W))
    }


  })

  object PrefetchState extends ChiselEnum {
    val waitAccept, waitResponse = Value
  }

  object FetchState extends ChiselEnum {
    val idle, fetch = Value
  }

  val fetchState = RegInit(FetchState.fetch)

  val currentPc = RegInit(0xFFFF_FFF.U(28.W))
  val instrBundle = Reg(UInt(128.W))

  val prefetchValid = RegInit(0.B)
  val prefetchBundle = Reg(UInt(128.W))

  val hit = io.toCore.addr(31, 4) === currentPc
  val instrOffset = io.toCore.addr(3, 2)
  val lastAccess = instrOffset === 3.U

  val prefetchPc = RegInit(0xFFFF_FFF.U(28.W))



  val prevAccessPc = RegInit(0xFFFF_FFFFL.U(32.W))
  when(io.toCore.valid && io.toCore.ready) {
    prevAccessPc := io.toCore.addr
  }
  val unexpectedPrefetchMiss = WireDefault(0.B)
  dontTouch(unexpectedPrefetchMiss)
  val sameBundleAsPrevAccess = io.toCore.addr(31, 4) === prevAccessPc(31, 4)
  val noBackwardsJump = io.toCore.addr(3, 2) > prevAccessPc(3, 2)
  val nextBundleFromPrev = io.toCore.addr(31, 4) === (prevAccessPc + 1.U)
  when(io.toCore.valid && !hit && ((sameBundleAsPrevAccess && noBackwardsJump) || nextBundleFromPrev)) {
    unexpectedPrefetchMiss := 1.B
  }

  val prefetchShouldUpdate = RegInit(0.B)

  when(io.toCore.valid && lastAccess && !prefetchValid) {
    prefetchShouldUpdate := 1.B
  }

  // consume the prefetched bundle and make it the current bundle
  val prefetchWillBeValid = WireDefault(0.B)
  when(io.toCore.valid && lastAccess && (prefetchValid || prefetchWillBeValid)) {
    currentPc := prefetchPc
    instrBundle := prefetchBundle
    prefetchValid := 0.B
  }

  val waiting = RegNext(!hit, 0.B)
  
  val instrs = VecInit(
    instrBundle(31, 0),
    instrBundle(63, 32),
    instrBundle(95, 64),
    instrBundle(127, 96)
  )
  io.toCore.instr := instrs(instrOffset)

  io.toNi.valid := 0.B
  io.toNi.addr := DontCare
  io.toCore.ready := 0.B

  switch(stateReg) {
    is(State.idle) {
      io.toCore.ready := hit
      when(io.toCore.valid && !hit) {
        prefetchValid := 0.B
        stateReg := State.waitingFetch
      }.otherwise {
        stateReg := State.waitingPrefetch
      }
    }
    is(State.waitingFetch) {
      io.toCore.ready := 0.B
      io.toNi.valid := 1.B
      io.toNi.addr := Cat(io.toCore.addr(31, 4), 0.U(4.W))
      instrBundle := io.toNi.bundle
      when(io.toNi.ready) {
        currentPc := io.toCore.addr(31, 4)
        prefetchPc := io.toCore.addr(31, 4) + 1.U
        prefetchValid := 0.B
        stateReg := State.idle
      }
    }
    is(State.waitingFetchIgnoreResp) {
      io.toCore.ready := 0.B
      io.toNi.valid := 1.B
      io.toNi.addr := Cat(io.toCore.addr(31, 4), 0.U(4.W))
      instrBundle := io.toNi.bundle
      when(io.toNi.ready) { // this is a stale request response
        stateReg := State.waitingFetch
      }
    }
    is(State.waitingPrefetch) {
      io.toCore.ready := hit
      io.toNi.valid := 1.B
      io.toNi.addr := Cat(prefetchPc, 0.U(4.W))
      when(io.toCore.valid && !hit) {
        prefetchValid := 0.B
        when(io.toNi.requestInTransit) {
          stateReg := State.waitingFetchIgnoreResp
        }.otherwise {
          stateReg := State.waitingFetch
        }
      }.elsewhen(io.toNi.ready && !prefetchValid) {
        prefetchBundle := io.toNi.bundle
        prefetchWillBeValid := 1.B
        stateReg := State.idle
        prefetchPc := prefetchPc + 1.U
        when(prefetchShouldUpdate) {
          prefetchShouldUpdate := 0.B
          currentPc := prefetchPc
          instrBundle := io.toNi.bundle
          prefetchValid := 0.B
        }.otherwise {
          prefetchValid := 1.B
        }
      }
    }

  }



}

 */