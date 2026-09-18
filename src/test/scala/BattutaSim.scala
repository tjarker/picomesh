import chisel3._
import liftoff._
import liftoff.simulation.verilator.Verilator

import java.io.{File, PrintWriter}

object BattutaSim {

  val verilatorArguments = Seq(
    Verilator.Arguments.CustomFlag("--Wno-TIMESCALEMOD"),
    Verilator.Arguments.NoTiming,
    Verilator.Arguments.CustomFlag("--Wno-STMTDLY"),
  )

  def filterReporting(): Unit = {
    Reporting.addProviderFilter("SimController.Queue")
    Reporting.addProviderFilter("Task")
    Reporting.addProviderFilter("SimController")
    Reporting.addProviderFilter("SimController.Loop")
    Reporting.addProviderFilter("ClockTick")
    Reporting.addProviderFilter("Channel")
  }

  /** Access to a WideBattutaArray through its ponte port. Core k is node k + 3. */
  class Ports(dut: WideBattutaArray) {

    def read(addr: BigInt): BigInt = {
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

    def coreBase(core: Int): BigInt = BigInt(core + 3) << 28

    def scratchpad(core: Int, i: Int): BigInt = read(coreBase(core) | 0x01000000 | (i * 4))

    /** Programs finish by writing 1 to their config register, which holds the core in reset. */
    def inReset(core: Int): Boolean = read(coreBase(core) | 0x01000014) == 1
  }

  /** Writes a $readmemh file. Each segment is a run of words starting at an entry index; an entry
    * holds `wordsPerEntry` words with the first in the low bits, like the wide ROM's 4-word lines.
    */
  def writeHex(file: File, segments: Seq[(Long, Seq[BigInt])], wordsPerEntry: Int): Unit = {
    file.getParentFile.mkdirs()
    val out = new PrintWriter(file)
    try {
      segments.foreach { case (index, words) =>
        out.println(f"@$index%x")
        words.grouped(wordsPerEntry).foreach { entry =>
          val value = entry.zipWithIndex.map { case (w, i) => w << (32 * i) }.reduce(_ | _)
          out.println(value.toString(16).reverse.padTo(8 * wordsPerEntry, '0').reverse)
        }
      }
    } finally out.close()
  }
}
