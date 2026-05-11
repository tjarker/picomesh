package ponte

import chisel3._
import chisel3.util._

import uart.UartIO


/** The `PonteDecoder` implements the decoding of the Ponte protocol. It
  * receives bytes through a handshake interface, uses the `PonteEscaper` to
  * unescape the bytes and decodes the protocol in a state machine. The decoded
  * transactions are the executed on the APB bus.
  */
class PonteDecoder extends Module {

  val io = IO(new Bundle {
    val rx = Flipped(Decoupled(UInt(8.W)))
    val tx = Decoupled(UInt(8.W))
    val port = new PonteAccessPort
    val state = Output(UInt(7.W))
  })

  val dec = Module(new PonteEscaper)
  dec.io.in <> io.rx

  object State extends ChiselEnum {
    val Start =         Value//("b0000001".U)
    val ReadLen =       Value//("b0000010".U)
    val Address =       Value//("b0000100".U)
    val Data =          Value//("b0001000".U)
    val BusTx =         Value//("b0010000".U)
    val Resp =          Value//("b0100000".U)
    val UpdateReadLen = Value//("b1000000".U)
  }

  val stateReg = RegInit(State.Start)
  io.state := stateReg.asUInt
  
  val cntReg = RegInit(0.U(2.W))
  val addrReg = RegInit(0.U(32.W))
  val isWriteReg = RegInit(0.B)
  val readLenReg = RegInit(0.U(8.W))
  val dataReg = RegInit(0.U(32.W))

  io.port.valid := 0.B
  io.port.addr := addrReg
  io.port.wdata := dataReg
  io.port.write := isWriteReg

  io.tx.valid := 0.B
  io.tx.bits := dataReg(7, 0)

  dec.io.stall := 0.B


  val decrementCntReg = WireDefault(0.B)
  when(decrementCntReg) {
    cntReg := cntReg - 1.U
  }
  val cntRegIsZero = cntReg === 0.U

  switch(stateReg) {
    is(State.Start) { // wait for new transactions
      cntReg := 3.U // prepare for address receive
      isWriteReg := dec.io.startWrite

      when(dec.io.valid) {
        // new transaction is starting, determine if it's a read or write
        stateReg := Mux(dec.io.startRead, State.ReadLen, State.Address)
      }
    }
    is(State.ReadLen) { // get the length of a read transaction
      cntReg := 3.U // prepare for address receive
      readLenReg := dec.io.data
      when(dec.io.valid) {
        stateReg := State.Address
      }
    }
    is(State.Address) { // read the 2-byte address
      when(dec.io.valid) {
        decrementCntReg := 1.B
        addrReg := Cat(dec.io.data, addrReg(31, 8))
        when(cntRegIsZero) {
          stateReg := Mux(isWriteReg, State.Data, State.BusTx)
          cntReg := 3.U // prepare for data receive if it's a write
        }
      }
    }
    is(State.Data) { // read 4 bytes of write data
      when(dec.io.valid) {
        decrementCntReg := 1.B
        dataReg := Cat(dec.io.data, dataReg(31, 8))
        when(cntRegIsZero) {
          stateReg := State.BusTx
        }
      }
    }
    is(State.BusTx) {
      io.port.valid := 1.B
      dec.io.stall := 1.B // stall accepting new bytes
      cntReg := 3.U
      when(io.port.done) {
        addrReg := addrReg + 4.U
        dataReg := io.port.rdata
        stateReg := Mux(isWriteReg, State.Data, State.Resp)
      }
    }
    is(State.Resp) { // send 4 bytes of read data back
      dec.io.stall := 1.B
      io.tx.valid := 1.B
      when(io.tx.ready) {
        dataReg := dataReg(31, 8)
        decrementCntReg := 1.B
        when(cntRegIsZero) {
          stateReg := State.UpdateReadLen
        }
      }
    }
    is(State.UpdateReadLen) { // decrement read counter
      dec.io.stall := 1.B
      readLenReg := readLenReg - 1.U
      stateReg := Mux(readLenReg === 0.U, State.Start, State.BusTx)
    }

  }

  // when the FSM is not performing a APB transactions
  // a new Ponte transaction may be started, aborting
  // the current one
  when(dec.io.valid && !dec.io.stall) {
    when(dec.io.startRead) {
      isWriteReg := 0.B
      stateReg := State.ReadLen
    }.elsewhen(dec.io.startWrite) {
      isWriteReg := 1.B
      stateReg := State.Address
      cntReg := 3.U
    }
  }

}
