
all: comp verilog pico-mesh-reset pico-mesh-synth pico-mesh-layout

verilog:
	sbt "runMain PicoMeshBigTop"

#NIX_RUN=nix run github:chipfoundry/openlane-2/CI2511 -- 
#LIBRELANE_HARDEN=$(NIX_RUN) --run-tag harden --manual-pdk --pdk-root ${PWD}/../dependencies/pdks/

NIX_RUN=nix run github:librelane/librelane/3.0.10 --
LIBRELANE_HARDEN=$(NIX_RUN) --run-tag harden


harden-pico-node:
	@rm -rf layout/PicoNode/runs
	$(LIBRELANE_HARDEN) --save-views-to build/layout/PicoNode layout/PicoNode/config.yaml

openroad-pico-node:
	${NIX_RUN}--last-run --flow OpenInOpenROAD layout/PicoNode/config.yaml

klayout-pico-node:
	${NIX_RUN}--last-run --flow OpenInKLayout layout/PicoNode/config.yaml

pico-mesh-reset:
	@rm -rf layout/PicoMeshBigTop/runs

pico-mesh-synth:
	${LIBRELANE_HARDEN} --to "Checker.NetlistAssignStatements" layout/PicoMeshBigTop/config.yaml

pico-mesh-layout:
	${LIBRELANE_HARDEN} --from "OpenROAD.CheckSDCFiles" --save-views-to build/layout/PicoMeshBigTop layout/PicoMeshBigTop/config.yaml

openroad-pico-mesh:
	${NIX_RUN}--last-run --flow OpenInOpenROAD layout/PicoMeshBigTop/config.yaml

klayout-pico-mesh:
	${NIX_RUN}--last-run --flow OpenInKLayout layout/PicoMeshBigTop/config.yaml



battuta_monolothic:
	@rm -rf layout/Battuta/runs
	${LIBRELANE_HARDEN} --save-views-to build/layout/Battuta layout/Battuta/configMono.yaml

battuta_core_0:
	rm -rf layout/Battuta/tiles/PicoTile/runs
	${LIBRELANE_HARDEN} --save-views-to build/layout/Battuta/tiles/PicoTile layout/Battuta/tiles/PicoTile/config.yaml

battuta_core_1:
	@rm -rf layout/Battuta/tiles/PicoTile_1/runs
	${LIBRELANE_HARDEN} --save-views-to build/layout/Battuta/tiles/PicoTile_1 layout/Battuta/tiles/PicoTile_1/config.yaml

battuta_core_2:
	@rm -rf layout/Battuta/tiles/PicoTile_2/runs
	${LIBRELANE_HARDEN} --save-views-to build/layout/Battuta/tiles/PicoTile_2 layout/Battuta/tiles/PicoTile_2/config.yaml

battuta_core_3:
	@rm -rf layout/Battuta/tiles/PicoTile_3/runs
	${LIBRELANE_HARDEN} --save-views-to build/layout/Battuta/tiles/PicoTile_3 layout/Battuta/tiles/PicoTile_3/config.yaml

battuta_core_4:
	@rm -rf layout/Battuta/tiles/PicoTile_4/runs
	${LIBRELANE_HARDEN} --save-views-to build/layout/Battuta/tiles/PicoTile_4 layout/Battuta/tiles/PicoTile_4/config.yaml

battuta_core_5:
	@rm -rf layout/Battuta/tiles/PicoTile_5/runs
	${LIBRELANE_HARDEN} --save-views-to build/layout/Battuta/tiles/PicoTile_5 layout/Battuta/tiles/PicoTile_5/config.yaml

battuta_mem_0:
	@rm -rf layout/Battuta/tiles/MemoryTile/runs
	${LIBRELANE_HARDEN} --save-views-to build/layout/Battuta/tiles/MemoryTile layout/Battuta/tiles/MemoryTile/config.yaml



comp: comp-bootloader comp-rom comp-app comp-barrier_demo

#blocks are dirs in layout dir
blocks = $(shell cd layout && find * -maxdepth 0 -type d)

ecch:
	@echo $(blocks)

# Gate-level netlist for the RTL-vs-netlist equivalence test. Synthesised from
# the current generated/ output so the two sides are always in sync -- the
# netlists under layout/*/runs are snapshots of whatever the RTL was that day.
SKY130_LIB=../dependencies/pdks/sky130A/libs.ref/sky130_fd_sc_hd/lib/sky130_fd_sc_hd__tt_025C_1v80.lib

synth-s4router:
	@mkdir -p build/synth
	yosys -q -p "\
		read_verilog generated/PicoMeshBigTop.v; \
		hierarchy -top S4Router; \
		synth -top S4Router -flatten; \
		dfflibmap -liberty $(SKY130_LIB); \
		abc -liberty $(SKY130_LIB); \
		opt_clean -purge; \
		write_verilog -noattr build/synth/S4Router_synth.v"
	@echo "cells: $$(grep -c sky130_fd_sc_hd__ build/synth/S4Router_synth.v)"

$(blocks):
	@echo "Compiling block $@"
	rm -rf layout/$@/runs
	${NIX_RUN} --run-tag harden --save-views-to build/layout/$@ layout/$@/config.yaml

openroad-targets=$(blocks:%=openroad-%)

klayout-blocks=$(blocks:%=klayout-%)

$(openroad_targets):
	${NIX_RUN}--last-run --flow OpenInOpenROAD layout/$*/config.yaml

$(klayout-blocks):
	${NIX_RUN}--last-run --flow OpenInKLayout layout/$*/config.yaml

comp-rom:
	@mkdir -p build/rom
	riscv64-unknown-elf-g++ \
		-march=rv32ezicsr -mabi=ilp32e \
		-nostdlib -nostartfiles -ffreestanding -Os \
		-I src/c \
		-T src/c/rom.ld \
		-o build/rom/rom.elf src/c/rom.c
	riscv64-unknown-elf-objcopy -O binary build/rom/rom.elf build/rom/rom.bin
	riscv64-unknown-elf-objdump -d build/rom/rom.elf


comp-bootloader:
	@mkdir -p build/bootloader
	riscv64-unknown-elf-g++ \
		-march=rv32ezicsr -mabi=ilp32e \
		-nostdlib -nostartfiles -ffreestanding -Os \
		-T src/c/bootloader.ld \
		-o build/bootloader/bootloader.elf src/c/bootloader.c
	riscv64-unknown-elf-objcopy -O binary build/bootloader/bootloader.elf build/bootloader/bootloader.bin
	riscv64-unknown-elf-objdump -d build/bootloader/bootloader.elf

comp-app:
	@mkdir -p build/app
	riscv64-unknown-elf-g++ \
		-march=rv32ezicsr -mabi=ilp32e \
		-nostdlib -nostartfiles -ffreestanding -Os \
		-I src/c \
		-T src/c/app.ld \
		-o build/app/app.elf src/c/app.c
	riscv64-unknown-elf-objcopy -O binary build/app/app.elf build/app/app.bin
	riscv64-unknown-elf-objdump -d build/app/app.elf

comp-barrier_demo:
	@mkdir -p build/barrier_demo
	riscv64-unknown-elf-g++ \
		-march=rv32ezicsr -mabi=ilp32e \
		-nostdlib -nostartfiles -ffreestanding -Os \
		-I src/c \
		-T src/c/rom.ld \
		-o build/barrier_demo/barrier_demo.elf src/c/barrier_demo.c
	riscv64-unknown-elf-objcopy -O binary build/barrier_demo/barrier_demo.elf build/barrier_demo/barrier_demo.bin
	riscv64-unknown-elf-objdump -d build/barrier_demo/barrier_demo.elf