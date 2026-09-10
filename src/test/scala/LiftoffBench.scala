

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
      val res = dut.io.pontePort.rdata.peek().litValue
      dut.clock.step()
      res
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

      def getInstrAndCycles(core: Int): (Int, Int) = {
        val instr = read(dut, (core.toLong << 28) | 0x01000004).toInt
        val cycles = read(dut, (core.toLong << 28) | 0x01000008).toInt
        (instr, cycles)
      }

      for (i <- 0 until 6) {
        val (instr, cycles) = getInstrAndCycles(i + 3)
        println(s"Core ${i}: instr=$instr cycles=$cycles cpi=${cycles.toDouble / instr}")
        instr should be > 0
        cycles should be > 0
      }

    }
  }


}


class BattutaBarrier extends AnyFlatSpec with Matchers {

  val pdk = "../../../../.ciel/sky130A/libs.ref/sky130_fd_sc_hd/verilog"

  import liftoff.simulation.ModelSource


  "Battuta" should "work with barriers" in {
    val model = ChiselModel(
      new BattutaArray(PicoRvConfig.small, "build/bootloader/bootloader.bin", "build/barrier_demo/barrier_demo.bin"), 
      "build/liftoff-bench-barrier/".toDir,
      ModelSource.Rtl,
      // ModelSource.Netlist(
      //   Seq(
      //     "layout/Battuta/runs/harden/52-openroad-fillinsertion/Battuta.nl.v".toFile,
      //     "src/verilog/sram_model.v".toFile,
      //     s"$pdk/primitives.v".toFile,
      //     s"$pdk/sky130_fd_sc_hd.v".toFile
      //   )
      // ),
      Seq(),//"src/verilog/picorv32.v".toFile),
      Seq(
        Verilator.Arguments.CustomFlag("--Wno-TIMESCALEMOD"), 
        Verilator.Arguments.NoTiming,
        Verilator.Arguments.CustomFlag("--Wno-STMTDLY"),
        //Verilator.Arguments.CustomFlag("--trace-saif"),
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

      def read(dut: BattutaArray, addr: BigInt): BigInt = {
        dut.io.pontePort.valid.poke(1.B)
        dut.io.pontePort.addr.poke(addr.U)
        dut.io.pontePort.write.poke(0.B)
        dut.clock.step(1)
        dut.clock.stepUntil(dut.io.pontePort.done, 1.B)
        dut.io.pontePort.valid.poke(0.B)
        val res = dut.io.pontePort.rdata.peek().litValue
        dut.clock.step()
        res
      }

      def coreIsInReset(coreId: Int): Boolean = {
        read(dut, (coreId.toLong << 28) | 0x01000014) == 1
      }


      for (i <- 0 until 6) {
        while(!coreIsInReset(i + 3)) {
          dut.clock.step(1)
        }
      }

      def getInstrAndCycles(core: Int): (Int, Int) = {
        val instr = read(dut, (core.toLong << 28) | 0x01000004).toInt
        val cycles = read(dut, (core.toLong << 28) | 0x01000008).toInt
        (instr, cycles)
      }

      for (i <- 0 until 6) {
        val (instr, cycles) = getInstrAndCycles(i + 3)
        println(s"Core ${i}: instr=$instr cycles=$cycles cpi=${cycles.toDouble / instr}")
        instr should be > 0
        cycles should be > 0
      }


    }
  }
}



class LiftoffBenchWide extends AnyFlatSpec with Matchers {
  import liftoff._

  

