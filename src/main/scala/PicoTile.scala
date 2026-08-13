
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
  emitVerilog(new PicoTile(0, conf, Schedule(3).schedule, PicoRvConfig.small), Array("--target-dir", "generated"))
}

class PicoTile(id: Int, conf: Config, schedule: Array[Array[Int]], picoConf: PicoRvConfig) extends Tile(id, conf, schedule) {

  val pico = Module(new PicoRv(picoConf))
  
  pico.io.coreId := id.U

  pico.io.remoteWb.expand(
    _.cyc := reqPort.rx.valid && respPort.tx.ready, // we wait with issuing the request until the resp.tx is ready
    _.stb := reqPort.rx.valid && respPort.tx.ready,
    _.adr := reqPort.rx.bits.data.addr,
    _.wdata := reqPort.rx.bits.data.data,
    _.we := reqPort.rx.bits.data.write,
    _.sel := "b1111".U
  )
  reqPort.rx.ready := respPort.tx.ready && pico.io.remoteWb.ack
  respPort.tx.expand(
    _.valid := pico.io.remoteWb.ack && !reqPort.rx.bits.data.write, // we wait with issuing the response until the resp.tx is ready, so single cycle ack is ok
    _.bits.expand(
      _.core := reqPort.rx.bits.core, // reply to the requesting core 
      _.data.data := pico.io.remoteWb.rdata
    )
  )

  reqPort.tx.expand(
    _.valid := 0.B, // default
    _.bits.expand(
      _.core := pico.io.wb.adr(31, 28),
      _.data.expand(
        _.addr := pico.io.wb.adr,
        _.data := pico.io.wb.wdata,
        _.write := pico.io.wb.we
      )
    )
  )

  respPort.rx.ready := 0.B // default
  pico.io.wb.ack := 0.B // default
  pico.io.wb.rdata := respPort.rx.bits.data.data 

  object State extends ChiselEnum {
    val Idle, RemoteRequest, RemoteWait = Value
  }


  val state = RegInit(State.Idle)

  switch(state) {
    is(State.Idle) {
      when(pico.io.wb.cyc) {
        state := State.RemoteRequest
      }
    }
    is(State.RemoteRequest) {
      reqPort.tx.valid := 1.B

      when(reqPort.tx.ready) {
        when(pico.io.wb.we) {
          pico.io.wb.ack := 1.B
          state := State.Idle
        } otherwise {
          state := State.RemoteWait
        }
      }
    }
    is(State.RemoteWait) {
      respPort.rx.ready := 1.B
      when(respPort.rx.valid) {
        pico.io.wb.ack := 1.B
        state := State.Idle
      }
    }
  }

}