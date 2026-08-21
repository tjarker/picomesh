

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import liftoff._
import liftoff.simulation.verilator.Verilator
import liftoff.misc.Reporting.NullStream

class LiftoffBench extends AnyFlatSpec with Matchers {
  import liftoff._

  

  "PicoMesh" should "boot" in {
    val model = ChiselModel(
      new BattutaArray(PicoRvConfig.small, "build/bootloader/bootloader.bin", "build/rom/rom.bin"), 
      "build/liftoff-bench/".toDir,
      Seq(),//"src/verilog/picorv32.v".toFile),
      Seq(
        Verilator.Arguments.CustomFlag("--Wno-TIMESCALEMOD"), 
        Verilator.Arguments.NoTiming,
        Verilator.Arguments.CustomFlag("--Wno-STMTDLY"),
      ),
      Seq()
      )
    def read(dut: BattutaArray, addr: BigInt): BigInt = {
      dut.io.pontePort.valid.poke(1.B)
      dut.io.pontePort.addr.poke(addr.U)
      dut.io.pontePort.write.poke(0.B)
      dut.clock.step(1)
      dut.clock.stepUntil(dut.io.pontePort.done, 1.B)
      dut.io.pontePort.valid.poke(0.B)
      dut.io.pontePort.rdata.peek().litValue
    }
    Reporting.addProviderFilter("SimController.Queue")
    Reporting.addProviderFilter("Task")
    Reporting.addProviderFilter("SimController")
    Reporting.addProviderFilter("SimController.Loop")
    Reporting.addProviderFilter("ClockTick")
    Reporting.addProviderFilter("Channel")
    model.simulate("build/liftoff-bench/sim/".toDir) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step(1)
      dut.reset.poke(false.B)

      var steps = 0

      while(read(dut, 0x31000014) == 0 && steps < 10000) {
        dut.clock.step(500)
        steps += 500
      }

      for (i <- 0 until 6) {
        read(dut, 0x1FFFFC00 + i * 4) shouldBe (0x03330000L + i)
      }
      read(dut, 0x1FFFFC00 + 6 * 4) shouldBe 0x10
    }
  }


}


class BattutaBarrier extends AnyFlatSpec with Matchers {


  "Battuta" should "work with barriers" in {
    val model = ChiselModel(
      new Battuta("build/bootloader/bootloader.bin", "build/barrier_demo/barrier_demo.bin"), 
      "build/liftoff-bench-barrier/".toDir,
      Seq(),//"src/verilog/picorv32.v".toFile),
      Seq(
        Verilator.Arguments.CustomFlag("--Wno-TIMESCALEMOD"), 
        Verilator.Arguments.NoTiming,
        Verilator.Arguments.CustomFlag("--Wno-STMTDLY"),
        Verilator.Arguments.CustomFlag("--trace-saif"),
      ),
      Seq()
      )

    Reporting.addProviderFilter("SimController.Queue")
    Reporting.addProviderFilter("Task")
    Reporting.addProviderFilter("SimController")
    Reporting.addProviderFilter("SimController.Loop")
    Reporting.addProviderFilter("ClockTick")
    Reporting.addProviderFilter("Channel")
    model.simulate("build/liftoff-bench-barrier/sim/".toDir) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step(1)
      dut.reset.poke(false.B)
      dut.clock.step(5000)  
    }
  }
}
