import chisel3._
import chisel3.util._
import s4noc.Schedule
import s4noc.RouterIO
import s4noc.SingleChannelIO

class CustomS4Router[T <: Data](schedule: Array[Array[Int]], dt: T) extends Module {
  val io = IO(new RouterIO(dt))
  val slotCountPort = IO(Input(UInt(log2Ceil(schedule.length).W)))
  val localPortLookahead = IO(Output(new SingleChannelIO(dt)))


  // Just convert schedule table to a Chisel type table
  // unused slot is -1, convert to 0.U
  val sched = Wire(Vec(schedule.length, Vec(s4noc.Const.NR_OF_PORTS, UInt(3.W))))
  for (i <- 0 until schedule.length) {
    for (j <- 0 until s4noc.Const.NR_OF_PORTS) {
      val s = schedule(i)(j)
      val v = if (s == -1) s4noc.Const.INVALID else s
      sched(i)(j) := v.U(3.W)
    }
  }

  // TDM schedule starts one cycles later for read data delay
  //val regDelay = RegNext(regCounter, init = 0.U)
  val currentSched = sched(slotCountPort)
  // TODO: test if this movement of the register past the schedule table works, better here a register
  // val currentSched = RegNext(sched(regCounter))

  for (j <- 0 until s4noc.Const.NR_OF_PORTS) {
    val data = io.ports(currentSched(j)).in.data
    val valid = Mux(currentSched(j) === s4noc.Const.INVALID.U, false.B, io.ports(currentSched(j)).in.valid)
    io.ports(j).out.data := RegNext(data)
    io.ports(j).out.valid := RegNext(valid, init = false.B)

    if (j == s4noc.Const.LOCAL) {
      localPortLookahead.data := data
      localPortLookahead.valid := valid
    }
  }
}
