

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import liftoff._
import liftoff.simulation.verilator.Verilator

class LiftoffBench extends AnyFlatSpec with Matchers {
  import liftoff._
  "PicoMesh" should "boot" in {
    val model = ChiselModel(
      new PicoMeshBig(PicoRvConfig.small), 
      "build/liftoff-bench/".toDir,
      Seq(),//"src/verilog/picorv32.v".toFile),
      Seq(
        Verilator.Arguments.CustomFlag("--Wno-TIMESCALEMOD"), 
        Verilator.Arguments.NoTiming,
        Verilator.Arguments.CustomFlag("--Wno-STMTDLY"),
      ),
      Seq()
      )
    model.simulate("build/liftoff-bench/sim/".toDir) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step(1)
      dut.reset.poke(false.B)
      dut.clock.step(20000)
    }
  }
}
