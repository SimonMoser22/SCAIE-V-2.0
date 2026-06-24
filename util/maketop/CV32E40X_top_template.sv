/*
  Part of Master's Thesis:
      "Dynamic partial re-configurable hardware management module for a RISC-V based Microcontroller"

  Author:      Florian Angermair
  Date:        June 2025
*/
import cv32e40x_pkg::*;

module top #(
    parameter ROM_FILE = "",
    parameter int SIM = 0
) (
    input  logic        clk_i,
    input  logic        rstn_i,
    // Buttons
    input  logic        btnc_i,
    input  logic        btnu_i,
    input  logic        btnl_i,
    input  logic        btnr_i,
    input  logic        btnd_i,
    // Switches
    input  logic [15:0] switches_i,
    // Leds
    output logic [15:0] leds_o,
    output logic [ 2:0] rgb_led_o,
    // Pmod JA, JB, JC
    output logic [ 7:0] pmod_ja_o,
    output logic [ 7:0] pmod_jb_o,
    output logic [ 7:0] pmod_jc_o,
    output logic [ 7:0] pmod_jd_o,
    // USB-rs232 interface
    input  logic        serial_rx_i,
    output logic        serial_tx_o
);

  // Button debouncer configuration
  // In a simulation, a smaller clock divider is used to save time.
  localparam int DEBOUNCE_CLK_DIV = SIM ? 1 : 50000;
  localparam int DEBOUNCE_DURATION_CNT = 40;

  logic        clk_100;
  logic        sys_clk;
  logic        drhm_clk;
  logic        async_rst;
  logic        sys_rst;
  logic        drhm_rst;
  logic        instr_req;
  logic        instr_gnt;
  logic        instr_rvalid;
  logic [31:0] instr_addr;
  logic [31:0] instr_rdata;
  logic        data_req;
  logic        data_gnt;
  logic        data_rvalid;
  logic        data_err;
  logic [31:0] data_addr;
  logic        data_we;
  logic [ 3:0] data_be;
  logic [31:0] data_rdata;
  logic [31:0] data_wdata;
  logic [31:0] irq;
  logic        irq_ack;
  logic [ 3:0] gpio_out;
  logic [31:0] gpio_in;
  logic        timer_irq;

  //SCAIEV MAKETOP COREWIRES

  //SCAIEV MAKETOP ISAXWIRES

  clk_gen clk_gen_inst (
      .clk_i(clk_i),
      .rst_i(1'b0),
      .clk_100_o(clk_100),
      .clk_50_o (sys_clk),
      .clk_25_o (drhm_clk),
      .locked   (locked)
  );

  assign async_rst = ~rstn_i && locked;

  rst_gen rst_gen_isnt (
      .arst_i (async_rst),
      .clk_a_i(sys_clk),
      .clk_b_i(drhm_clk),
      .rst_a_o(sys_rst),
      .rst_b_o(drhm_rst)
  );

  wb_if wbm[1] (
      .clk(sys_clk),
      .rst(sys_rst)
  );

  wb_if wbs[6] (
      .clk(sys_clk),
      .rst(sys_rst)
  );

  cv32e40x_if_xif #(
      .X_NUM_RS   (2),
      .X_MEM_WIDTH(32),
      .X_RFR_WIDTH(32),
      .X_RFW_WIDTH(32),
      .X_MISA     ('0)
  ) ext_if ();



  cv32e40x_core #(
      .LIB                 (0),
      .RV32                (RV32I),
      .A_EXT               (0),
      .B_EXT               (B_NONE),
      .M_EXT               (M),
      .DBG_NUM_TRIGGERS    (1),
      .PMA_NUM_REGIONS     (0),
      .CLIC                (0),
      .CLIC_ID_WIDTH       (5),
      .X_EXT               (0),
      .X_NUM_RS            (2),
      .X_ID_WIDTH          (4),
      .X_MEM_WIDTH         (32),
      .X_RFR_WIDTH         (32),
      .X_RFW_WIDTH         (32),
      .X_MISA              (32'h0),
      .X_ECS_XS            (2'b0),
      .NUM_MHPMCOUNTERS    (1)
  ) cpu_core (
      .clk_i              (sys_clk),
      .rst_ni             (~sys_rst),
      .scan_cg_en_i       (1'b0),
      .boot_addr_i        ('h200),
      .dm_exception_addr_i('h0),
      .dm_halt_addr_i     ('h200),
      .mhartid_i          ('h0),
      .mimpid_patch_i     ('h0),
      .mtvec_addr_i       ('h0),
      .instr_req_o        (instr_req),
      .instr_gnt_i        (instr_gnt),
      .instr_rvalid_i     (instr_rvalid),
      .instr_addr_o       (instr_addr),
      .instr_memtype_o    (),
      .instr_prot_o       (),
      .instr_dbg_o        (),
      .instr_rdata_i      (instr_rdata),
      .instr_err_i        (1'b0),
      .data_req_o         (data_req),
      .data_gnt_i         (data_gnt),
      .data_rvalid_i      (data_rvalid),
      .data_addr_o        (data_addr),
      .data_be_o          (data_be),
      .data_we_o          (data_we),
      .data_wdata_o       (data_wdata),
      .data_memtype_o     (),
      .data_prot_o        (),
      .data_dbg_o         (),
      .data_atop_o        (),
      .data_rdata_i       (data_rdata),
      .data_err_i         (data_err),
      .data_exokay_i      (1'b1),
      .mcycle_o           (),
      .xif_compressed_if  (ext_if),
      .xif_issue_if       (ext_if),
      .xif_commit_if      (ext_if),
      .xif_mem_if         (ext_if),
      .xif_mem_result_if  (ext_if),
      .xif_result_if      (ext_if),
      .irq_i              (irq),
      .wu_wfe_i           (1'b1),
      .clic_irq_i         ('h0),
      .clic_irq_id_i      ('h0),
      .clic_irq_level_i   ('h0),
      .clic_irq_priv_i    ('h0),
      .clic_irq_shv_i     ('h0),
      .fencei_flush_req_o (),
      .fencei_flush_ack_i (1'b0),
      .debug_req_i        (1'b0),
      .debug_havereset_o  (),
      .debug_running_o    (),
      .debug_halted_o     (),
      .fetch_enable_i     (1'b1),
      .core_sleep_o       ()

	  //SCAIEV MAKETOP COREPINS

  );

  mmio_controller #(
      .ROM_FILE(ROM_FILE)
  ) mmio_controller_i (
      .clk_i         (sys_clk),
      .rst_n         (~sys_rst),
      .data_addr_i   (data_addr),
      .data_wdata_i  (data_wdata),
      .data_we_i     (data_we),
      .data_req_i    (data_req),
      .data_be_i     (data_be),
      .data_rdata_o  (data_rdata),
      .data_gnt_o    (data_gnt),
      .data_rvalid_o (data_rvalid),
      .data_err_o    (data_err),
      .instr_addr_i  (instr_addr),
      .instr_req_i   (instr_req),
      .instr_rdata_o (instr_rdata),
      .instr_gnt_o   (instr_gnt),
      .instr_rvalid_o(instr_rvalid),
      .data_wb       (wbm[0])
  );

  wb_interconnect_sharedbus #(
      .numm     (1),
      .nums     (6),
      //             GPIO         Not Used     Not Used      SERIAL      Not Used      TIMER
      .base_addr('{'h1000_0000, 'h2000_0000, 'h3000_0000, 'h4000_0000, 'h5000_0000, 'h6000_0000}),
      .size     ('{'h0000_000F, 'h0000_000F, 'h0000_FFFF, 'h0000_000F, 'h00FF_FFFF, 'h0000_FFFF})
  ) wb_intercon (
      .wbm(wbm),
      .wbs(wbs)
  );

  assign gpio_in = {switches_i, 8'h00, 3'b0, btnc_i, btnl_i, btnd_i, btnr_i, btnu_i};
  wb_gpio #(
      .OUTPUT_WIDTH(4),
      .INPUT_WIDTH(32),
      .DEBOUNCE_CLK_DIV(DEBOUNCE_CLK_DIV),
      .DEBOUNCE_DURATION_CNT(DEBOUNCE_DURATION_CNT)
  ) wb_gpio_inst (
      .wbs  (wbs[0]),
      .out_o(gpio_out),
      .in_i (gpio_in)
  );

  assign wbs[1].ack   = 1'b0;
  assign wbs[1].stall = 1'b0;
  assign wbs[1].err   = '0;
  assign wbs[1].dat_s = '0;

  assign wbs[2].ack   = 1'b0;
  assign wbs[2].stall = 1'b0;
  assign wbs[2].err   = '0;
  assign wbs[2].dat_s = '0;

  wb_serial wb_serial_inst (
      .clk_i      (sys_clk),
      .rst_i      (sys_rst),
      .wbs_ack_o  (wbs[3].ack),
      .wbs_adr_i  (wbs[3].adr),
      .wbs_cyc_i  (wbs[3].cyc),
      .wbs_stall_o(wbs[3].stall),
      .wbs_stb_i  (wbs[3].stb),
      .wbs_we_i   (wbs[3].we),
      .wbs_sel_i  (wbs[3].sel),
      .wbs_err_o  (wbs[3].err),
      .wbs_dat_i  (wbs[3].dat_m),
      .wbs_dat_o  (wbs[3].dat_s),
      .tx_o       (serial_tx_o),
      .rx_i       (serial_rx_i)
  );

  assign wbs[4].ack   = 1'b0;
  assign wbs[4].stall = 1'b0;
  assign wbs[4].err   = '0;
  assign wbs[4].dat_s = '0;

  wb_timer wb_timer_inst (
      .wbs  (wbs[5]),
      .irq_o(timer_irq)
  );

  assign irq = {24'h000000, timer_irq, 7'h00};

  assign rgb_led_o = 3'b000;
  assign leds_o = {12'h000, gpio_out};
  assign pmod_ja_o = {4'h0, gpio_out};


  //SCAIEV MAKETOP SCAL
    
  //SCAIEV MAKETOP ISAXINST

endmodule
