package routing.simple

import chisel3._
import chisel3.util._

import routing.Direction._
import routing._

import liftoff._

import liftoff.pathToFileOps

import scala.collection.immutable.SeqMap
import s4noc.S4NoC

case class SimpleNocParams[T <: Data](
  nx: Int, 
  ny: Int, 
  payloadGen: () => T,
  bufferFactory: BufferFactory,
  arbiterFactory: ArbiterFactory,
  routingPolicy: RoutingPolicy,
  wrappedTopology: Boolean,
  debug: Boolean = false
) {
  val dimensions: (Int, Int) = (nx, ny)
  def payloadType: T = payloadGen()
  def xCoordType: UInt = UInt(log2Ceil(nx).W)
  def yCoordType: UInt = UInt(log2Ceil(ny).W)
}

object SimpleNocParams {
  def default: SimpleNocParams[UInt] = SimpleNocParams[UInt](
    nx = 4,
    ny = 4,
    payloadGen = () => UInt(32.W),
    bufferFactory = ChiselQueueBuffer,
    arbiterFactory = ChiselArbiter,
    routingPolicy = XYRouting,
    wrappedTopology = false,
    debug = false
  )
}

class SimpleRouterFactory[T <: Data](p: SimpleNocParams[T]) extends RouterFactory {
  override def createRouter(coord: Coord): RouterLike = {
    Module(new SimpleRouter[T](coord)(p)).suggestName(s"router_${coord.x}_${coord.y}")
  }
}

class CoordTaggedBundle[T <: Data](nx: Int, ny: Int, gen: (Int, Int) => T) extends Record {

  val map = SeqMap(Seq.tabulate(nx, ny) { (x, y) =>
    val coord = Coord(x, y)
    coord -> gen(x, y)
  }.flatten:_*)

  override def elements: SeqMap[String, Data] = map.map{ case (Coord(x,y), signal) => s"${x}_${y}" -> signal}

  def apply(coord: Coord): T = this.map(coord)

  def all: Seq[T] = map.values.toSeq

}

abstract class SimpleNoc[T <: Data](implicit val p: SimpleNocParams[T]) extends Module {
  val ports: CoordTaggedBundle[SimpleRouterPort[T]]
  val debugPorts: Option[CoordTaggedBundle[RouterDebugPort[SimplePacket[T]]]]


  def nocName: String = s"SimpleNoc_${p.bufferFactory.name}_${p.arbiterFactory.name}"

  override val desiredName: String = nocName
}



class SimpleS4nocWrapper[T <: Data](implicit p: SimpleNocParams[T]) extends SimpleNoc[T] {

  import s4noc._

  override def nocName: String = s"S4NoC"

  val ports = IO(new CoordTaggedBundle(p.nx, p.ny, (x,y) => new SimpleRouterPort(Coord(x,y), Local)(p)))
  val debugPorts = None


  val noc = Module(new S4NoC(s4noc.Config(
    n = p.nx * p.ny,
    tx = BubbleType(1),
    split = BubbleType(2),
    rx = DoubleBubbleType(2),
    width = p.payloadGen().getWidth
  )))

  for (x <- 0 until p.nx; y <- 0 until p.ny) {
    val idx = y * p.nx + x
    noc.io.networkPort(idx).tx.valid := ports(Coord(x,y)).ingress.valid
    noc.io.networkPort(idx).tx.bits.data := ports(Coord(x,y)).ingress.bits.payload.asUInt
    noc.io.networkPort(idx).tx.bits.core := ports(Coord(x,y)).ingress.bits.dest.x + ports(Coord(x,y)).ingress.bits.dest.y * p.nx.U
    ports(Coord(x,y)).ingress.ready := noc.io.networkPort(idx).tx.ready

    ports(Coord(x,y)).egress.valid := noc.io.networkPort(idx).rx.valid
    ports(Coord(x,y)).egress.bits.payload := noc.io.networkPort(idx).rx.bits.data.asTypeOf(p.payloadGen())
    ports(Coord(x,y)).egress.bits.dest.x := noc.io.networkPort(idx).rx.bits.core % p.nx.U(8.W)
    ports(Coord(x,y)).egress.bits.dest.y := noc.io.networkPort(idx).rx.bits.core / p.nx.U(8.W)
    ports(Coord(x,y)).egress.bits.dest.southbound := 0.B
    ports(Coord(x,y)).egress.bits.dest.eastbound := 0.B
    noc.io.networkPort(idx).rx.ready := ports(Coord(x,y)).egress.ready
  }


}


class SimpleNocTorus[T <: Data](implicit p: SimpleNocParams[T]) extends SimpleNoc[T] {

  val routers = Seq.tabulate(p.nx, p.ny) { (x, y) =>
    val r = Module(new SimpleRouter(Coord(x, y))(p))
    r.suggestName(s"router_${x}_${y}")
    r
  }

  

