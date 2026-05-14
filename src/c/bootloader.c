#include <stdint.h>

volatile int* const CORE_TAG = (volatile int*)0xFFFF0000;


__attribute__((section(".text._start")))
void _start(void) {

  uint32_t coreTag = *CORE_TAG;
  uint32_t coreBaseAddr = coreTag << 28;


  // get boot address
  uint32_t *bootAddrReg = (uint32_t*)(coreBaseAddr | 0x01000010);
  uint32_t bootAddr = *bootAddrReg;

  uint32_t coreId;
  if (coreTag == 0x4) coreId = 0;
  else if (coreTag == 0x7) coreId = 1;
  else coreId = coreTag - 6;

  // setup stack pointer
  uint32_t *stackPtr = (uint32_t*)(0x60000400 - coreId * 0x80);
  asm volatile (
    "mv sp, %0"
    :
    : "r" (stackPtr)
  );
  
  // use asm jalr to jump to boot address
  asm volatile (
    "jalr zero, %0"
    :
    : "r" (bootAddr)
  );
}