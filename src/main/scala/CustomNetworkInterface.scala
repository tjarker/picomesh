import chisel3._
import chisel3.util._
import chisel.lib.fifo._
import soc.ReadyValidChannelsIO
import s4noc._

class PassthroughDummyFifo[T <: Data](gen: T, depth: Int) extends Fifo(gen: T, depth: Int) {

  io.enq.ready := io.deq.ready
  io.deq.valid := io.enq.valid
  io.deq.bits := io.enq.bits
}

/**
  * A S4NoC network interface with no split buffers, a single tx buffer, but 6 receive buffers, to reduce resources.
  *
  * @param id
  * @param conf
  * @param dt
  */
class CustomNetworkInterface[T <: Data](id: Int, conf: Config, dt: T) extends Module {
  val io = IO(new Bundle {
    val networkPort = Flipped(new ReadyValidChannelsIO(Entry(dt)))
    val local = Flipped(new ChannelIO(dt))
  })


  def doubleBubbleFifo(dt: T, depth: Int) = {
    Module(new DoubleBufferFifo(Entry(dt), depth))
  }
  def bubbleFifo(dt: T, depth: Int) = {
    Module(new BubbleFifo(Entry(dt), depth))
  }
  def dummyFifo(dt: T, depth: Int) = {
    Module(new PassthroughDummyFifo(Entry(dt), depth))
  }

  val sched = Schedule(conf.dim)
  val len = sched.schedule.length
  // from slot count to destination core
  val translationTableSend = VecInit(Seq.fill(len)(0.U(8.W)))
  // from slot count to sending core
  val translationTableRcv = VecInit(Seq.fill(len)(0.U(8.W)))
  val validSlot = VecInit(Seq.fill(len)(false.B))
  println(s"Core $id:")
  for (i <- 0 until len) {
    val dest = sched.timeToDest(id, i).dest

    if (dest != -1) {
      translationTableSend(i) := dest.U
      validSlot(i) := true.B
    }
    val src = sched.timeToSource(id, i)
    if (src != -1) {
      translationTableRcv(i) := src.U
    }
    println(s"  Slot $i: dest=$dest, src=$src")
  }

  var acc = 0
  // calculate response times
  for (i <- 0 until conf.n) {
    // find arrival time for i
    var arrivalTime = -1
    for (j <- 0 until len) {
      val src = sched.timeToSource(id, j)
      if (src == i) {
        arrivalTime = j
      }
    }
    // find next transmission slot back to i
    var responseTime = -1
    for (j <- 0 until len) {
      val dest = sched.timeToDest(id, j).dest
      if (dest == i) {
        responseTime = j
      }
    }
    if (arrivalTime != -1 && responseTime != -1) {
      var diff = (responseTime - arrivalTime + len) % len
      if (diff == 0)  diff = len
      println(s"  Response time for $i: $diff")
      acc = acc + diff
    } else {
      println(s"  Response time for $i: N/A")
    }
  }

  val avgResponseTime = acc / (conf.n - 1)
  println(s"  Average response time: $avgResponseTime")

  val regCnt = RegInit(0.U(log2Up(len).W))
  regCnt := Mux(regCnt === (len - 1).U, 0.U, regCnt + 1.U)
  // TDM schedule starts one cycle later for read data delay of OneWayMemory
  // Maybe we can use that delay here as well for something good
  val timeSlotReg = RegNext(regCnt, init = 0.U)

  // TX
  // in/out direction is from the network view
  // flipped here
  val txFifo = dummyFifo(dt, 1)
  io.networkPort.tx <> txFifo.io.enq

  // val toCore = translationTable(txFifo.io.deq.bits.core)
  val toCore = txFifo.io.deq.bits.core

  // TODO: Minimum should be a single register. Could be enough in most cases.
  // TODO: we are wasting resources when also having the core # in this FIFO
  val splitBuffers = (0 until conf.n).map(_ => bubbleFifo(dt, 1))
  for (i <- 0 until conf.n) {
    splitBuffers(i).io.enq.bits.data := txFifo.io.deq.bits.data
    splitBuffers(i).io.enq.bits.core := i.U
  }

  // there must be a more elegant solution
  val enqReadyVec = VecInit(Seq.fill(conf.n)(false.B))
  val enqValidVec = VecInit(Seq.fill(conf.n)(false.B))
  val deqValidVec = VecInit(Seq.fill(conf.n)(false.B))
  val deqDataVec = Wire(Vec(conf.n, dt))
  val deqReadyVec = VecInit(Seq.fill(conf.n)(false.B))

  // connections
  for (i <- 0 until conf.n) {
    enqReadyVec(i) := splitBuffers(i).io.enq.ready
    splitBuffers(i).io.enq.valid := enqValidVec(i)
    deqValidVec(i) := splitBuffers(i).io.deq.valid
    deqDataVec(i) := splitBuffers(i).io.deq.bits.data
    splitBuffers(i).io.deq.ready := deqReadyVec(i)
  }
  // the following is a combinational valid/ready path from a split buffer
  txFifo.io.deq.ready := enqReadyVec(toCore)
  when (txFifo.io.deq.valid) {
    when (enqReadyVec(toCore)) {
      enqValidVec(toCore) := true.B
    }
  }

  val core = translationTableSend(timeSlotReg)
  val slotOk = validSlot(timeSlotReg)
  when (slotOk) { deqReadyVec(core) := true.B }

  val valid = deqValidVec(core) && slotOk
  io.local.in.data := deqDataVec(core)
  io.local.in.valid := valid

  // RX
  val rxFifo = doubleBubbleFifo(dt, 1)
  // rxFifo.io.enq.ready is ignored. When the FIFO is full, packets are simply dropped.
  rxFifo.io.enq.valid := io.local.out.valid
  rxFifo.io.enq.bits.data := io.local.out.data
  rxFifo.io.enq.bits.core := translationTableRcv(timeSlotReg)

  io.networkPort.rx <> rxFifo.io.deq
}
