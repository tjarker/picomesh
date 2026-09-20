# TACLe benchmarks for the WideBattutaArray simulation (src/test/scala/TacleBench.scala).
#
# Each benchmark is compiled once and linked once per core window, so all six cores
# run it at the same time without sharing globals:
#   program ROM  ROM_BASE                               dispatch.S
#                ROM_BASE + ((k + 1) << WINDOW_SHIFT)   core k .text
#   memLow       DATA_BASE + (k << WINDOW_SHIFT)        core k .rodata .data .bss, stack on top

TACLE_SRC   := third_party/tacle-bench/bench
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

# Benchmarks whose measured region needs neither floating point nor multiplication or
# division: the cores have no M extension, so those would be software routines here and
# hardware instructions on the platforms we compare against. Kernels excluded for
# soft-float: complex_updates cosf cubic deg2rad fft filterbank fir2dim iir lms ludcmp
# minver pm quicksort rad2deg st. For recursion: bitcount bitonic fac recursion. For
# software multiply or divide: matrix1 prime.
TACLE_KERNELS := binarysearch bsort countnegative insertsort isqrt jfdctint md5 sha

# The sequential benchmarks under the same rule. Excluded for soft-float: audiobeam epic
# fmref susan. For recursion: ammunition anagram huff_enc. For software multiply or divide:
# adpcm_dec adpcm_enc cjpeg_transupp dijkstra g723_enc gsm_dec gsm_enc h264_dec mpeg2
# rijndael_dec rijndael_enc.
TACLE_SEQ := cjpeg_wrbmp huff_dec ndes petrinet statemate

# application benchmarks: powerwindow is the four-task automotive window controller, integer only
TACLE_APPS := powerwindow

# our own programs, each a directory under src/c/tacle, run ahead of the TACLe benchmarks
TACLE_LOCAL      := smoke bytestore
TACLE_BENCHMARKS := $(TACLE_LOCAL) $(TACLE_KERNELS) $(TACLE_APPS) $(TACLE_SEQ)
tacle_src = $(if $(filter $(TACLE_LOCAL),$(1)),$(TACLE_DIR)/$(1),$(if $(filter $(TACLE_APPS),$(1)),$(TACLE_SRC)/app/$(1),$(if $(filter $(TACLE_SEQ),$(1)),$(TACLE_SRC)/sequential/$(1),$(TACLE_SRC)/kernel/$(1))))

TACLE_RT_SRCS := $(wildcard $(TACLE_DIR)/rt/compiler-rt/*.c) $(TACLE_DIR)/rt/compiler-rt/riscv/mulsi3.S $(TACLE_DIR)/rt/libc_min.c
TACLE_RT_OBJS := $(patsubst $(TACLE_DIR)/rt/%,$(TACLE_BUILD)/rt/%.o,$(TACLE_RT_SRCS))
TACLE_RT_LIB  := $(TACLE_BUILD)/rt/libtaclert.a
TACLE_CRT0    := $(TACLE_BUILD)/rt/crt0.o
TACLE_RUNNER  := $(TACLE_DIR)/runner.c

# what <bench>_return() gives after a correct run; TACLe's own main compares against it
TACLE_EXPECTED_binarysearch := -1

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
$(TACLE_BUILD)/%/stamp: $$(wildcard $$(call tacle_src,$$*)/*.c $$(call tacle_src,$$*)/*.h) $(TACLE_RT_LIB) $(TACLE_CRT0) $(TACLE_RUNNER) $(TACLE_DIR)/tacle.ld $(TACLE_DIR)/tacle.mk
	@rm -rf $(@D)
	@mkdir -p $(@D)/obj
	@for src in $(wildcard $(call tacle_src,$*)/*.c); do \
	  echo "$(TACLE_CC) -c $$src"; \
	  $(TACLE_CC) $(TACLE_CFLAGS) -Dmain=tacle_unused_main -c $$src -o $(@D)/obj/$$(basename $$src .c).o || exit 1; \
	done
	@$(TACLE_CC) $(TACLE_CFLAGS) -DTACLE_BENCH=$* -DTACLE_EXPECTED=$(or $(TACLE_EXPECTED_$*),0) \
	  -c $(TACLE_RUNNER) -o $(@D)/obj/runner.o
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
