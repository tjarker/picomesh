package routing

import chisel3._
import chisel3.util.DecoupledIO
import routing.simple.DirectionBundle
import scala.collection.immutable.SeqMap
import liftoff._

trait PacketPortLike {
  type P <: Data // packet type
  def dir: Direction
  def chisel: DecoupledIO[P]
}

trait RouterPortLike {
  type P <: Data
  def dir: Direction
  def coord: Coord
  def ingressPort: PacketPortLike
  def egressPort: PacketPortLike
  def chisel: Bundle {
    val ingress: DecoupledIO[P]
    val egress: DecoupledIO[P]
  }
}

class DirectionMap[T <: Data](directions: Seq[Direction], gen: => T) extends Record {
  val map = SeqMap(directions.map(dir => dir -> gen):_*)
  override def elements: SeqMap[String,Data] = SeqMap(map.toSeq.map { case (dir, signal) => 
    dir.toString -> signal 
  }:_*)
  def apply(dir: Direction): T = this.map(dir)
  def all: Seq[T] = directions.map(map)
  def getMap: Map[Direction, T] = this.map
}

class RouterDebugElement[P <: Data](packetGen: => P) extends Bundle {
  val reqs = new DirectionMap(Direction.all, Bool())
  val valid = Bool()
  val packet = packetGen

  def pretty: String = {
    if (valid.litToBoolean) {
      val outdir = reqs.getMap.find(_._2.litToBoolean) match {
        case Some((dir, _)) => dir.toString
        case None => "?"
      }
      s"${packet.peek()} -> $outdir"
    } else {
      "-"
    }
  }
}
class RouterDebugPort[P <: Data](packetGen: => P) extends DirectionMap(Direction.all, new RouterDebugElement(packetGen)){

}

trait RouterDebugPortLike {
  type P <: Data
  def apply(dir: Direction): RouterDebugElement[P]

}

trait RouterLike {
  type P <: Data
  def coord: Coord
  def port(dir: Direction): RouterPortLike
  def allPorts: Seq[RouterPortLike] = {
    Seq(North, South, East, West, Local).map(port)
  }
  def debugPort: Option[RouterDebugPort[P]]
}

abstract class RouterFactory {
  def createRouter(coord: Coord): RouterLike
}
