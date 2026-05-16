
all: comp verilog harden-pico-node pico-mesh-synth pico-mesh-layout

verilog:
	sbt "runMain PicoMeshBigTop"

NIX_RUN=nix run github:chipfoundry/openlane-2/CI2511 -- 

harden-pico-node:
	@rm -rf layout/PicoNode/runs
	$(NIX_RUN) --save-views-to build/layout/PicoNode --run-tag harden layout/PicoNode/config.yaml

openroad-pico-node:
	${NIX_RUN}--last-run --flow OpenInOpenROAD layout/PicoNode/config.yaml

klayout-pico-node:
	${NIX_RUN}--last-run --flow OpenInKLayout layout/PicoNode/config.yaml

pico-mesh-reset:
	@rm -rf layout/PicoMeshBigTop/runs

pico-mesh-synth:
	${NIX_RUN} --run-tag harden --to "Checker.NetlistAssignStatements" layout/PicoMeshBigTop/config.yaml

pico-mesh-layout:
	${NIX_RUN} --run-tag harden --from "OpenROAD.CheckSDCFiles" --save-views-to build/layout/PicoMeshBigTop layout/PicoMeshBigTop/config.yaml

openroad-pico-mesh:
	${NIX_RUN}--last-run --flow OpenInOpenROAD layout/PicoMeshBigTop/config.yaml

klayout-pico-mesh:
	${NIX_RUN}--last-run --flow OpenInKLayout layout/PicoMeshBigTop/config.yaml

comp: comp-bootloader comp-rom comp-app

comp-rom:
	@mkdir -p build/rom
	riscv64-unknown-elf-g++ \
		-march=rv32e -mabi=ilp32e \
		-nostdlib -nostartfiles -ffreestanding -Os \
		-I src/c \
		-T src/c/rom.ld \
		-o build/rom/rom.elf src/c/rom.c
	riscv64-unknown-elf-objcopy -O binary build/rom/rom.elf build/rom/rom.bin
	riscv64-unknown-elf-objdump -d build/rom/rom.elf


comp-bootloader:
	@mkdir -p build/bootloader
	riscv64-unknown-elf-g++ \
		-march=rv32e -mabi=ilp32e \
		-nostdlib -nostartfiles -ffreestanding -Os \
		-T src/c/bootloader.ld \
		-o build/bootloader/bootloader.elf src/c/bootloader.c
	riscv64-unknown-elf-objcopy -O binary build/bootloader/bootloader.elf build/bootloader/bootloader.bin
	riscv64-unknown-elf-objdump -d build/bootloader/bootloader.elf

comp-app:
	@mkdir -p build/app
	riscv64-unknown-elf-g++ \
		-march=rv32e -mabi=ilp32e \
		-nostdlib -nostartfiles -ffreestanding -Os \
		-I src/c \
		-T src/c/app.ld \
		-o build/app/app.elf src/c/app.c
	riscv64-unknown-elf-objcopy -O binary build/app/app.elf build/app/app.bin
	riscv64-unknown-elf-objdump -d build/app/app.elf