
verilog:
	sbt "runMain PicoMesh"

NIX_RUN=nix run github:chipfoundry/openlane-2/CI2511 -- 

harden-pico-node:
	@rm -rf layout/PicoNode/runs build/layout/PicoNode
	$(NIX_RUN) --save-views-to build/layout/PicoNode --run-tag harden layout/PicoNode/config.yaml

openroad-pico-node:
	${NIX_RUN}--last-run --flow OpenInOpenROAD layout/PicoNode/config.yaml

klayout-pico-node:
	${NIX_RUN}--last-run --flow OpenInKLayout layout/PicoNode/config.yaml

harden-pico-mesh:
	@rm -rf layout/PicoMeshTop/runs build/layout/PicoMeshTop
	$(NIX_RUN) --save-views-to build/layout/PicoMeshTop --run-tag harden layout/PicoMeshTop/config.yaml

harden-pico-mesh-big:
	@rm -rf layout/PicoMeshBigTop/runs build/layout/PicoMeshBigTop
	$(NIX_RUN) --save-views-to build/layout/PicoMeshBigTop --run-tag harden layout/PicoMeshBigTop/config.yaml

pico-mesh-reset:
	@rm -rf layout/PicoMeshBigTop/runs build/layout/PicoMeshBigTop

pico-mesh-synth:
	${NIX_RUN} --run-tag harden --to "Checker.NetlistAssignStatements" layout/PicoMeshBigTop/config.yaml

pico-mesh-layout:
	${NIX_RUN} --run-tag harden --from "OpenROAD.CheckSDCFiles" --save-views-to build/layout/PicoMeshBigTop layout/PicoMeshBigTop/config.yaml

openroad-pico-mesh-big:
	${NIX_RUN}--last-run --flow OpenInOpenROAD layout/PicoMeshBigTop/config.yaml

klayout-pico-mesh-big:
	${NIX_RUN}--last-run --flow OpenInKLayout layout/PicoMeshBigTop/config.yaml


s4noc-req-harden:
	@rm -rf layout/S4NocReq/runs build/layout/S4NocReq
	$(NIX_RUN) --save-views-to build/layout/S4NocReq --run-tag harden layout/S4NocReq/config.yaml

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