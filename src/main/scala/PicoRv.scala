
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
    enableCounters = false,
    enableCounters64 = false,
    enableRegs16_31 = false,
    enableRegsDualPort = false,
    twoCycleCompare = true,
    twoCycleAlu = true,
    enableIrqQregs = false,
  )
}

object PicoRv {
  implicit class BoolToInt(val b: Boolean) extends AnyVal {
    def toInt: Int = if (b) 1 else 0
  }
}

import PicoRv._

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


class PicoRv(c: PicoRvConfig) extends Module {

  val io = IO(new Bundle {
    val coreId = Input(UInt(4.W))
    val wb = new WishbonePort
    val remoteWb = Flipped(new WishbonePort)
  })



  val core = Module(new PicoRvBlackBox(c))

  val configReg = RegInit(0.U(1.W))

  core.io.pcpi_wait := 0.B
  core.io.pcpi_wr := 0.B
  core.io.pcpi_ready := 0.B
  core.io.pcpi_rd := 0.U
  core.io.wb_clk_i := clock
  core.io.wb_rst_i := reset.asBool || configReg(0) 
  core.io.irq := 0.U


  


  val isLocalAccess = core.io.wbm_adr_o(31, 28) === io.coreId || core.io.wbm_adr_o === 0xFFFF_0000L.U

  io.remoteWb.ack := io.remoteWb.cyc
  core.io.wbm_ack_i := Mux(!isLocalAccess, io.wb.ack, core.io.wbm_cyc_o && !io.remoteWb.cyc)

  io.wb.cyc := !isLocalAccess && core.io.wbm_cyc_o
  io.wb.stb := !isLocalAccess && core.io.wbm_cyc_o
  io.wb.we := core.io.wbm_we_o
  io.wb.adr := core.io.wbm_adr_o
  io.wb.wdata := core.io.wbm_dat_o
  io.wb.sel := core.io.wbm_sel_o



  val localAccessAddr = Mux(io.remoteWb.cyc, io.remoteWb.adr, core.io.wbm_adr_o)
  val localAccessData = Mux(io.remoteWb.cyc, io.remoteWb.wdata, core.io.wbm_dat_o)
  val localAccessMask = Mux(io.remoteWb.cyc, io.remoteWb.sel, core.io.wbm_sel_o)
  val localAccessWrite = Mux(io.remoteWb.cyc, io.remoteWb.we, core.io.wbm_we_o)


  /* 
      0xFFFF_0000: Core ID (read-only)
      0x?100_0000: Scratchpad[0]
      0x?100_0004: Scratchpad[1]
      0x?100_0008: Scratchpad[2]
      0x?100_000C: Scratchpad[3]
      0x?100_0010: Boot Address (read/write)
      0x?000_0014: Config (read/write)
   */

  val scratchPad = Mem(4, UInt(32.W))
  val bootAddr = RegInit(0x0800_0000.U(32.W))
  val scratchPadAccess = localAccessAddr(27, 4) === 0x100_000.U
  val coreIdAccess = localAccessAddr(31, 0) === 0xFFFF_0000L.U
  val bootAddrAccess = localAccessAddr(27, 0) === 0x100_0010.U

  val configAccess = localAccessAddr(27, 0) === 0x100_0014.U


  val readData = MuxCase(0.U, Seq(
    scratchPadAccess -> scratchPad.read(localAccessAddr(3, 2)),
    coreIdAccess -> io.coreId,
    bootAddrAccess -> bootAddr,
    configAccess -> configReg
  ))

  io.remoteWb.rdata := readData
  core.io.wbm_dat_i := Mux(isLocalAccess, readData, io.wb.rdata)

  val remoteWrite = io.remoteWb.cyc && io.remoteWb.we
  val picoLocalWrite = core.io.wbm_cyc_o && isLocalAccess && core.io.wbm_we_o
  val writeAccess = remoteWrite || picoLocalWrite
  when(writeAccess && scratchPadAccess) {
    scratchPad.write(localAccessAddr(3, 2), localAccessData)
  }

  when(writeAccess && bootAddrAccess) {
    bootAddr := localAccessData
  }

  when(writeAccess && configAccess) {
    configReg := localAccessData
  }

}


class PicoRv2(c: PicoRvConfig) extends Module {

  val io = IO(new Bundle {
    val mem = new MemoryPort
  })

  val core = Module(new PicoRvBlackBox(c))

  core.io.wb_clk_i := clock
  core.io.wb_rst_i := reset

  core.io.pcpi_wait := 0.B
  core.io.pcpi_ready := 0.B
  core.io.pcpi_wr := 0.B
  core.io.pcpi_rd := 0.U
  core.io.irq := 0.U

  io.mem.req.bits.addr := core.io.wbm_adr_o
  io.mem.req.bits.data := core.io.wbm_dat_o
  io.mem.req.bits.mask := core.io.wbm_sel_o
  io.mem.req.bits.write := core.io.wbm_we_o
  core.io.wbm_dat_i := io.mem.resp.bits.data

  // defalts
  io.mem.req.valid := 0.B
  io.mem.resp.ready := 0.B

  core.io.wbm_ack_i := 0.B

  object State extends ChiselEnum {
    val Idle, RemoteRequest, RemoteWait = Value
  }


  val state = RegInit(State.Idle)

  switch(state) {
    is(State.Idle) {
      io.mem.req.valid := 0.B

      when(core.io.wbm_cyc_o) {
        state := State.RemoteRequest
      }
    }
    is(State.RemoteRequest) {
      io.mem.req.valid := core.io.wbm_cyc_o

      when(io.mem.req.ready) {
        when(core.io.wbm_we_o) {
          core.io.wbm_ack_i := 1.B
          state := State.Idle
        } otherwise {
          state := State.RemoteWait
        }
      }
    }
    is(State.RemoteWait) {
      io.mem.req.valid := 0.B
      io.mem.resp.ready := 1.B
      when(io.mem.resp.valid) {
        core.io.wbm_ack_i := 1.B
        state := State.Idle
      }
    }
  }

  

}


import soc.ReadyValidChannelsIO
import s4noc.Entry









