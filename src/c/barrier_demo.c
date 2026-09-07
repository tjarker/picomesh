#include "common.h"


__attribute__((section(".text._start")))
void _start(void) {

  volatile uint32_t *localScratchpad = local_scratchpad();

  uint32_t coreId = core_id();

  barrier_enable();

  for (int i = 0; i < 6; i++) {
    MEM[coreId] = i;
    barrier_wait();
  }

  barrier_disable();

  // get performance counters from riscv csr
  uint32_t instr_retired;
  uint32_t cycle_count;
  asm volatile ("csrr %0, 0xC02" : "=r"(instr_retired));
  asm volatile ("csrr %0, 0xC00" : "=r"(cycle_count));
  localScratchpad[1] = instr_retired;
  localScratchpad[2] = cycle_count;

  // reset yourself
  *local_config_reg() = 0x1; // write to config reg to reset core
  while (1) {
    // Loop forever
  }

}