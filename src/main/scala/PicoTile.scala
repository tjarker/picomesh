
import chisel3._
import chisel3.util._
import s4noc.Const
import s4noc.Config
import s4noc.Schedule

import Util._
import s4noc._

object PicoTile extends App {
  val conf = Config(9, 
    BubbleType(1),
      BubbleType(1),
      DoubleBubbleType(1),
      0
  )
  emitVerilog(new PicoTile(0, conf, Schedule(3), new InvertedSchedule(3), PicoRvConfig.small), Array("--target-dir", "generated"))
}

class PicoTile(id: Int, conf: Config, reqSched: Schedule, respSched: InvertedSchedule, picoConf: PicoRvConfig) extends Tile(id, conf, reqSched, respSched) {

  val barrierPort = IO(new Bundle {
    val barrierArrived = Output(Bool())
    val barrierRelease = Input(Bool())
  })

  val pico = Module(new PicoRv(id, picoConf))
  
  pico.io.barrierRelease := barrierPort.barrierRelease
  barrierPort.barrierArrived := pico.io.barrierArrived


  val requesterNi = Module(new BlockingRequesterNi(id, reqSched, respSched))
  val responderNi = Module(new PipelinedResponderNi(id, reqSched, respSched, Seq(0, 3, 4, 5, 6, 7, 8).filter(_ != id)))

  reqRouter.io.ports(Const.LOCAL).in := requesterNi.io.reqIngress
  requesterNi.io.respEgress := respRouter.io.ports(Const.LOCAL).out
  responderNi.io.reqEgress := reqRouter.io.ports(Const.LOCAL).out
  respRouter.io.ports(Const.LOCAL).in := responderNi.io.respIngress
  responderNi.io.reqLookAhead <> reqRouter.localPortLookahead

  requesterNi.io.reqSlot := reqSlotCounter
  requesterNi.io.respSlot := respSlotCounter
  responderNi.io.reqSlot := reqSlotCounter
  responderNi.io.respSlot := respSlotCounter


  pico.io.remoteWb.expand(
    _.cyc := responderNi.io.comMemReq.valid,
    _.stb := responderNi.io.comMemReq.wr,
    _.adr := responderNi.io.comMemReq.addr,
    _.wdata := responderNi.io.comMemReq.wrData,
    _.we := responderNi.io.comMemReq.wr,
    _.sel := "b1111".U
  )
  responderNi.io.rdData := pico.io.remoteWb.rdata

  pico.io.wb <> requesterNi.io.wb

}