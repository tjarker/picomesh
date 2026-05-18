

// import chisel3._
// import org.scalatest.flatspec.AnyFlatSpec
// import org.scalatest.matchers.should.Matchers
// import liftoff._
// import liftoff.simulation.verilator.Verilator

// class LiftoffBench extends AnyFlatSpec with Matchers {
//   import liftoff._
//   "PicoMesh" should "boot" in {
//     val model = ChiselModel(
//       new PicoMeshBig(PicoRvConfig.small), 
//       "build/liftoff-bench/".toDir,
//       Seq(),//"src/verilog/picorv32.v".toFile),
//       Seq(
//         Verilator.Arguments.CustomFlag("--Wno-TIMESCALEMOD"), 
//         Verilator.Arguments.NoTiming,
//         Verilator.Arguments.CustomFlag("--Wno-STMTDLY"),
//       ),
//       Seq()
//       )

//     def read(dut: PicoMeshBig, addr: BigInt): BigInt = {
//       dut.io.pontePort.valid.poke(1.B)
//       dut.io.pontePort.addr.poke(addr.U)
//       dut.io.pontePort.write.poke(0.B)
//       dut.clock.step(1)
//       dut.clock.stepUntil(dut.io.pontePort.done, 1.B)
//       dut.io.pontePort.valid.poke(0.B)
//       dut.io.pontePort.rdata.peek().litValue
//     }
//     model.simulate("build/liftoff-bench/sim/".toDir) { dut =>
//       dut.reset.poke(true.B)
//       dut.clock.step(1)
//       dut.reset.poke(false.B)

//       var steps = 0

//       while(read(dut, 0x31000014) == 0 && steps < 1000000) {
//         dut.clock.step(500)
//         steps += 500
//       }

//       for (i <- 0 until 6) {
//         read(dut, 0x1FFFFC00 + i * 4) shouldBe (0x03330000L + i)
//       }
//       read(dut, 0x1FFFFC00 + 6 * 4) shouldBe 0x10
//     }
//   }
// }
