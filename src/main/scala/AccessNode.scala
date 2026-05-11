

import chisel3._
import chisel3.util._
import Util._

import soc.ReadyValidChannelsIO
import s4noc.Entry

class AccessNode extends Module {

  val io = IO(new Bundle {
    val networkPortReq = new ReadyValidChannelsIO(Entry(new MemoryRequest))
    val networkPortResp = new ReadyValidChannelsIO(Entry(new MemoryResponse))
    val pontePort = Flipped(new ponte.PonteAccessPort)
  })


  object State extends ChiselEnum {
    val Idle, WaitReq, WaitResp = Value
  }

  val state = RegInit(State.Idle)

  io.networkPortReq.tx.expand(
    _.valid := 0.B, // default
    _.bits.expand(
      _.core := io.pontePort.addr(31, 28),
      _.data.expand(
        _.addr := io.pontePort.addr,
        _.data := io.pontePort.wdata,
        _.write := io.pontePort.write,
        _.mask := "b1111".U
      )
    )
  )

  io.networkPortReq.rx.ready := 1.B
  io.networkPortResp.tx.valid := RegNext(io.networkPortReq.rx.valid, 0.B)
  io.networkPortResp.tx.bits.expand(
    _.core := io.networkPortReq.rx.bits.core,
    _.data.data := 0.U
  )

  io.networkPortResp.rx.ready := 0.B // default

  io.pontePort.done := 0.B // default
  io.pontePort.rdata := io.networkPortResp.rx.bits.data.data


  switch(state) {
    is(State.Idle) {
      when(io.pontePort.valid) {
        state := State.WaitReq
      }
    }
    is(State.WaitReq) {
      io.networkPortReq.tx.valid := 1.B

      when(io.networkPortReq.tx.ready) {
        state := State.WaitResp
      }
    }
    is(State.WaitResp) {
      when(io.networkPortResp.rx.valid) {
        state := State.Idle
        io.pontePort.done := 1.B
      }
    }
  }

  
}
