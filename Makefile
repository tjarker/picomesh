
verilog:
	sbt "runMain PicoMesh"

harden-pico-node:
	@rm -rf layout/PicoNode/runs build/layout/PicoNode
	librelane --save-views-to build/layout/PicoNode --run-tag harden layout/PicoNode/config.yaml

harden-pico-mesh-top:
	@rm -rf layout/PicoMeshTop/runs build/layout/PicoMeshTop
	librelane --save-views-to build/layout/PicoMeshTop --run-tag harden layout/PicoMeshTop/config.yaml

harden-pico-mesh-big-top:
	@rm -rf layout/PicoMeshBigTop/runs build/layout/PicoMeshBigTop
	librelane --save-views-to build/layout/PicoMeshBigTop --run-tag harden layout/PicoMeshBigTop/config.yaml

compile:
	@mkdir -p build/prog
	riscv64-unknown-elf-gcc \
		-march=rv32e -mabi=ilp32e \
		-nostdlib -nostartfiles -ffreestanding -O3 \
		-T src/c/link.ld \
		-o build/prog/program.elf src/c/main.c
	riscv64-unknown-elf-objcopy -O binary build/prog/program.elf build/prog/program.bin
	riscv64-unknown-elf-objdump -d build/prog/program.elf