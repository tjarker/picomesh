
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers



class PicoNocBench extends AnyFlatSpec with ChiselScalatestTester {
  "PicoRv" should "cycle when reset is deasserted" in {
    test(new PicoMeshBig(PicoRvConfig.small))
    .withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { c =>
      c.clock.step(5000)
    }
  }
}


class PicoRvBench extends AnyFlatSpec with ChiselScalatestTester {
  "PicoRv" should "cycle when reset is deasserted" in {
    test(new PicoRv(PicoRvConfig.small.copy(
      progAddrReset = 0x2000_0000,
    )))
    .withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { c =>
      c.io.wb.ack.poke(1.B)

      c.io.remoteWb.cyc.poke(1.B)
      c.io.remoteWb.we.poke(1.B)
      c.io.remoteWb.adr.poke(0x2100_0010L.U)
      c.io.remoteWb.wdata.poke(0xDEADBEEFL.U)
      c.io.remoteWb.sel.poke(0xF.U)
      c.clock.step(1)
      c.io.remoteWb.cyc.poke(0.B)
      c.clock.step(500)
    }
  }
}


