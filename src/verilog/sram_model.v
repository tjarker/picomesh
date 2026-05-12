module sky130_sram_1kbyte_1rw1r_32x256_8 (
`ifdef USE_POWER_PINS
    vccd1,
    vssd1,
`endif

    // Port 0: RW
    clk0,
    csb0,
    web0,
    wmask0,
    addr0,
    din0,
    dout0,

    // Port 1: R
    clk1,
    csb1,
    addr1,
    dout1
);

  parameter NUM_WMASKS = 4;
  parameter DATA_WIDTH = 32;
  parameter ADDR_WIDTH = 8;
  parameter RAM_DEPTH  = 1 << ADDR_WIDTH;
  parameter VERBOSE    = 0;

`ifdef USE_POWER_PINS
  inout vccd1;
  inout vssd1;
`endif

  // Port 0
  input                         clk0;
  input                         csb0;
  input                         web0;
  input  [NUM_WMASKS-1:0]       wmask0;
  input  [ADDR_WIDTH-1:0]       addr0;
  input  [DATA_WIDTH-1:0]       din0;
  output reg [DATA_WIDTH-1:0]   dout0;

  // Port 1
  input                         clk1;
  input                         csb1;
  input  [ADDR_WIDTH-1:0]       addr1;
  output reg [DATA_WIDTH-1:0]   dout1;

  // Memory array
  reg [DATA_WIDTH-1:0] mem [0:RAM_DEPTH-1];

  integer i;

  // ============================================================
  // Port 0: synchronous read/write on rising edge
  // ============================================================

  always @(posedge clk0) begin
    if (!csb0) begin

      // Write
      if (!web0) begin

        if (wmask0[0])
          mem[addr0][7:0]   <= din0[7:0];

        if (wmask0[1])
          mem[addr0][15:8]  <= din0[15:8];

        if (wmask0[2])
          mem[addr0][23:16] <= din0[23:16];

        if (wmask0[3])
          mem[addr0][31:24] <= din0[31:24];

        if (VERBOSE)
          $display("%0t WRITE addr=%0h data=%0h wmask=%0b",
                   $time, addr0, din0, wmask0);

      end

      // Read
      else begin
        dout0 <= mem[addr0];

        if (VERBOSE)
          $display("%0t READ0 addr=%0h data=%0h",
                   $time, addr0, mem[addr0]);
      end
    end
  end

  // ============================================================
  // Port 1: synchronous read on rising edge
  // ============================================================

  always @(posedge clk1) begin
    if (!csb1) begin

      dout1 <= mem[addr1];

      if (VERBOSE)
        $display("%0t READ1 addr=%0h data=%0h",
                 $time, addr1, mem[addr1]);
    end
  end

endmodule