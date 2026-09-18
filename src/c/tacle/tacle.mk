# TACLe kernel benchmarks for the WideBattutaArray simulation (src/test/scala/TacleBench.scala).
#
# Each benchmark is compiled once and linked once per core window, so all six cores
# run it at the same time without sharing globals:
#   program ROM  ROM_BASE                               dispatch.S
#                ROM_BASE + ((k + 1) << WINDOW_SHIFT)   core k .text
#   memLow       DATA_BASE + (k << WINDOW_SHIFT)        core k .rodata .data .bss, stack on top

TACLE_SRC   := third_party/tacle-bench/bench/kernel
TACLE_DIR   := src/c/tacle
TACLE_BUILD := build/tacle

TACLE_CC      := riscv64-unknown-elf-gcc
TACLE_AR      := riscv64-unknown-elf-ar
TACLE_OBJCOPY := riscv64-unknown-elf-objcopy

TACLE_ARCH    := -march=rv32e_zicsr -mabi=ilp32e
TACLE_CFLAGS  := $(TACLE_ARCH) -O2 -ffreestanding -fno-common -g
TACLE_LDFLAGS := $(TACLE_ARCH) -nostdlib -nostartfiles -Wl,--no-relax -Wl,--gc-sections

TACLE_ROM_BASE      := 0x08000000
TACLE_DATA_BASE     := 0x10000000
TACLE_WINDOW_SHIFT  := 18
TACLE_STACK_RESERVE := 0x4000

# Only the time-predictable integer kernels. Excluded for linking soft-float routines:
# complex_updates cosf cubic deg2rad fft filterbank fir2dim iir lms ludcmp minver pm
# quicksort rad2deg st. Excluded for recursion: bitcount bitonic fac recursion.
TACLE_KERNELS := binarysearch bsort countnegative insertsort isqrt jfdctint matrix1 md5 prime sha

# our own programs, each a directory under src/c/tacle, run ahead of the TACLe kernels
TACLE_LOCAL      := smoke bytestore
TACLE_BENCHMARKS := $(TACLE_LOCAL) $(TACLE_KERNELS)
tacle_src = $(if $(filter $(TACLE_LOCAL),$(1)),$(TACLE_DIR)/$(1),$(TACLE_SRC)/$(1))

TACLE_RT_SRCS := $(wildcard $(TACLE_DIR)/rt/compiler-rt/*.c) $(TACLE_DIR)/rt/compiler-rt/riscv/mulsi3.S $(TACLE_DIR)/rt/libc_min.c
TACLE_RT_OBJS := $(patsubst $(TACLE_DIR)/rt/%,$(TACLE_BUILD)/rt/%.o,$(TACLE_RT_SRCS))
TACLE_RT_LIB  := $(TACLE_BUILD)/rt/libtaclert.a
TACLE_CRT0    := $(TACLE_BUILD)/rt/crt0.o

.PHONY: tacle
tacle: $(TACLE_BUILD)/layout.properties $(TACLE_BUILD)/dispatch.text.bin $(foreach b,$(TACLE_BENCHMARKS),$(TACLE_BUILD)/$(b)/stamp)

$(TACLE_BUILD)/layout.properties: $(TACLE_DIR)/tacle.mk
	@mkdir -p $(@D)
	@printf 'romBase=%s\ndataBase=%s\nwindowShift=%s\nbenchmarks=%s\n' $(TACLE_ROM_BASE) $(TACLE_DATA_BASE) $(TACLE_WINDOW_SHIFT) "$(TACLE_BENCHMARKS)" > $@

# no builtins, so the runtime's loops are not turned into calls to itself
$(TACLE_BUILD)/rt/%.c.o: $(TACLE_DIR)/rt/%.c
	@mkdir -p $(@D)
	$(TACLE_CC) $(TACLE_CFLAGS) -fno-builtin -fno-tree-loop-distribute-patterns -c $< -o $@

$(TACLE_BUILD)/rt/%.S.o: $(TACLE_DIR)/rt/%.S
	@mkdir -p $(@D)
	$(TACLE_CC) $(TACLE_ARCH) -c $< -o $@

$(TACLE_RT_LIB): $(TACLE_RT_OBJS)
	@rm -f $@
	$(TACLE_AR) rcs $@ $^

$(TACLE_CRT0): $(TACLE_DIR)/crt0.S
	@mkdir -p $(@D)
	$(TACLE_CC) $(TACLE_ARCH) -c $< -o $@

$(TACLE_BUILD)/dispatch.text.bin: $(TACLE_DIR)/dispatch.S $(TACLE_DIR)/tacle.mk
	@mkdir -p $(@D)
	$(TACLE_CC) $(TACLE_LDFLAGS) -DROM_BASE=$(TACLE_ROM_BASE) -DWINDOW_SHIFT=$(TACLE_WINDOW_SHIFT) -Wl,-Ttext=$(TACLE_ROM_BASE) $< -o $(TACLE_BUILD)/dispatch.elf
	$(TACLE_OBJCOPY) -O binary -j .text $(TACLE_BUILD)/dispatch.elf $@

# Compiles a benchmark once, then links it and splits it into text and data images for each core.
.SECONDEXPANSION:
$(TACLE_BUILD)/%/stamp: $$(wildcard $$(call tacle_src,$$*)/*.c $$(call tacle_src,$$*)/*.h) $(TACLE_RT_LIB) $(TACLE_CRT0) $(TACLE_DIR)/tacle.ld $(TACLE_DIR)/tacle.mk
	@rm -rf $(@D)
	@mkdir -p $(@D)/obj
	@for src in $(wildcard $(call tacle_src,$*)/*.c); do \
	  echo "$(TACLE_CC) -c $$src"; \
	  $(TACLE_CC) $(TACLE_CFLAGS) -c $$src -o $(@D)/obj/$$(basename $$src .c).o || exit 1; \
	done
	@for k in 0 1 2 3 4 5; do \
	  $(TACLE_CC) $(TACLE_LDFLAGS) -T $(TACLE_DIR)/tacle.ld \
	    -Wl,--defsym=TEXT_BASE=$$(( $(TACLE_ROM_BASE) + ((k + 1) << $(TACLE_WINDOW_SHIFT)) )) \
	    -Wl,--defsym=DATA_BASE=$$(( $(TACLE_DATA_BASE) + (k << $(TACLE_WINDOW_SHIFT)) )) \
	    -Wl,--defsym=WINDOW=$$(( 1 << $(TACLE_WINDOW_SHIFT) )) \
	    -Wl,--defsym=STACK_RESERVE=$(TACLE_STACK_RESERVE) \
	    -o $(@D)/core$$k.elf $(TACLE_CRT0) $(@D)/obj/*.o $(TACLE_RT_LIB) || exit 1; \
	  $(TACLE_OBJCOPY) -O binary -j .text $(@D)/core$$k.elf $(@D)/core$$k.text.bin || exit 1; \
	  $(TACLE_OBJCOPY) -O binary -j .rodata -j .data $(@D)/core$$k.elf $(@D)/core$$k.data.bin || exit 1; \
	done
	@echo "linked $* for 6 cores"
	@touch $@
