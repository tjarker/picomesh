

import chisel3._
import chisel3.util._


object DecoupledLib {

  class DecoupledMux[T <: Data](gen: T) extends Module {
    val io = IO(new Bundle {
      val sel = Input(Bool())
      val left = Flipped(Decoupled(gen))
      val right = Flipped(Decoupled(gen))
      val out = Decoupled(gen)
    })

    io.out.valid := Mux(io.sel, io.right.valid, io.left.valid)
    io.out.bits := Mux(io.sel, io.right.bits, io.left.bits)
    io.left.ready := Mux(io.sel, 0.B, io.out.ready)
    io.right.ready := Mux(io.sel, io.out.ready, 0.B)
  }

  object DecoupledMux {
    def apply[T <: Data](sel: Bool, left: DecoupledIO[T], right: DecoupledIO[T]): DecoupledIO[T] = {
      val mux = Module(new DecoupledMux(left.bits.cloneType))
      mux.io.sel := sel
      mux.io.left <> left
      mux.io.right <> right
      mux.io.out
    }
  }

  class DecoupledSplit[T <: Data](gen: T) extends Module {
    val io = IO(new Bundle {
      val goLeft = Input(Bool())
      val in = Flipped(Decoupled(gen))
      val left = Decoupled(gen)
      val right = Decoupled(gen)
    })

    io.in.ready := Mux(io.goLeft, io.left.ready, io.right.ready)
    io.left.valid := io.in.valid && io.goLeft
    io.left.bits := io.in.bits
    io.right.valid := io.in.valid && !io.goLeft
    io.right.bits := io.in.bits
  }

  object DecoupledSplit {
    def apply[T <: Data](goLeft: Bool, in: DecoupledIO[T]): (DecoupledIO[T], DecoupledIO[T]) = {
      val split = Module(new DecoupledSplit(in.bits.cloneType))
      split.io.goLeft := goLeft
      split.io.in <> in
      (split.io.left, split.io.right)
    }
  }


}



