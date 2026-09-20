// Behavioural SRAM for simulation: the port behaviour of the OpenRAM macro
// (inputs sampled on the rising edge, dout follows reads only, per-byte write
// mask), with a configurable depth and an optional $readmemh image. Chisel
// memories cannot be preloaded when they carry a write mask, hence this model.

module sim_sram #(
    parameter ADDR_WIDTH = 10,
    parameter INIT_FILE  = ""
) (
    input                   clk,
    input  [ADDR_WIDTH-1:0] addr,
    input  [31:0]           din,
    input  [3:0]            wmask,
    input                   write,
    output reg [31:0]       dout
);

  reg [31:0] mem [0:(1 << ADDR_WIDTH) - 1];

  initial begin
    if (INIT_FILE != "") begin
      $readmemh(INIT_FILE, mem);
    end
  end

  always @(posedge clk) begin
    if (write) begin
      if (wmask[0]) mem[addr][7:0]   <= din[7:0];
      if (wmask[1]) mem[addr][15:8]  <= din[15:8];
      if (wmask[2]) mem[addr][23:16] <= din[23:16];
      if (wmask[3]) mem[addr][31:24] <= din[31:24];
    end else begin
      dout <= mem[addr];
    end
  end

endmodule
