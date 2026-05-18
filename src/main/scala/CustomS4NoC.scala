import chisel3._
import soc.ReadyValidChannelsIO
import s4noc._

import Util._


/**
  * S4NoC with a polymorphic type and the option to only instantiate network interfaces for a subset of the nodes for non-square NoC topologies.
  *
  * @param nodes
  * @param dt
  * @param usedPorts
  */
class CustomS4NoC[T <: Data](nodes: Int, dt: => T, usedPorts: Seq[Int]) extends Module  {

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
    if (usedPorts.contains(i)) {
      // can use NetworkInterfaceSingle for paper numbers
      val ni = Module(new CustomNetworkInterface(i, conf, dt))
      net.io.local(i) <> ni.io.local
      io.networkPort(i) <> ni.io.networkPort
    } else {
      net.io.local(i).in.expand(
        _.valid := 0.B,
        _.data := 0.U.asTypeOf(net.io.local(i).in.data)
      )
      io.networkPort(i).rx.valid := false.B
      io.networkPort(i).rx.bits := 0.U.asTypeOf(io.networkPort(i).tx.bits)
      io.networkPort(i).tx.ready := true.B
    }
  }
}