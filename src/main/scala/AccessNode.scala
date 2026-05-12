

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

  val bootRom = VecInit(Util.Binary.load("build/bootloader/bootloader.bin").map(_.U(32.W)))

  val progRom = VecInit(Util.Binary.load("build/rom/rom.bin").map(_.U(32.W)))

  val readData = Mux(
    io.networkPortReq.rx.bits.data.addr(27),
    progRom(io.networkPortReq.rx.bits.data.addr(26, 2)),
    bootRom(io.networkPortReq.rx.bits.data.addr(26, 2))
  )

  io.networkPortReq.rx.ready := io.networkPortResp.tx.ready
  io.networkPortResp.tx.valid := io.networkPortReq.rx.valid && !io.networkPortReq.rx.bits.data.write
  io.networkPortResp.tx.bits.expand(
    _.core := io.networkPortReq.rx.bits.core,
    _.data.data := readData
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
