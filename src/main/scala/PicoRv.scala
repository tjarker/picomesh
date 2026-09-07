
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
    twoCycleCompare = false,
    twoCycleAlu = false,
    enableIrqQregs = false,
    catchMisaligned = false,
    catchIllegalInstruction = false,
    enableIrqTimer = false,
  )
}

object PicoRv {
  implicit class BoolToInt(val b: Boolean) extends AnyVal {
    def toInt: Int = if (b) 1 else 0
  }
}

import PicoRv._


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

class PicoRvBlackBox(c: PicoRvConfig) extends BlackBox(Map(
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
