import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import liftoff._
import liftoff.simulation.ModelSource
import liftoff.simulation.verilator.Verilator

import s4noc.{S4Router, Schedule}

/** Drives identical stimulus through the RTL and through the post-P&R netlist
  * of the same module, and requires the output ports to agree cycle by cycle.
  *
  * This is the acceptance test for netlist simulation: the Chisel handle is the
  * same object in both runs, only the Verilog behind it differs.
  */
class NetlistEquivalence extends AnyFlatSpec with Matchers {

  val pdk = "../dependencies/pdks/sky130A/libs.ref/sky130_fd_sc_hd/verilog"

  // Synthesised from the current RTL by `make synth-s4router`. The netlists
  // sitting in layout/*/runs are older than generated/, so comparing against
  // one of those tests whether the design changed, not whether the simulator
  // works.
  val netlist = "build/synth/S4Router_synth.v"

  def dut = new S4Router(Schedule(3).schedule, new MemoryRequest)

  /** Same stimulus for both runs: walk a distinct pattern into every input port
    * and sample all five output channels each cycle.
    */
  def stimulus(d: S4Router[MemoryRequest]): Seq[Seq[BigInt]] = {
    d.reset.poke(true.B)
    d.clock.step(4)
    d.reset.poke(false.B)

    (0 until 60).map { cycle =>
      for (p <- 0 until 5) {
        d.io.ports(p).in.valid.poke((((cycle >> p) & 1) == 1).B)
        d.io.ports(p).in.data.addr.poke((0x1000000 * p + cycle).U)
        d.io.ports(p).in.data.data.poke((0xdead0000L + cycle * 7 + p).U)
        d.io.ports(p).in.data.write.poke(((cycle + p) % 3 == 0).B)
      }
      d.clock.step(1)

      (0 until 5).flatMap { p =>
        Seq(
          d.io.ports(p).out.valid.peek().litValue,
          d.io.ports(p).out.data.addr.peek().litValue,
          d.io.ports(p).out.data.data.peek().litValue,
          d.io.ports(p).out.data.write.peek().litValue
        )
      }
    }
  }

  "S4Router" should "produce identical outputs from RTL and netlist" in {

    val rtlModel = ChiselModel(
      dut,
      "build/nl-equiv-rtl/".toDir,
      ModelSource.Rtl,
      Seq(),
      Seq(Verilator.Arguments.NoTiming),
      Seq()
    )
    val rtlTrace = rtlModel.simulate("build/nl-equiv-rtl/sim/".toDir)(stimulus).result

    val netlistModel = ChiselModel(
      dut,
      "build/nl-equiv-gl/".toDir,
      ModelSource.Netlist(
        Seq(
          netlist.toFile,
          s"$pdk/primitives.v".toFile,
          s"$pdk/sky130_fd_sc_hd.v".toFile
        )
      ),
      Seq(),
      Seq(),
      Seq()
    )
    val netlistTrace = netlistModel.simulate("build/nl-equiv-gl/sim/".toDir)(stimulus).result

    netlistTrace.length shouldBe rtlTrace.length

    val mismatches = rtlTrace.zip(netlistTrace).zipWithIndex.collect {
      case ((r, n), cycle) if r != n => s"cycle $cycle: rtl=$r netlist=$n"
    }

    withClue(s"${mismatches.length} mismatching cycles:\n${mismatches.take(5).mkString("\n")}\n") {
      mismatches shouldBe empty
    }
  }
}
