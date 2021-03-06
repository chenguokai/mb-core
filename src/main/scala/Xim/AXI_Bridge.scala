package Xim

import chisel3._
import chisel3.util.HasBlackBoxInline


class AXI_Bridge(width: Int = 64) extends BlackBox with HasBlackBoxInline {
    val io = IO(Flipped(new AXI_interface {
        val clock = Output(Clock())
        val reset = Output(Reset())
        // CPU side
        
        // inst sram-like
        val inst_req = Output(UInt(1.W))
        val inst_wr = Output(UInt(1.W))
        val inst_size = Output(UInt(2.W))
        val inst_addr = Output(UInt(width.W))
        val inst_wdata = Output(UInt(width.W))
        val inst_rdata = Input(UInt(width.W))
        val inst_addr_ok = Input(UInt(1.W))
        val inst_data_ok = Input(UInt(1.W))
        // data sram-like
        val data_req = Output(UInt(1.W))
        val data_wr = Output(UInt(1.W))
        val data_size = Output(UInt(2.W))
        val data_addr = Output(UInt(width.W))
        val data_wdata = Output(UInt(width.W))
        val data_rdata = Input(UInt(width.W))
        val data_addr_ok = Input(UInt(1.W))
        val data_data_ok = Input(UInt(1.W))
        
        // AXI side is automatically included in AXI_interface
    }))
    setInline("AXI_Bridge.v",
        s"""
           module AXI_Bridge
           (
               input         clock,
               input         reset,
           
               //inst sram-like
               input         inst_req     ,
               input         inst_wr      ,
               input  [1 :0] inst_size    ,
               input  [63:0] inst_addr    ,
               input  [63:0] inst_wdata   ,
               output [63:0] inst_rdata   ,
               output        inst_addr_ok ,
               output        inst_data_ok ,
           
               //data sram-like
               input         data_req     ,
               input         data_wr      ,
               input  [1 :0] data_size    ,
               input  [63:0] data_addr    ,
               input  [63:0] data_wdata   ,
               output [63:0] data_rdata   ,
               output        data_addr_ok ,
               output        data_data_ok ,
           
               //axi
               //ar
               output [3 :0] arid         ,
               output [63:0] araddr       ,
               output [7 :0] arlen        ,
               output [2 :0] arsize       ,
               output [1 :0] arburst      ,
               output [1 :0] arlock        ,
               output [3 :0] arcache      ,
               output [2 :0] arprot       ,
               output        arvalid      ,
               input         arready      ,
               //r
               input  [3 :0] rid          ,
               input  [63:0] rdata        ,
               input  [1 :0] rresp        ,
               input         rlast        ,
               input         rvalid       ,
               output        rready       ,
               //aw
               output [3 :0] awid         ,
               output [63:0] awaddr       ,
               output [7 :0] awlen        ,
               output [2 :0] awsize       ,
               output [1 :0] awburst      ,
               output [1 :0] awlock       ,
               output [3 :0] awcache      ,
               output [2 :0] awprot       ,
               output        awvalid      ,
               input         awready      ,
               //w
               output [3 :0] wid          ,
               output [63:0] wdata        ,
               output [7 :0] wstrb        ,
               output        wlast        ,
               output        wvalid       ,
               input         wready       ,
               //b
               input  [3 :0] bid          ,
               input  [1 :0] bresp        ,
               input         bvalid       ,
               output        bready
           );
wire resetn = !reset;
//addr
reg do_req;
reg do_req_or; //req is inst or data;1:data,0:inst
reg        do_wr_r;
reg [1 :0] do_size_r;
reg [63:0] do_addr_r;
reg [63:0] do_wdata_r;
wire data_back;

assign inst_addr_ok = !do_req&&!data_req;
assign data_addr_ok = !do_req;
always @(posedge clock)
begin
    do_req     <= !resetn                       ? 1'b0 :
                  (inst_req||data_req)&&!do_req ? 1'b1 :
                  data_back                     ? 1'b0 : do_req;
    do_req_or  <= !resetn ? 1'b0 :
                  !do_req ? data_req : do_req_or;

    do_wr_r    <= data_req&&data_addr_ok ? data_wr :
                  inst_req&&inst_addr_ok ? inst_wr : do_wr_r;
    do_size_r  <= data_req&&data_addr_ok ? data_size :
                  inst_req&&inst_addr_ok ? inst_size : do_size_r;
    do_addr_r  <= data_req&&data_addr_ok ? data_addr :
                  inst_req&&inst_addr_ok ? inst_addr : do_addr_r;
    do_wdata_r <= data_req&&data_addr_ok ? data_wdata :
                  inst_req&&inst_addr_ok ? inst_wdata :do_wdata_r;
end

//inst sram-like
assign inst_data_ok = do_req&&!do_req_or&&data_back;
assign data_data_ok = do_req&& do_req_or&&data_back;
assign inst_rdata   = rdata;
assign data_rdata   = rdata;

//---axi
reg addr_rcv;
reg wdata_rcv;

assign data_back = addr_rcv && (rvalid&&rready||bvalid&&bready);
always @(posedge clock)
begin
    addr_rcv  <= !resetn          ? 1'b0 :
                 arvalid&&arready ? 1'b1 :
                 awvalid&&awready ? 1'b1 :
                 data_back        ? 1'b0 : addr_rcv;
    wdata_rcv <= !resetn        ? 1'b0 :
                 wvalid&&wready ? 1'b1 :
                 data_back      ? 1'b0 : wdata_rcv;
end
//ar
assign arid    = 4'd0;
assign araddr  = do_addr_r;
assign arlen   = 8'd0;
assign arsize  = do_size_r;
assign arburst = 2'd0;
assign arlock  = 2'd0;
assign arcache = 4'd0;
assign arprot  = 3'd0;
assign arvalid = do_req&&!do_wr_r&&!addr_rcv;
//r
assign rready  = 1'b1;

//aw
assign awid    = 4'd0;
assign awaddr  = do_addr_r;
assign awlen   = 8'd0;
assign awsize  = do_size_r;
assign awburst = 2'd0;
assign awlock  = 2'd0;
assign awcache = 4'd0;
assign awprot  = 3'd0;
assign awvalid = do_req&&do_wr_r&&!addr_rcv;
//w
assign wid    = 4'd0;
assign wdata  = do_wdata_r;
assign wstrb  = do_size_r==2'd0 ? 8'b00000001<<do_addr_r[2:0] :
                do_size_r==2'd1 ? 8'b00000011<<do_addr_r[2:0] :
                do_size_r==2'd2 ? 8'b00001111<<do_addr_r[2:0] : 8'b11111111;
assign wlast  = 1'd1;
assign wvalid = do_req&&do_wr_r&&!wdata_rcv;
//b
assign bready  = 1'b1;

endmodule


           
    """.stripMargin
    )
    /*
The above verilog copyright info:
------------------------------------------------------------------------------
--------------------------------------------------------------------------------
Copyright (c) 2016, Loongson Technology Corporation Limited.

All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation and/or
other materials provided with the distribution.

3. Neither the name of Loongson Technology Corporation Limited nor the names of
its contributors may be used to endorse or promote products derived from this
software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL LOONGSON TECHNOLOGY CORPORATION LIMITED BE LIABLE
TO ANY PARTY FOR DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
--------------------------------------------------------------------------------
------------------------------------------------------------------------------
     */
}
