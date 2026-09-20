
import chisel3._
import chisel3.experimental.{IntParam, StringParam}
import chisel3.util.HasBlackBoxPath
import Util._
import s4noc.Schedule

class MemoryTile(id: Int, conf: s4noc.Config, reqSched: Schedule, respSched: InvertedSchedule, responseWords: Int) extends Tile(id, conf, reqSched, respSched, responseWords) {
  

  val mem = Module(new sky130_sram_1kbyte_1rw1r_32x256_8)

  val responderNi = Module(new PipelinedResponderNi(id, reqSched, respSched, Seq(0, 3, 4, 5, 6, 7, 8), 1))

  responderNi.io.reqEgress <> reqLocal.out
  responderNi.io.respIngress <> respLocal.in
  responderNi.io.reqLookAhead <> reqRouter.localPortLookahead

  responderNi.io.reqSlot := reqSlotCounter
  responderNi.io.respSlot := respSlotCounter


  reqLocal.in.valid := false.B
  reqLocal.in.data := DontCare


  mem.io.clk0 := clock
  mem.io.csb0 := 0.B
  mem.io.web0 := !(responderNi.io.syncMemReq.valid && responderNi.io.syncMemReq.wr)
  mem.io.wmask0 := responderNi.io.syncMemReq.mask
  mem.io.addr0 := responderNi.io.syncMemReq.addr(9, 2)
  mem.io.din0 := responderNi.io.syncMemReq.wrData
  responderNi.io.rdData := mem.io.dout0

  mem.io.clk1 := 0.B.asClock
  mem.io.csb1 := 1.B // not used
  mem.io.addr1 := 0.U


}

/** Which memory backs a memory tile. */
sealed trait MemoryImpl
object MemoryImpl {
  /** The OpenRAM macro, as taped out: 256 words. */
  case object OpenRam extends MemoryImpl
  /** A behavioural SRAM of 2^addrBits words for simulation, optionally preloaded by $readmemh from `initHex`. */
  case class SimSram(addrBits: Int, initHex: Option[String] = None) extends MemoryImpl
}

/** Behavioural SRAM with the macro's port behaviour, for simulations that need more memory. */
class SimSramBlackBox(addrBits: Int, initFile: String) extends BlackBox(Map(
  "ADDR_WIDTH" -> IntParam(addrBits),
  "INIT_FILE" -> StringParam(initFile)
)) with HasBlackBoxPath {
  override def desiredName = "sim_sram"

  val io = IO(new Bundle {
    val clk = Input(Clock())
    val addr = Input(UInt(addrBits.W))
    val din = Input(UInt(32.W))
    val wmask = Input(UInt(4.W))
    val write = Input(Bool())
    val dout = Output(UInt(32.W))
  })

  addPath("src/verilog/sim_sram.v")
}

/** MemoryTile with the OpenRAM macro replaced by the behavioural SRAM, for simulations that need
  * more memory.
  *
  * The timing is the macro's: the lookahead request is sampled on the rising edge and the word is valid
  * in the next cycle, when the request reaches the local port and the responder NI captures it.
  */
class SimMemoryTile(id: Int, conf: s4noc.Config, reqSched: Schedule, respSched: InvertedSchedule, responseWords: Int, addrBits: Int, initHex: Option[String]) extends Tile(id, conf, reqSched, respSched, responseWords) {

  val mem = Module(new SimSramBlackBox(addrBits, initHex.map(f => new java.io.File(f).getAbsolutePath).getOrElse("")))

  val responderNi = Module(new PipelinedResponderNi(id, reqSched, respSched, Seq(0, 3, 4, 5, 6, 7, 8), 1))

  responderNi.io.reqEgress <> reqLocal.out
  responderNi.io.respIngress <> respLocal.in
  responderNi.io.reqLookAhead <> reqRouter.localPortLookahead

  responderNi.io.reqSlot := reqSlotCounter
  responderNi.io.respSlot := respSlotCounter


  reqLocal.in.valid := false.B
  reqLocal.in.data := DontCare


  val req = responderNi.io.syncMemReq
  val write = req.valid && req.wr
  val index = req.addr(addrBits + 1, 2)

  mem.io.clk := clock
  mem.io.addr := index
  mem.io.din := req.wrData
  mem.io.wmask := req.mask
  mem.io.write := write
  responderNi.io.rdData := mem.io.dout

}