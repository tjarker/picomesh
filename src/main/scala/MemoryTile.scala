
import chisel3._
import Util._
import s4noc.Schedule

class MemoryTile(id: Int, conf: s4noc.Config, reqSched: Schedule, respSched: InvertedSchedule) extends Tile(id, conf, reqSched, respSched) {
  

  val mem = Module(new sky130_sram_1kbyte_1rw1r_32x256_8)

  val responderNi = Module(new PipelinedResponderNi(id, reqSched, respSched, Seq(0, 3, 4, 5, 6, 7, 8)))

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