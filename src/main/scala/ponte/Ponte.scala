package ponte

import chisel3._
import chisel3.util._

import uart.{Rx, Tx}
import uart.UartPins

class PonteAccessPort extends Bundle {
  val valid = Output(Bool())
  val addr = Output(UInt(32.W))
  val wdata = Output(UInt(32.W))
  val write = Output(Bool())
  val done = Input(Bool())
  val rdata = Input(UInt(32.W))
}

object Ponte {
  val START_WR = 0xaa
  val START_RD = 0xab
  val ESC = 0x5a
  val ESC_MASK = 0x20

  object Baud {
    val Baud115200 = 115200
    val Baud9600 = 9600
    val Baud921600 = 921600
  }
}

/** `Ponte` is an UART-to-APB bridge that allows us to communicate with the APB
  * network of the DTU Subsystem using a UART interface. The targeted APB bus
  * has 16-bit addresses and 32-bit data words.
  *
  * @param frequency
  *   The frequency of the system clock
  * @param baudRate
  *   The baud rate of the UART interface
  */
class Ponte(frequency: Int, baudRate: Int) extends Module {

  val io = IO(new Bundle {
    val uart = new UartPins
    val port = new PonteAccessPort
    val state = Output(UInt(7.W))
  })

  val uartRx = Module(new Rx(frequency, baudRate))
  val uartTx = Module(new Tx(frequency, baudRate))

  io.uart.tx := uartTx.io.txd
  uartRx.io.rxd := io.uart.rx

  val ponteDecoder = Module(new PonteDecoder)
  ponteDecoder.io.rx <> uartRx.io.channel
  ponteDecoder.io.port <> io.port
  ponteDecoder.io.tx <> uartTx.io.channel
  io.state := ponteDecoder.io.state
}
