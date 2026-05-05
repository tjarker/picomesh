
verilog:
	sbt "runMain PicoMesh"

harden-pico-node:
	@rm -rf layout/PicoNode/runs build/layout/PicoNode
	librelane --save-views-to build/layout/PicoNode --run-tag harden layout/PicoNode/config.yaml

compile:
	riscv64-unknown-elf-gcc \
		-march=rv32e -mabi=ilp32e \
		-nostdlib -nostartfiles -ffreestanding -O3 \
		-T src/c/link.ld \
		-o build/prog/program.elf src/c/main.c
	riscv64-unknown-elf-objcopy -O binary build/prog/program.elf build/prog/program.bin
	riscv64-unknown-elf-objdump -d build/prog/program.elf