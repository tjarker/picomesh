#include <stdint.h>

volatile int* const CORE_TAG = (volatile int*)0xFFFF0000;


__attribute__((section(".text._start")))
void _start(void) {

  uint32_t coreTag = *CORE_TAG;
  uint32_t coreBaseAddr = coreTag << 28;


  // get boot address
  uint32_t *bootAddrReg = (uint32_t*)(coreBaseAddr | 0x01000010);
  uint32_t bootAddr = *bootAddrReg;
  
  // use asm jalr to jump to boot address
  asm volatile (
    "jalr zero, %0"
    :
    : "r" (bootAddr)
  );
}