  "PicoMesh" should "boot" in {
    val model = ChiselModel(
      new WideBattutaArray(PicoRvConfig.small, "build/bootloader/bootloader.bin", "build/rom/rom.bin"), 
      "build/liftoff-bench/".toDir,
      Seq(),//"src/verilog/picorv32.v".toFile),
      Seq(
        Verilator.Arguments.CustomFlag("--Wno-TIMESCALEMOD"), 
        Verilator.Arguments.NoTiming,
        Verilator.Arguments.CustomFlag("--Wno-STMTDLY"),
      ),
      Seq()
      )
    def read(dut: WideBattutaArray, addr: BigInt): BigInt = {
      dut.io.pontePort.valid.poke(1.B)
      dut.io.pontePort.addr.poke(addr.U)
      dut.io.pontePort.write.poke(0.B)
      dut.clock.step(1)
      dut.clock.stepUntil(dut.io.pontePort.done, 1.B)
      dut.io.pontePort.valid.poke(0.B)
      val res = dut.io.pontePort.rdata.peek().litValue
      dut.clock.step()
      res
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

      def getInstrAndCycles(core: Int): (Int, Int) = {
        val instr = read(dut, (core.toLong << 28) | 0x01000004).toInt
        val cycles = read(dut, (core.toLong << 28) | 0x01000008).toInt
        (instr, cycles)
      }

      for (i <- 0 until 6) {
        val (instr, cycles) = getInstrAndCycles(i + 3)
        println(s"Core ${i}: instr=$instr cycles=$cycles cpi=${cycles.toDouble / instr}")
        instr should be > 0
        cycles should be > 0
      }

    }
  }


}


class BattutaBarrierWide extends AnyFlatSpec with Matchers {

  val pdk = "../../../../.ciel/sky130A/libs.ref/sky130_fd_sc_hd/verilog"

  import liftoff.simulation.ModelSource


  "Battuta" should "work with barriers" in {
    val model = ChiselModel(
      new WideBattutaArray(PicoRvConfig.small, "build/bootloader/bootloader.bin", "build/barrier_demo/barrier_demo.bin"), 
      "build/liftoff-bench-barrier/".toDir,
      ModelSource.Rtl,
      // ModelSource.Netlist(
      //   Seq(
      //     "layout/Battuta/runs/harden/52-openroad-fillinsertion/Battuta.nl.v".toFile,
      //     "src/verilog/sram_model.v".toFile,
      //     s"$pdk/primitives.v".toFile,
      //     s"$pdk/sky130_fd_sc_hd.v".toFile
      //   )
      // ),
      Seq(),//"src/verilog/picorv32.v".toFile),
      Seq(
        Verilator.Arguments.CustomFlag("--Wno-TIMESCALEMOD"), 
        Verilator.Arguments.NoTiming,
        Verilator.Arguments.CustomFlag("--Wno-STMTDLY"),
        //Verilator.Arguments.CustomFlag("--trace-saif"),
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

      def read(dut: WideBattutaArray, addr: BigInt): BigInt = {
        dut.io.pontePort.valid.poke(1.B)
        dut.io.pontePort.addr.poke(addr.U)
        dut.io.pontePort.write.poke(0.B)
        dut.clock.step(1)
        dut.clock.stepUntil(dut.io.pontePort.done, 1.B)
        dut.io.pontePort.valid.poke(0.B)
        val res = dut.io.pontePort.rdata.peek().litValue
        dut.clock.step()
        res
      }

      def coreIsInReset(coreId: Int): Boolean = {
        read(dut, (coreId.toLong << 28) | 0x01000014) == 1
      }


      for (i <- 0 until 6) {
        while(!coreIsInReset(i + 3)) {
          dut.clock.step(1)
        }
      }

      def getInstrAndCycles(core: Int): (Int, Int) = {
        val instr = read(dut, (core.toLong << 28) | 0x01000004).toInt
        val cycles = read(dut, (core.toLong << 28) | 0x01000008).toInt
        (instr, cycles)
      }

      for (i <- 0 until 6) {
        val (instr, cycles) = getInstrAndCycles(i + 3)
        println(s"Core ${i}: instr=$instr cycles=$cycles cpi=${cycles.toDouble / instr}")
        instr should be > 0
        cycles should be > 0
      }


    }
  }
}
