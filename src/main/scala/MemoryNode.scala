import chisel3._
import soc.ReadyValidChannelsIO
import s4noc.Entry

import Util._

class MemoryNode extends Module with NocNode {
  val io = IO(new Bundle {
    val networkPortReq = new ReadyValidChannelsIO(Entry(new MemoryRequest))
    val networkPortResp = new ReadyValidChannelsIO(Entry(new MemoryResponse))
  })

  val mem = SyncReadMem(1024, UInt(32.W))

  val readData = mem.read(io.networkPortReq.rx.bits.data.addr(31, 2))

  io.networkPortReq.rx.ready := io.networkPortResp.tx.ready
  io.networkPortResp.tx.expand(
    _.valid := io.networkPortReq.rx.valid && !io.networkPortReq.rx.bits.data.write,
    _.bits.expand(
      _.core := io.networkPortReq.rx.bits.core,
      _.data.data := readData
    )
  )

  // write 
  when(io.networkPortReq.rx.valid && io.networkPortReq.rx.bits.data.write) {
    mem.write(
      io.networkPortReq.rx.bits.data.addr(31, 2),
      io.networkPortReq.rx.bits.data.data
    )
  }

  io.networkPortReq.tx.expand(
    _.valid := 0.B,
    _.bits.expand(
      _.core := 0.U,
      _.data := DontCare
    )
  )
  io.networkPortResp.rx.ready := 0.B

}