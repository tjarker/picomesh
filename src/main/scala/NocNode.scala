import soc.ReadyValidChannelsIO
import s4noc.Entry

trait NocNode {
  val io: {
    val networkPortReq: ReadyValidChannelsIO[Entry[MemoryRequest]]
    val networkPortResp: ReadyValidChannelsIO[Entry[MemoryResponse]]
  }
}
