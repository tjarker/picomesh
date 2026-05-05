
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec



class PicoNocBench extends AnyFlatSpec with ChiselScalatestTester {
  "PicoRv" should "cycle when reset is deasserted" in {
    test(new PicoMesh(PicoRvConfig.small.copy(
      progAddrReset = 0x20000000,
      stackAddr = 0x30000100
    )))
    .withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { c =>
      c.clock.step(500)
    }
  }
}


class PicoRvBench extends AnyFlatSpec with ChiselScalatestTester {
  "PicoRv" should "cycle when reset is deasserted" in {
    test(new PicoRv(PicoRvConfig.small.copy(
      progAddrReset = 0x2000_0000,
    ), 2))
    .withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { c =>
      c.io.wb.ack.poke(1.B)

      c.io.remoteWb.cyc.poke(1.B)
      c.clock.step(10)
      c.io.remoteWb.cyc.poke(0.B)
      c.clock.step(500)
    }
  }
}