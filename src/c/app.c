#include "common.h"


__attribute__((section(".text._start")))
void _start(void) {

  uint32_t coreId = core_id();
  MEM[coreId] = coreId;
  while (1) {
    // Loop forever
  }
}