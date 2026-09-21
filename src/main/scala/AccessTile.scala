
import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFileInline

import Util._

import s4noc._

class AccessTile(id: Int, conf: s4noc.Config, reqSched: Schedule, respSched: InvertedSchedule, bootBinPath: String, prog: ProgramRom) extends Tile(id, conf, reqSched, respSched, 1) {

  def this(id: Int, conf: s4noc.Config, reqSched: Schedule, respSched: InvertedSchedule, bootBinPath: String, romBinPath: String) =
    this(id, conf, reqSched, respSched, bootBinPath, ProgramRom.Baked(romBinPath))

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

  // one word per line here, unlike the wide tile's four
  val progWord: UInt => UInt = prog match {
    case ProgramRom.Baked(romBinPath) =>
      val progRom = VecInit(Util.Binary.load(romBinPath).map(_.U(32.W)))
      addr => progRom(addr(26, 2))
    case ProgramRom.Loaded(hexPath, lineBits) =>
      val progRom = Mem(1 << lineBits, UInt(32.W))
      loadMemoryFromFileInline(progRom, new java.io.File(hexPath).getAbsolutePath)
      addr => progRom(addr(lineBits + 1, 2))
  }

  val readData = Mux(
    responderNi.io.comMemReq.addr(27),
    progWord(responderNi.io.comMemReq.addr),
    bootRom(responderNi.io.comMemReq.addr(26, 2))
  )
  responderNi.io.rdData := readData

}

/** Where an access node's program ROM gets its contents. */
sealed trait ProgramRom
object ProgramRom {
  /** Baked into the design at elaboration, as taped out. */
  case class Baked(binPath: String) extends ProgramRom
  /** 2^lineBits lines read by $readmemh when the simulation starts, so one model serves any
    * program. A line is one 4-word bundle in the wide tile, one word in the narrow one. */
  case class Loaded(hexPath: String, lineBits: Int) extends ProgramRom
}

class WideAccessTile(id: Int, conf: s4noc.Config, reqSched: Schedule, respSched: InvertedSchedule, bootBinPath: String, prog: ProgramRom) extends Tile(id, conf, reqSched, respSched, 4) {

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

  val readData = prog match {
    case ProgramRom.Baked(romBinPath) =>
      val progBinWords = Util.Binary.load(romBinPath)
      val progBinWordsPacked = progBinWords.grouped(4).map { group =>
        group.zipWithIndex.map { case (word, idx) =>
          word << (idx * 32)
        }.reduce(_ | _)
      }.toSeq
      val progRom = VecInit(progBinWordsPacked.map(_.U(128.W)))

      Mux(
        responderNi.io.comMemReq.addr(27),
        progRom(responderNi.io.comMemReq.addr(26, 4)),
        bootRom(responderNi.io.comMemReq.addr(26, 4))
      )
    case ProgramRom.Loaded(hexPath, lineBits) =>
      val progRom = Mem(1 << lineBits, UInt(128.W))
      loadMemoryFromFileInline(progRom, new java.io.File(hexPath).getAbsolutePath)

      Mux(
        responderNi.io.comMemReq.addr(27),
        progRom(responderNi.io.comMemReq.addr(lineBits + 3, 4)),
        bootRom(responderNi.io.comMemReq.addr(26, 4))
      )
  }
  responderNi.io.rdData := readData

}