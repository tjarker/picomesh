#include "common.h"


__attribute__((section(".text._start")))
void _start(void) {

  uint32_t coreId = core_id();

  MEM[coreId] = coreId;
  
  if (coreId == 0) {
    scratchpad(1)[0] = 0x1;
  }

  volatile uint32_t *localScratchpad = local_scratchpad();
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
  // reset yourself
  *local_config_reg() = 0x1; // write to config reg to reset core

  while (1) {
    // Loop forever
  }
}