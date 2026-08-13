
import chisel3._
import chisel3.util._

import Util._

class AccessTile(id: Int, conf: s4noc.Config, schedule: Array[Array[Int]], bootBinPath: String, romBinPath: String) extends Tile(id, conf, schedule) {

  val pontePort = IO(Flipped(new ponte.PonteAccessPort))


  object State extends ChiselEnum {
    val Idle, WaitReq, WaitResp = Value
  }

  val state = RegInit(State.Idle)

  reqPort.tx.expand(
    _.valid := 0.B, // default
    _.bits.expand(
      _.core := pontePort.addr(31, 28),
      _.data.expand(
        _.addr := pontePort.addr,
        _.data := pontePort.wdata,
        _.write := pontePort.write
      )
    )
  )

  val bootRom = VecInit(Util.Binary.load(bootBinPath).map(_.U(32.W)))

  val progRom = VecInit(Util.Binary.load(romBinPath).map(_.U(32.W)))

  val readData = Mux(
    reqPort.rx.bits.data.addr(27),
    progRom(reqPort.rx.bits.data.addr(26, 2)),
    bootRom(reqPort.rx.bits.data.addr(26, 2))
  )

  reqPort.rx.ready := respPort.tx.ready
  respPort.tx.valid := reqPort.rx.valid && !reqPort.rx.bits.data.write
  respPort.tx.bits.expand(
    _.core := reqPort.rx.bits.core,
    _.data.data := readData
  )

  respPort.rx.ready := 0.B // default

  pontePort.done := 0.B // default
  pontePort.rdata := respPort.rx.bits.data.data


  switch(state) {
    is(State.Idle) {
      when(pontePort.valid) {
        state := State.WaitReq
      }
    }
    is(State.WaitReq) {
      reqPort.tx.valid := 1.B

      when(reqPort.tx.ready) {
        state := Mux(pontePort.write, State.Idle, State.WaitResp)
      }
    }
    is(State.WaitResp) {
      respPort.rx.ready := 1.B
      when(respPort.rx.valid) {
        state := State.Idle
        pontePort.done := 1.B
      }
    }
  }


}
