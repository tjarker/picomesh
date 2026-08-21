import chisel3._

import s4noc._
import s4noc.Const._
import ponte.Ponte

object Battuta extends App {
  emitVerilog(new Battuta("build/bootloader/bootloader.bin", "build/rom/rom.bin"), Array("--target-dir", "generated"))
}

class Battuta(bootBinPath: String, romBinPath: String) extends Module {
  val io= IO(new Bundle {
    val ponteTx = Output(Bool())
    val ponteRx = Input(Bool())
  })

  val syncReset = RegNext(RegNext(reset))

  val ponte = Module(new Ponte(10_000_000, 9600))
  ponte.reset := syncReset

  val array = Module(new BattutaArray(PicoRvConfig.small, bootBinPath, romBinPath))
  array.reset := syncReset

  ponte.io.uart.rx := io.ponteRx
  io.ponteTx := ponte.io.uart.tx
  array.io.pontePort <> ponte.io.port

}

class BattutaArray(c: PicoRvConfig, bootBinPath: String, romBinPath: String) extends Module {

  val io = IO(new Bundle {
    val pontePort = Flipped(new ponte.PonteAccessPort)
  })

  val picoConf = c.copy(
    progAddrReset = 0x0000_0000,
    //stackAddr = 0x2000_0400
  )

  val s4nocConf = s4noc.Config(
    n = 9,
    BubbleType(1),
      BubbleType(1),
      DoubleBubbleType(1),
      0
  )
  val s4nocSchedule = Schedule(3).schedule

  val coreTiles = Seq.tabulate(6) { i =>
    Module(new PicoTile(i + 3, s4nocConf, s4nocSchedule, picoConf))
  }


  // barrier logic
  val arrived = coreTiles.map(_.barrierPort.barrierArrived)
  val released = RegInit(0.B)
  when(released) {
    released := 0.B
  }.elsewhen(arrived.reduce(_ && _)) {
    released := 1.B
  }
  coreTiles.foreach { c =>
    c.barrierPort.barrierRelease := released
  }


  coreTiles.foreach { c =>
    c.reset := RegNext(reset)
  }

  val accessTile = Module(new AccessTile(0, s4nocConf, s4nocSchedule, bootBinPath, romBinPath))
  accessTile.reset := RegNext(reset)
  accessTile.pontePort <> io.pontePort
  val memLowTile = Module(new MemoryTile(1, s4nocConf, s4nocSchedule))
  memLowTile.reset := RegNext(reset)
  val memHighTile = Module(new MemoryTile(2, s4nocConf, s4nocSchedule))
  memHighTile.reset := RegNext(reset)

  val tiles = Seq(accessTile, memLowTile, memHighTile) ++ coreTiles

  def connect(r1: Int, p1: Int, r2: Int, p2: Int): Unit = {
    tiles(r1).io.req(p1).in := tiles(r2).io.req(p2).out
    tiles(r2).io.req(p2).in := tiles(r1).io.req(p1).out
    tiles(r1).io.resp(p1).in := tiles(r2).io.resp(p2).out
    tiles(r2).io.resp(p2).in := tiles(r1).io.resp(p1).out
  }

  val n = 3
  for (i <- 0 until n) {
    for (j <- 0 until n) {
      val r = i * n + j
      connect(r, EAST, i * n + (j + 1) % n, WEST)
      connect(r, SOUTH, (i + 1) % n * n + j, NORTH)
    }
  }

}
