
import chisel3._
import chisel3.util.experimental.loadMemoryFromFileInline
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
  mem.io.wmask0 := "b1111".U
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
  /** A SyncReadMem of 2^addrBits words for simulation, optionally preloaded by $readmemh from `initHex`. */
  case class SimSram(addrBits: Int, initHex: Option[String] = None) extends MemoryImpl
}

/** MemoryTile with the OpenRAM macro replaced by a SyncReadMem, for simulations that need more memory.
  *
  * The timing is the macro's: the lookahead request is sampled on the rising edge and the word is valid
  * in the next cycle, when the request reaches the local port and the responder NI captures it.
  */
class SimMemoryTile(id: Int, conf: s4noc.Config, reqSched: Schedule, respSched: InvertedSchedule, responseWords: Int, addrBits: Int, initHex: Option[String]) extends Tile(id, conf, reqSched, respSched, responseWords) {

  val mem = SyncReadMem(1 << addrBits, UInt(32.W))
  initHex.foreach(file => loadMemoryFromFileInline(mem, new java.io.File(file).getAbsolutePath))

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

  when(write) {
    mem.write(index, req.wrData)
  }
  // like the macro's dout0, the output only follows reads
  responderNi.io.rdData := mem.read(index, !write)

}