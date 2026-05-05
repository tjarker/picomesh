import chisel3._
import soc.ReadyValidChannelsIO
import s4noc._

import Util._

class S4NoC[T <: Data](nodes: Int, dt: => T) extends Module  {

  val conf = Config(nodes, 
    BubbleType(1),
      BubbleType(1),
      DoubleBubbleType(1),
      dt.getWidth
  )

  val io = IO(new Bundle {
    val networkPort = Vec(conf.n, Flipped(new ReadyValidChannelsIO(Entry(dt))))
  })


  val net = Module(new Network(conf.dim, dt))

  for (i <- 0 until conf.n) {
    // can use NetworkInterfaceSingle for paper numbers
    val ni = Module(new NetworkInterface(i, conf, dt))
    net.io.local(i) <> ni.io.local
    io.networkPort(i) <> ni.io.networkPort
  }
}