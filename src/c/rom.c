#include "common.h"


__attribute__((section(".text._start")))
void _start(void) {

  uint32_t coreId = core_id();
  volatile uint32_t *localScratchpad = local_scratchpad();

  MEM[coreId] = coreId;

  localScratchpad[0] = 0; // clear scratchpad
  
  if (coreId == 0) {
    scratchpad(1)[0] = 0x1;
  }

  while(localScratchpad[0] == 0) {
    //asm volatile ("nop"); // wait for write to scratchpad
  } // wait for write to scratchpad

  if (coreId != 0) {
    scratchpad(coreId == 5 ? 0 : coreId + 1)[0] = localScratchpad[0] + coreId;
  }

  if (coreId == 0) {
    MEM[6] = localScratchpad[0]; // place result in memory
    for (int i = 0; i < 6; i++) {
      scratchpad(i)[0] += 0x2220000; // test readback and update
      MEM[i] += 0x3330000; // test readback and update
    }
  }


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