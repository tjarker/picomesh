create_clock -name clk -period 8.0 [get_ports clock]


set_input_delay -max 6 -clock clk [get_ports {io_*}]
set_output_delay -max 6 -clock clk [get_ports {io_*}]


