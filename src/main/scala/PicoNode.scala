import chisel3._
import soc.ReadyValidChannelsIO
import s4noc.Entry

import Util._
import chisel3.util._

class PicoNode(c: PicoRvConfig, coreId: Int) extends Module with NocNode {

  

  val io = IO(new Bundle {
    val networkPortReq = new ReadyValidChannelsIO(Entry(new MemoryRequest))
    val networkPortResp = new ReadyValidChannelsIO(Entry(new MemoryResponse))
  })


  val pico = Module(new PicoRv(c, coreId))

  pico.io.remoteWb.expand(
    _.cyc := io.networkPortReq.rx.valid && io.networkPortResp.tx.ready, // we wait with issuing the request until the resp.tx is ready
    _.stb := io.networkPortReq.rx.valid && io.networkPortResp.tx.ready,
    _.adr := io.networkPortReq.rx.bits.data.addr,
    _.wdata := io.networkPortReq.rx.bits.data.data,
    _.we := io.networkPortReq.rx.bits.data.write,
    _.sel := io.networkPortReq.rx.bits.data.mask
  )
  io.networkPortReq.rx.ready := io.networkPortResp.tx.ready && pico.io.remoteWb.ack
  io.networkPortResp.tx.expand(
    _.valid := pico.io.remoteWb.ack, // we wait with issuing the response until the resp.tx is ready, so single cycle ack is ok
    _.bits.expand(
      _.core := coreId.U,
      _.data.data := pico.io.remoteWb.rdata
    )
  )

  io.networkPortReq.tx.expand(
    _.valid := 0.B, // default
    _.bits.expand(
      _.core := pico.io.wb.adr(31, 28),
      _.data.expand(
        _.addr := pico.io.wb.adr,
        _.data := pico.io.wb.wdata,
        _.write := pico.io.wb.we,
        _.mask := pico.io.wb.sel
      )
    )
  )

  io.networkPortResp.rx.ready := 0.B // default
  pico.io.wb.ack := 0.B // default
  pico.io.wb.rdata := io.networkPortResp.rx.bits.data.data 

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
      io.networkPortReq.tx.valid := 1.B

      when(io.networkPortReq.tx.ready) {
        when(pico.io.wb.we) {
          pico.io.wb.ack := 1.B
          state := State.Idle
        } otherwise {
          state := State.RemoteWait
        }
      }
    }
    is(State.RemoteWait) {
      io.networkPortResp.rx.ready := 1.B
      when(io.networkPortResp.rx.valid) {
        pico.io.wb.ack := 1.B
        state := State.Idle
      }
    }
  }


}



class LocalAccess extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new MemoryRequest))
    val resp = Decoupled(new MemoryResponse)
  })

  val scratchPad = Mem(4, UInt(32.W))

  val scratchPadAccess = io.req.bits.addr(27, 4) === 0x100_000.U
  val coreIdAccess = io.req.bits.addr(27, 0) === 0x200_0000.U

  io.req.ready := io.resp.ready
  io.resp.valid := io.req.valid
  io.resp.bits.data := MuxCase(0.U, Seq(
    scratchPadAccess -> scratchPad.read(io.req.bits.addr(3, 2)),
    coreIdAccess -> io.req.bits.addr(31, 28)
  ))

  when(io.req.valid && scratchPadAccess && io.req.bits.write) {
    scratchPad.write(io.req.bits.addr(3, 2), io.req.bits.data)
  }

}