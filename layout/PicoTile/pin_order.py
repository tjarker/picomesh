
def bus(name, width, msbfirst=True):
    if msbfirst:
         for i in range(width-1, -1, -1): print(f"{name}\\[{i}\\]") 
    else:
        for i in range(width): print(f"{name}\\[{i}\\]")

def interleaved_bus(names, range):
    for i in range:
        for name in names:
            print(f"{name}\\[{i}\\]")

def signal(name):
    print(name)

def bus_major():
    print("@bus_major")

def edge(dir):
    print(f"\n#{dir}")

def placeholder_pins(n):
    print(f"${n}")

bus_major()
edge("N")
interleaved_bus(["io_req_0_out_data_data", "io_req_0_in_data_data"], range(32))
signal("io_req_0_out_data_write")
signal("io_req_0_in_data_write")
signal("io_req_0_out_valid")
signal("io_req_0_in_valid")
interleaved_bus(["io_req_0_out_data_addr", "io_req_0_in_data_addr"], range(28))

signal("clock")
signal("reset")

signal("io_resp_0_out_valid")
signal("io_resp_0_in_valid")
interleaved_bus(["io_resp_0_out_data_data", "io_resp_0_in_data_data"], range(128))

edge("E")
interleaved_bus(["io_req_1_out_data_data", "io_req_1_in_data_data"], range(32))
signal("io_req_1_out_data_write")
signal("io_req_1_in_data_write")
signal("io_req_1_out_valid")
signal("io_req_1_in_valid")
interleaved_bus(["io_req_1_out_data_addr", "io_req_1_in_data_addr"], range(28))

signal("io_resp_1_out_valid")
signal("io_resp_1_in_valid")
interleaved_bus(["io_resp_1_out_data_data", "io_resp_1_in_data_data"], range(128))


edge("S")
interleaved_bus(["io_req_2_in_data_data", "io_req_2_out_data_data"], range(32))
signal("io_req_2_in_data_write")
signal("io_req_2_out_data_write")
signal("io_req_2_in_valid")
signal("io_req_2_out_valid")
interleaved_bus(["io_req_2_in_data_addr", "io_req_2_out_data_addr"], range(28))

placeholder_pins(2)

signal("io_resp_2_in_valid")
signal("io_resp_2_out_valid")
interleaved_bus(["io_resp_2_in_data_data", "io_resp_2_out_data_data"], range(128))

edge("W")
interleaved_bus(["io_req_3_in_data_data", "io_req_3_out_data_data"], range(32))
signal("io_req_3_in_data_write")
signal("io_req_3_out_data_write")
signal("io_req_3_in_valid")
signal("io_req_3_out_valid")
interleaved_bus(["io_req_3_in_data_addr", "io_req_3_out_data_addr"], range(28))

signal("io_resp_3_in_valid")
signal("io_resp_3_out_valid")
interleaved_bus(["io_resp_3_in_data_data", "io_resp_3_out_data_data"], range(128))