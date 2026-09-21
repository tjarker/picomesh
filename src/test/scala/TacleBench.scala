import chisel3._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import liftoff._

import java.io.{File, FileInputStream, PrintWriter}
import java.util.Properties
import scala.sys.process._

/** How one benchmark run ended. */
sealed trait TacleOutcome
object TacleOutcome {
  case class CoreResult(ret: Int, instructions: Long, cycles: Long, done: Boolean)
  case class Finished(cores: Seq[CoreResult]) extends TacleOutcome
  case class Trapped(cores: Seq[Int], cycle: Int) extends TacleOutcome
  case class TimedOut(cycle: Int) extends TacleOutcome
}

/** Runs the time-predictable integer TACLe kernels (no soft-float, no recursion, no software
  * multiplication or division; the list is in tacle.mk) on all six cores at the same time.
  *
  * `make tacle` (src/c/tacle/tacle.mk) links every benchmark once per core window. One Verilator
  * model per variant serves all benchmarks: its program ROM and memLow are loaded by $readmemh
  * from fixed files, rewritten before each simulation. Every core reports its return value and
  * its own instruction and cycle counters for the benchmark's entry function through its
  * scratchpad (see runner.c and crt0.S).
  *
  * Results go to build/tacle/results/<variant>/<benchmark>.csv and are collected in
  * build/tacle/results-<variant>.csv.
  *
  * Options, as environment variables because the test JVM is forked:
  *   TACLE_MAX_CYCLES   cycle budget per benchmark (default 2e9)
  *   TACLE_POLL_CYCLES  longest interval between completion checks (default 1e6)
  */
abstract class TacleSuite[M <: Module] extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  import TacleOutcome._

  /** Names this variant's model, hex files and results. */
  def variant: String
  /** Words per program ROM line: the wide access node reads 4-word bundles, the narrow one words. */
  def romWordsPerLine: Int
  /** The compiled model, built once for all benchmarks. */
  def model: ChiselModel[M]
  def ports(dut: M): BattutaSim.Ports
  /** One bit per core, high while that core is halted in a trap. */
  def trap(dut: M): UInt

  val buildDir = new File("build/tacle")
  def simDir = new File(buildDir, s"sim-$variant")
  def progHex = new File(simDir, "prog.hex")
  def memLowHex = new File(simDir, "memlow.hex")

  val maxCycles = sys.env.get("TACLE_MAX_CYCLES").map(_.toInt).getOrElse(2_000_000_000)
  val maxPollCycles = sys.env.get("TACLE_POLL_CYCLES").map(_.toInt).getOrElse(1_000_000)

  val DoneMarker = BigInt(0x7AC1E0D0L)

  // build first: the benchmark list and memory layout come from the build
  buildDir.mkdirs()
  val makeLog = new File("build/tacle-make.log")
  if ((Seq("make", "-k", "comp-bootloader", "tacle") #> makeLog).! != 0) {
    println(s"[TacleBench] some targets failed to build, see $makeLog")
  }

  val layout = {
    val p = new Properties()
    val in = new FileInputStream(new File(buildDir, "layout.properties"))
    try p.load(in) finally in.close()
    p
  }
  val romBase = java.lang.Long.decode(layout.getProperty("romBase")).longValue
  val dataBase = java.lang.Long.decode(layout.getProperty("dataBase")).longValue
  val windowShift = layout.getProperty("windowShift").toInt
  val benchmarks = layout.getProperty("benchmarks").split(" ").toSeq

  def bitsFor(n: Long): Int = 64 - java.lang.Long.numberOfLeadingZeros(n - 1)
  /** Byte offset within a ROM line. */
  def romLineShift = bitsFor(4L * romWordsPerLine)
  // the dispatcher's window and six text windows, in lines
  def romLineBits = bitsFor((7L << windowShift) / (4L * romWordsPerLine))
  // six data windows, in words
  def memLowAddrBits = bitsFor((6L << windowShift) / 4)

  /** Places the dispatcher and each core's text and data images at their memory indices. */
  def writeImages(bench: String): Unit = {
    def romLine(addr: Long): Long = (addr & ((1L << (romLineBits + romLineShift)) - 1)) >> romLineShift
    def memLowWord(addr: Long): Long = (addr >> 2) & ((1L << memLowAddrBits) - 1)

    val text = (0 until 6).map { k =>
      romLine(romBase + ((k + 1L) << windowShift)) -> Util.Binary.load(s"$buildDir/$bench/core$k.text.bin")
    }
    BattutaSim.writeHex(progHex, (romLine(romBase) -> Util.Binary.load(s"$buildDir/dispatch.text.bin")) +: text, romWordsPerLine)

    val data = (0 until 6).map { k =>
      memLowWord(dataBase + (k.toLong << windowShift)) -> Util.Binary.load(s"$buildDir/$bench/core$k.data.bin")
    }
    BattutaSim.writeHex(memLowHex, data, 1)
  }

  def run(dut: M): TacleOutcome = {
    dut.reset.poke(true.B)
    dut.clock.step(1)
    dut.reset.poke(false.B)

    val p = ports(dut)
    val trapBits = trap(dut)
    var poll = 10_000
    var outcome: Option[TacleOutcome] = None
    while (outcome.isEmpty) {
      dut.clock.step(poll)
      poll = math.min(poll * 2, maxPollCycles)

      val t = trapBits.peek().litValue
      if (t != 0) {
        outcome = Some(Trapped((0 until 6).filter(k => t.testBit(k)), dut.clock.cycle.toInt))
      } else if ((0 until 6).forall(p.inReset)) {
        outcome = Some(Finished((0 until 6).map { k =>
          CoreResult(
            p.scratchpad(k, 0).toInt,
            p.scratchpad(k, 1).toLong,
            p.scratchpad(k, 2).toLong,
            p.scratchpad(k, 3) == DoneMarker
          )
        }))
      } else if (dut.clock.cycle >= maxCycles) {
        outcome = Some(TimedOut(dut.clock.cycle.toInt))
      }
    }
    outcome.get
  }

  // outside the benchmark directories, which `make tacle` recreates on every rebuild
  def resultDir = new File(buildDir, s"results/$variant")
  def resultFile(bench: String) = new File(resultDir, s"$bench.csv")
  resultDir.mkdirs()

  BattutaSim.filterReporting()

  benchmarks.foreach { bench =>
    s"TACLe ($variant)" should s"run $bench on all six cores" in {
      resultFile(bench).delete()
      assert(new File(buildDir, s"$bench/stamp").exists(), s"$bench did not build, see $makeLog")

      writeImages(bench)
      val sim = model.simulate(s"$buildDir/$bench/sim-$variant/".toDir)(run)

      Option(sim.result) match {
        case None =>
          fail(s"$bench: the simulation threw, see the log above")
        case Some(Trapped(cores, cycle)) =>
          fail(s"$bench: cores ${cores.mkString(", ")} trapped by cycle $cycle")
        case Some(TimedOut(cycle)) =>
          fail(s"$bench: not finished after $cycle cycles (TACLE_MAX_CYCLES)")
        case Some(Finished(cores)) =>
          val out = new PrintWriter(resultFile(bench))
          try cores.zipWithIndex.foreach { case (c, k) =>
            out.println(f"$bench,$k,${c.ret},${c.instructions},${c.cycles},${c.cycles.toDouble / c.instructions}%.3f,${sim.cycles},${sim.freq}%.1f")
          } finally out.close()

          println(s"$bench (${sim.cycles} simulated cycles at ${sim.freq.round} kHz)")
          cores.zipWithIndex.foreach { case (c, k) =>
            println(f"  core $k: return ${c.ret}%d, ${c.instructions}%d instructions, ${c.cycles}%d cycles, CPI ${c.cycles.toDouble / c.instructions}%.3f")
          }

          all(cores.map(_.done)) shouldBe true
          all(cores.map(_.ret)) shouldBe 0
          all(cores.map(_.instructions)) should be > 0L
      }
    }
  }

  override def afterAll(): Unit = {
    val out = new PrintWriter(new File(buildDir, s"results-$variant.csv"))
    try {
      out.println("benchmark,core,return,instructions,cycles,cpi,simulated_cycles,sim_khz")
      benchmarks.map(resultFile).filter(_.exists).foreach { f =>
        val src = scala.io.Source.fromFile(f)
        try src.getLines().foreach(out.println) finally src.close()
      }
    } finally out.close()
  }
}

