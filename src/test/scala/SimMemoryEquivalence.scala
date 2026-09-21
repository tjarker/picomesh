import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import liftoff._

import java.io.File

/** The simulation variant (program ROM loaded at start, SyncReadMem SRAMs) must be cycle-exact with
  * the design as taped out. Both run the existing programs; every core reports its own instruction
  * and cycle counters, which have to match.
  */
class SimMemoryEquivalence extends AnyFlatSpec with Matchers {

  val bootBin = "build/bootloader/bootloader.bin"
  val programs = Seq("rom" -> "build/rom/rom.bin", "barrier_demo" -> "build/barrier_demo/barrier_demo.bin")
  val progHex = new File("build/sim-memory-equivalence/prog.hex")

  /** Runs until every core has reset itself; returns (instructions, cycles) per core. */
  def run(model: ChiselModel[WideBattutaArray], runDir: String): Seq[(BigInt, BigInt)] = {
    val result = model.simulate(runDir.toDir) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step(1)
      dut.reset.poke(false.B)

      val ports = new BattutaSim.Ports(dut.clock, dut.io.pontePort)
      while (!(0 until 6).forall(ports.inReset) && dut.clock.cycle < 200000) {
        dut.clock.step(500)
      }
      (0 until 6).map(k => (ports.scratchpad(k, 1), ports.scratchpad(k, 2)))
    }
    result.result
  }

  BattutaSim.filterReporting()

  lazy val simModel = ChiselModel(
    new WideBattutaArray(PicoRvConfig.small, bootBin, ProgramRom.Loaded(progHex.getPath, 18), MemoryImpl.SimSram(20), MemoryImpl.SimSram(20)),
    "build/sim-memory-equivalence/sim-model/".toDir,
    Seq(),
    BattutaSim.verilatorArguments,
    Seq()
  )

  programs.foreach { case (name, bin) =>
    "The simulation memories" should s"be cycle-exact with the taped-out memories for $name" in {
      val tapeoutModel = ChiselModel(
        new WideBattutaArray(PicoRvConfig.small, bootBin, bin),
        s"build/sim-memory-equivalence/$name-tapeout/".toDir,
        Seq(),
        BattutaSim.verilatorArguments,
        Seq()
      )
      val expected = run(tapeoutModel, s"build/sim-memory-equivalence/$name-tapeout/sim/")

      val words = Util.Binary.load(bin)
      BattutaSim.writeHex(progHex, Seq(0L -> words), 4) // 0x0800_0000 is the first line of the program ROM
      val actual = run(simModel, s"build/sim-memory-equivalence/$name-sim/sim/")

      println(s"$name: ${expected.zipWithIndex.map { case ((i, c), k) => s"core$k instr=$i cycles=$c" }.mkString(", ")}")
      expected should not be null
      expected.foreach { case (instr, _) => instr should be > BigInt(0) }
      actual shouldBe expected
    }
  }
}
