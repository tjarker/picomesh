#include "common.h"


__attribute__((section(".text._start")))
void _start(void) {

  uint32_t coreId = core_id();

  barrier_enable();

  for (int i = 0; i < 6; i++) {
    MEM[coreId] = i;
    barrier_wait();
  }

  barrier_disable();
  while (1) {
    // Loop forever
  }

}