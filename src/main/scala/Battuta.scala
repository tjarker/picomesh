import chisel3._
import chisel3.util.experimental.loadMemoryFromFileInline

import s4noc._
import s4noc.Const._
import ponte.Ponte

object Battuta extends App {
  emitVerilog(new Battuta("build/bootloader/bootloader.bin", "build/rom/rom.bin"), Array("--target-dir", "generated"))
}

object WideBattuta extends App {
  emitVerilog(new WideBattuta("build/bootloader/bootloader.bin", "build/rom/rom.bin"), Array("--target-dir", "generated"))
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

class WideBattuta(bootBinPath: String, romBinPath: String) extends Module {
  val io= IO(new Bundle {
    val ponteTx = Output(Bool())
    val ponteRx = Input(Bool())
  })

  val syncReset = RegNext(RegNext(reset))

  val ponte = Module(new Ponte(10_000_000, 9600))
  ponte.reset := syncReset

  val array = Module(new WideBattutaArray(PicoRvConfig.small, bootBinPath, romBinPath))
  array.reset := syncReset

  ponte.io.uart.rx := io.ponteRx
  io.ponteTx := ponte.io.uart.tx
  array.io.pontePort <> ponte.io.port

}

/** @param prog program ROM contents: baked in as taped out, or loaded when the simulation starts
  * @param memLow memory of node 1: the OpenRAM macro, or a larger SyncReadMem for simulation
  * @param memHigh memory of node 2
  * @param exposeTrap add an output with one trap bit per core, for simulation
  */
class WideBattutaArray(c: PicoRvConfig, bootBinPath: String, prog: ProgramRom, memLow: MemoryImpl = MemoryImpl.OpenRam, memHigh: MemoryImpl = MemoryImpl.OpenRam, exposeTrap: Boolean = false) extends Module {

  def this(c: PicoRvConfig, bootBinPath: String, romBinPath: String) = this(c, bootBinPath, ProgramRom.Baked(romBinPath))

  val io = IO(new Bundle {
    val pontePort = Flipped(new ponte.PonteAccessPort)
    val trap = if (exposeTrap) Some(Output(UInt(6.W))) else None
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
  val reqSchedule = Schedule(3)
  val respSchedule = new InvertedSchedule(3)

  val coreTiles = Seq.tabulate(6) { i =>
    Module(new WidePicoTile(i + 3, s4nocConf, reqSchedule, respSchedule, picoConf, exposeTrap))
  }
  io.trap.foreach(_ := VecInit(coreTiles.map(_.trap.get)).asUInt)


  val bootRom = VecInit(Util.Binary.load(bootBinPath).map(_.U(32.W)))

  // the word the cores should be fetching from the program ROM, for the instrCheck compare
  val progInstr: UInt => UInt = prog match {
    case ProgramRom.Baked(romBinPath) =>
      val progRom = VecInit(Util.Binary.load(romBinPath).map(_.U(32.W)))
      addr => progRom(addr(26, 2))
    case ProgramRom.Loaded(hexPath, lineBits) =>
      val progRom = Mem(1 << lineBits, UInt(128.W))
      loadMemoryFromFileInline(progRom, new java.io.File(hexPath).getAbsolutePath)
      addr => (progRom(addr(lineBits + 3, 4)) >> (addr(3, 2) ## 0.U(5.W)))(31, 0)
  }


  coreTiles.foreach { c =>
    c.instrCheckPort.instr := Mux(
      c.instrCheckPort.addr(27),
      progInstr(c.instrCheckPort.addr),
      bootRom(c.instrCheckPort.addr(26, 2))
    )
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

  def memoryTile(id: Int, memory: MemoryImpl): Tile[_, _] = memory match {
    case MemoryImpl.OpenRam => Module(new MemoryTile(id, s4nocConf, reqSchedule, respSchedule, 4))
    case MemoryImpl.SimSram(addrBits, initHex) => Module(new SimMemoryTile(id, s4nocConf, reqSchedule, respSchedule, 4, addrBits, initHex))
  }

  val accessTile = Module(new WideAccessTile(0, s4nocConf, reqSchedule, respSchedule, bootBinPath, prog))
  accessTile.reset := RegNext(reset)
  accessTile.pontePort <> io.pontePort
  val memLowTile = memoryTile(1, memLow)
  memLowTile.reset := RegNext(reset)
  val memHighTile = memoryTile(2, memHigh)
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
  val reqSchedule = Schedule(3)
  val respSchedule = new InvertedSchedule(3)

  val coreTiles = Seq.tabulate(6) { i =>
    Module(new PicoTile(i + 3, s4nocConf, reqSchedule, respSchedule, picoConf))
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

  val accessTile = Module(new AccessTile(0, s4nocConf, reqSchedule, respSchedule, bootBinPath, romBinPath))
  accessTile.reset := RegNext(reset)
  accessTile.pontePort <> io.pontePort
  val memLowTile = Module(new MemoryTile(1, s4nocConf, reqSchedule, respSchedule, 1))
  memLowTile.reset := RegNext(reset)
  val memHighTile = Module(new MemoryTile(2, s4nocConf, reqSchedule, respSchedule, 1))
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