/** The taped-out system with the prefetch buffer and the 4-word-wide response network. */
class TacleBench extends TacleSuite[WideBattutaArray] {
  def variant = "prefetch"
  def romWordsPerLine = 4

  lazy val model = ChiselModel(
    new WideBattutaArray(
      PicoRvConfig.small,
      "build/bootloader/bootloader.bin",
      ProgramRom.Loaded(progHex.getPath, romLineBits),
      memLow = MemoryImpl.SimSram(memLowAddrBits, Some(memLowHex.getPath)),
      exposeTrap = true
    ),
    s"$buildDir/model-$variant/".toDir,
    Seq(),
    BattutaSim.verilatorArguments,
    Seq()
  )

  def ports(dut: WideBattutaArray) = new BattutaSim.Ports(dut.clock, dut.io.pontePort)
  def trap(dut: WideBattutaArray) = dut.io.trap.get
}

/** The same system without prefetching: one instruction per network round trip. */
class TacleBenchNoPrefetch extends TacleSuite[BattutaArray] {
  def variant = "noprefetch"
  def romWordsPerLine = 1

  lazy val model = ChiselModel(
    new BattutaArray(
      PicoRvConfig.small,
      "build/bootloader/bootloader.bin",
      ProgramRom.Loaded(progHex.getPath, romLineBits),
      memLow = MemoryImpl.SimSram(memLowAddrBits, Some(memLowHex.getPath)),
      exposeTrap = true
    ),
    s"$buildDir/model-$variant/".toDir,
    Seq(),
    BattutaSim.verilatorArguments,
    Seq()
  )

  def ports(dut: BattutaArray) = new BattutaSim.Ports(dut.clock, dut.io.pontePort)
  def trap(dut: BattutaArray) = dut.io.trap.get
}
