
import chisel3._
import chisel3.util._

import Util._

import s4noc._

class AccessTile(id: Int, conf: s4noc.Config, reqSched: Schedule, respSched: InvertedSchedule, bootBinPath: String, romBinPath: String) extends Tile(id, conf, reqSched, respSched, 1) {

  val pontePort = IO(Flipped(new ponte.PonteAccessPort))


  object State extends ChiselEnum {
    val Idle, WaitReq, WaitResp = Value
  }

  val state = RegInit(State.Idle)

  val requesterNi = Module(new BlockingRequesterNi(id, reqSched, respSched, 1))
  val responderNi = Module(new PipelinedResponderNi(id, reqSched, respSched, Seq(0, 3, 4, 5, 6, 7, 8).filter(_ != id), 1))

  requesterNi.io.reqIngress <> reqLocal.in
  requesterNi.io.respEgress <> respLocal.out
  responderNi.io.reqEgress <> reqLocal.out
  responderNi.io.respIngress <> respLocal.in
  responderNi.io.reqLookAhead <> reqRouter.localPortLookahead

  requesterNi.io.reqSlot := reqSlotCounter
  requesterNi.io.respSlot := respSlotCounter
  responderNi.io.reqSlot := reqSlotCounter
  responderNi.io.respSlot := respSlotCounter

  requesterNi.io.wb.expand(
    _.cyc := pontePort.valid,
    _.stb := pontePort.valid,
    _.adr := pontePort.addr,
    _.wdata := pontePort.wdata,
    _.we := pontePort.write,
    _.sel := "b1111".U
  )
  pontePort.rdata := requesterNi.io.wb.rdata
  pontePort.done := requesterNi.io.wb.ack

  val bootRom = VecInit(Util.Binary.load(bootBinPath).map(_.U(32.W)))

  val progRom = VecInit(Util.Binary.load(romBinPath).map(_.U(32.W)))

  val readData = Mux(
    responderNi.io.comMemReq.addr(27),
    progRom(responderNi.io.comMemReq.addr(26, 2)),
    bootRom(responderNi.io.comMemReq.addr(26, 2))
  )
  responderNi.io.rdData := readData

}

class WideAccessTile(id: Int, conf: s4noc.Config, reqSched: Schedule, respSched: InvertedSchedule, bootBinPath: String, romBinPath: String) extends Tile(id, conf, reqSched, respSched, 4) {

  val pontePort = IO(Flipped(new ponte.PonteAccessPort))


  object State extends ChiselEnum {
    val Idle, WaitReq, WaitResp = Value
  }

  val state = RegInit(State.Idle)

  val requesterNi = Module(new BlockingRequesterNi(id, reqSched, respSched, 4))
  val responderNi = Module(new PipelinedResponderNi(id, reqSched, respSched, Seq(0, 3, 4, 5, 6, 7, 8).filter(_ != id), 4))

  requesterNi.io.reqIngress <> reqLocal.in
  requesterNi.io.respEgress <> respLocal.out
  responderNi.io.reqEgress <> reqLocal.out
  responderNi.io.respIngress <> respLocal.in
  responderNi.io.reqLookAhead <> reqRouter.localPortLookahead

  requesterNi.io.reqSlot := reqSlotCounter
  requesterNi.io.respSlot := respSlotCounter
  responderNi.io.reqSlot := reqSlotCounter
  responderNi.io.respSlot := respSlotCounter

  requesterNi.io.wb.expand(
    _.cyc := pontePort.valid,
    _.stb := pontePort.valid,
    _.adr := pontePort.addr,
    _.wdata := pontePort.wdata,
    _.we := pontePort.write,
    _.sel := "b1111".U
  )
  pontePort.rdata := requesterNi.io.wb.rdata
  pontePort.done := requesterNi.io.wb.ack

  val bootBinWords = Util.Binary.load(bootBinPath)
  val bootBinWordsPacked = bootBinWords.grouped(4).map { group =>
    group.zipWithIndex.map { case (word, idx) =>
      word << (idx * 32)
    }.reduce(_ | _)
  }.toSeq
  val bootRom = VecInit(bootBinWordsPacked.map(_.U(128.W)))

  val progBinWords = Util.Binary.load(romBinPath)
  val progBinWordsPacked = progBinWords.grouped(4).map { group =>
    group.zipWithIndex.map { case (word, idx) =>
      word << (idx * 32)
    }.reduce(_ | _)
  }.toSeq
  val progRom = VecInit(progBinWordsPacked.map(_.U(128.W)))


  val readData = Mux(
    responderNi.io.comMemReq.addr(27),
    progRom(responderNi.io.comMemReq.addr(26, 4)),
    bootRom(responderNi.io.comMemReq.addr(26, 4))
  )
  responderNi.io.rdData := readData

}