  for (x <- 0 until p.nx; y <- 0 until p.ny) {
    val router = routers(x)(y)
    val westRouter = routers((x - 1 + p.nx) % p.nx)(y)
    router.port(West).ingress <> westRouter.port(East).egress
    westRouter.port(East).ingress <> router.port(West).egress

    val eastRouter = routers((x + 1) % p.nx)(y)
    router.port(East).ingress <> eastRouter.port(West).egress
    eastRouter.port(West).ingress <> router.port(East).egress

    val southRouter = routers(x)((y + 1) % p.ny)
    router.port(South).ingress <> southRouter.port(North).egress
    southRouter.port(North).ingress <> router.port(South).egress

    val northRouter = routers(x)((y - 1 + p.ny) % p.ny)
    router.port(North).ingress <> northRouter.port(South).egress
    northRouter.port(South).ingress <> router.port(North).egress
  }

  val localRouterPorts = routers.flatten.map(_.port(Local))

  val ports = IO(new CoordTaggedBundle(p.nx, p.ny, (x,y) => new SimpleRouterPort(Coord(x,y), Local)(p)))
  val debugPorts = if (p.debug) Some(IO(new CoordTaggedBundle(p.nx, p.ny, (x,y) => new RouterDebugPort(new SimplePacket)))) else None


  localRouterPorts.foreach { routerPort =>
    ports(routerPort.coord).ingress <> routerPort.ingress
    routerPort.egress <> ports(routerPort.coord).egress
  }

  if (p.debug) {
    for (x <- 0 until p.nx; y <- 0 until p.ny) {
      val debugPort = debugPorts.get(Coord(x, y))
      val routerDebugPort = routers(x)(y).debugPort.get
      debugPort <> routerDebugPort
    }
  }


}

class SimpleNocMesh[T <: Data](implicit p: SimpleNocParams[T]) extends SimpleNoc[T] {

  val routers = Seq.tabulate(p.nx, p.ny) { (x, y) =>
    val r = Module(new SimpleRouter(Coord(x, y))(p))
    r.suggestName(s"router_${x}_${y}")
    r
  }

  for (x <- 0 until p.nx; y <- 0 until p.ny) {
    val router = routers(x)(y)
    if (x > 0) {
      val westRouter = routers(x - 1)(y)
      router.port(West).ingress <> westRouter.port(East).egress
      westRouter.port(East).ingress <> router.port(West).egress
    } else {
      router.port(West).ingress.valid := 0.B
      router.port(West).ingress.bits := DontCare
      router.port(West).egress.ready := 1.B
    }
    if (x < p.nx - 1) {
      val eastRouter = routers(x + 1)(y)
      router.port(East).ingress <> eastRouter.port(West).egress
      eastRouter.port(West).ingress <> router.port(East).egress
    } else {
      router.port(East).ingress.valid := 0.B
      router.port(East).ingress.bits := DontCare
      router.port(East).egress.ready := 1.B
    }
    if (y < p.ny - 1) {
      val southRouter = routers(x)(y + 1)
      router.port(South).ingress <> southRouter.port(North).egress
      southRouter.port(North).ingress <> router.port(South).egress
    } else {
      router.port(South).ingress.valid := 0.B
      router.port(South).ingress.bits := DontCare
      router.port(South).egress.ready := 1.B
    }
    if (y > 0) {
      val northRouter = routers(x)(y - 1)
      router.port(North).ingress <> northRouter.port(South).egress
      northRouter.port(South).ingress <> router.port(North).egress
    } else {
      router.port(North).ingress.valid := 0.B
      router.port(North).ingress.bits := DontCare
      router.port(North).egress.ready := 1.B
    }
  }

  val localRouterPorts = routers.flatten.map(_.port(Local))

  val ports = IO(new CoordTaggedBundle(p.nx, p.ny, (x,y) => new SimpleRouterPort(Coord(x,y), Local)(p)))
  val debugPorts = if (p.debug) Some(IO(new CoordTaggedBundle(p.nx, p.ny, (x,y) => new RouterDebugPort(new SimplePacket)))) else None


  localRouterPorts.foreach { routerPort =>
    ports(routerPort.coord).ingress <> routerPort.ingress
    routerPort.egress <> ports(routerPort.coord).egress
  }

  if (p.debug) {
    for (x <- 0 until p.nx; y <- 0 until p.ny) {
      val debugPort = debugPorts.get(Coord(x, y))
      val routerDebugPort = routers(x)(y).debugPort.get
      debugPort <> routerDebugPort
    }
  }
}


object SimpleNocTorus extends App {
  emitVerilog(new SimpleNocTorus()(SimpleNocParams.default))
}
