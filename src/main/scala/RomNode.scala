import chisel3._
import soc.ReadyValidChannelsIO
import s4noc.Entry

import Util._



class RomNode(program: Seq[BigInt]) extends Module with NocNode {
  val io = IO(new Bundle {
    val networkPortReq = new ReadyValidChannelsIO(Entry(new MemoryRequest))
    val networkPortResp = new ReadyValidChannelsIO(Entry(new MemoryResponse))
  })

  val rom = VecInit(program.map(_.U(32.W)))

  io.networkPortReq.rx.ready := io.networkPortResp.tx.ready
  io.networkPortResp.tx.expand(
    _.valid := io.networkPortReq.rx.valid,
    _.bits.expand(
      _.core := io.networkPortReq.rx.bits.core,
      _.data.data := rom(io.networkPortReq.rx.bits.data.addr(31, 2))
    )
  )

  io.networkPortReq.tx.expand(
    _.valid := 0.B,
    _.bits.expand(
      _.core := 0.U,
      _.data := DontCare
    )
  )
  io.networkPortResp.rx.ready := 0.B

}