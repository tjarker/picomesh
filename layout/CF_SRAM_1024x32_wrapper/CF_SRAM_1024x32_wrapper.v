// SPDX-FileCopyrightText: 2025 Umbralogic Technologies LLC d/b/a ChipFoundry and its Licensors, All Rights Reserved
// ========================================================================================
//
// This software is proprietary and protected by copyright and other intellectual property
// rights. Any reproduction, modification, translation, compilation, or representation
// beyond expressly permitted use is strictly prohibited.
//
// Access and use of this software are granted solely for integration into semiconductor
// chip designs created by you as part of ChipFoundry shuttles or ChipFoundry managed
// production programs. It is exclusively for Umbralogic Technologies LLC d/b/a ChipFoundry production purposes, and you may
// not modify or convey the software for any other purpose.
//
// DISCLAIMER: UMBRALOGIC TECHNOLOGIES LLC D/B/A CHIPFOUNDRY AND ITS LICENSORS PROVIDE THIS MATERIAL "AS IS," WITHOUT
// WARRANTIES OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
// Umbralogic Technologies LLC d/b/a ChipFoundry reserves the right to make changes without notice. Neither Umbralogic Technologies LLC d/b/a ChipFoundry nor its
// licensors assume any liability arising from the application or use of any product or
// circuit described herein. Umbralogic Technologies LLC d/b/a ChipFoundry products are not authorized for use as components
// in life-support devices.
//
// This license is subject to the terms of any separate agreement you have with Umbralogic Technologies LLC d/b/a ChipFoundry
// concerning the use of this software, which shall control in case of conflict.


`ifdef USE_POWER_PINS
    `define USE_PG_PIN
`endif

// CF_SRAM_1024x32_wb_wrapper.v
// This module instantiates a 1024x32 SRAM macro with a small controller,

module CF_SRAM_1024x32_wrapper #(parameter WIDTH = 12) (
`ifdef USE_POWER_PINS
    inout VPWR,
    inout VGND,
`endif
    input clk_i,
    input rst_i,
    input [WIDTH-3:0] addr_i,
    input we_i,
    input [3:0] sel_i,
    input [31:0] wr_data_i,
    output [31:0] rd_data_o
);

    // These signals form the SRAM-specific interface.
    wire [31:0] sram_do;    // Data output from SRAM to controller
    wire [31:0] sram_di;    // Data input from controller to SRAM
    wire [31:0] sram_ben;   // Byte enable from controller to SRAM (32-bit, derived from wbs_sel_i)
    wire [WIDTH-3:0] sram_ad;  // Address from controller to SRAM (word-aligned address), now using WIDTH
    wire sram_en;          // Chip enable from controller to SRAM (active high)
    wire sram_r_wb;        // Read/Write bar from controller to SRAM (1=Read, 0=Write)
    wire sram_clk_in;      // Clock signal for SRAM, directly from Wishbone clock

    // New wires for the CF_SRAM_1024x32 module's additional pins
    wire sram_scan_out_cc;  // Scan chain output

    assign sram_clk_in = clk_i;
    assign sram_en = 1'b1; // Always enabled
    assign sram_r_wb = !we_i;
    assign sram_ad = addr_i;
    assign sram_di = wr_data_i;
    assign sram_ben = {
        {8{sel_i[3]}}, // Replicate sel_i[3] 8 times for BEN[31:24]
        {8{sel_i[2]}}, // Replicate sel_i[2] 8 times for BEN[23:16]
        {8{sel_i[1]}}, // Replicate sel_i[1] 8 times for BEN[15:8]
        {8{sel_i[0]}}  // Replicate sel_i[0] 8 times for BEN[7:0]
    };
    assign rd_data_o = sram_do;
    

    // Instantiate the CF_SRAM_1024x32 macro
    // This is where your actual SRAM IP or memory block would be placed.
    // The dummy module definition has been removed as per your request.
    CF_SRAM_1024x32 i_sram (
        .DO         (sram_do),
        .AD         (sram_ad),
        .BEN        (sram_ben),
        .CLKin      (sram_clk_in),
        .DI         (sram_di),
        .EN         (sram_en),
        .R_WB       (sram_r_wb),
        // Connect new pins based on the provided list
        .ScanOutCC  (sram_scan_out_cc), // Output from SRAM
        .ScanInCC   (1'b0),             // Tie to 0 for unused scan input (example)
        .ScanInDL   (1'b0),             // Tie to 0 for unused scan input (example)
        .ScanInDR   (1'b0),             // Tie to 0 for unused scan input (example)
        .SM         (1'b0),             // Tie to 0 (example)
        .TM         (1'b0),             // Tie to 0 (example)
        .WLBI       (1'b0),             // Tie to 0 (example)
        .WLOFF      (1'b0),             // Tie to 0 (example)
    `ifdef USE_PG_PIN
        .vgnd       (1'b0),             // Tie to ground
        .vnb        (1'b0),             // Tie to ground (body bias)
        .vpb        (1'b1),             // Tie to VDD (body bias)
        .vpwra      (1'b1),             // Tie to VDD
    `endif
        .vpwrac     (1'b1),             // Tie to VDD
    `ifdef USE_PG_PIN
        .vpwrm      (1'b1),             // Tie to VDD
        .vpwrp      (1'b1),             // Tie to VDD
    `endif
        .vpwrpc     (1'b1)              // Tie to VDD
    );

endmodule

