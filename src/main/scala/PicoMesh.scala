
import chisel3._
import chisel3.util._
import Util._
import soc.ReadyValidChannelsIO
import s4noc.Entry

/**
  * A 3x3 S4NoC with 6 picorv cores, 2 memory nodes (1kb each) and an access node for the ponte UART-bridge
  * 
  * The layout is as follows:
  * [6] core3      - [7] core4    - [8] core5
  *   |              |              |
  * [3] core0      - [4] core1    - [5] core2
  *   |              |              |
  * [0] accessNode - [1] memLow   - [2] memHigh
  * 
  * Cores boot from a bootloader rom in the access node, which looks up a core-local boot address and jumps there.
  * 
  * By default the boot address of all cores points to another program rom in the acces node, containing a simple program,
  * that accumulates the sum of core-ids via the scratchpads of the cores and writes the result to memory.
  *
  * @param c config
  */
class PicoMeshBig(c: PicoRvConfig, bootBinPath: String, romBinPath: String) extends Module {

  val io = IO(new Bundle {
    val pontePort = Flipped(new ponte.PonteAccessPort)
  })
  

  val s4nocReq = Module(new CustomS4NoC(9, new MemoryRequest, Seq.range(0, 9)))
  val s4nocResp = Module(new CustomS4NoC(9, new MemoryResponse(1), Seq.range(0, 9)))

  val picoConf = c.copy(
    progAddrReset = 0x0000_0000,
    stackAddr = 0x20000400
  )

  

  val coreToCoreidMap = Map(
    0 -> 3,
    1 -> 4,
    2 -> 5,
    3 -> 6,
    4 -> 7,
    5 -> 8
  )

  val coreIds = Seq.range(3, 9)

  val cores = coreIds.map { case (coreId) => Module(new PicoNode(coreId, picoConf)) }

  for ((i, coreId) <- coreToCoreidMap) {
    s4nocReq.io.networkPort(coreId) <> cores(i).io.networkPortReq
    s4nocResp.io.networkPort(coreId) <> cores(i).io.networkPortResp
  }

  // val romNode = Module(new RomNode(Util.Binary.load("build/prog/program.bin")))
  val memLow = Module(new OpenRamMemoryNode)
  val memHigh = Module(new OpenRamMemoryNode)
  val accessNode = Module(new AccessNode(bootBinPath, romBinPath))
  io.pontePort <> accessNode.io.pontePort


  // connect core to s4nocReq
  s4nocReq.io.networkPort(0) <> accessNode.io.networkPortReq
  s4nocResp.io.networkPort(0) <> accessNode.io.networkPortResp

  // s4nocReq.io.networkPort(12) <> romNode.io.networkPortReq
  // s4nocResp.io.networkPort(12) <> romNode.io.networkPortResp

  s4nocReq.io.networkPort(1) <> memLow.io.networkPortReq
  s4nocResp.io.networkPort(1) <> memLow.io.networkPortResp

  s4nocReq.io.networkPort(2) <> memHigh.io.networkPortReq
  s4nocResp.io.networkPort(2) <> memHigh.io.networkPortResp

}


