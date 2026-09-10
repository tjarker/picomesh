#include <stdint.h>

volatile uint32_t* const CORE_TAG = (volatile uint32_t*)0xFFFF0000;

volatile uint32_t* const BARRIER = (volatile uint32_t*)0xFFFF0004;
volatile uint32_t* const BARRIER_EN = (volatile uint32_t*)0xFFFF0008;


volatile uint32_t* const MEM = (volatile uint32_t*)(0x1FFFFC00);


uint32_t core_id_to_addr(uint32_t coreId) {
  return (coreId + 3) << 28;
}

uint32_t core_tag_to_id(uint32_t coreTag) {
    return coreTag - 3;
}

inline uint32_t core_id() {
    return core_tag_to_id(*CORE_TAG);
}

inline volatile uint32_t *local_scratchpad() {
  uint32_t coreTag = *CORE_TAG;
  return (volatile uint32_t*)(coreTag << 28 | 0x01000000);
}

inline volatile uint32_t *scratchpad(uint32_t coreId) {
  uint32_t coreAddr = core_id_to_addr(coreId);
  return (volatile uint32_t*)(coreAddr | 0x01000000);
}

inline volatile uint32_t *local_boot_addr() {
  uint32_t coreTag = *CORE_TAG;
  return (volatile uint32_t*)(coreTag << 28 | 0x01000010);
}

inline volatile uint32_t *boot_addr(uint32_t coreId) {
  uint32_t coreAddr = core_id_to_addr(coreId);
  return (volatile uint32_t*)(coreAddr | 0x01000010);
}

inline volatile uint32_t *local_config_reg() {
  uint32_t coreTag = *CORE_TAG;
  return (volatile uint32_t*)(coreTag << 28 | 0x01000014);
}

inline volatile uint32_t *config_reg(uint32_t coreId) {
  uint32_t coreAddr = core_id_to_addr(coreId);
  return (volatile uint32_t*)(coreAddr | 0x01000014);
}

void barrier_enable() {
  *BARRIER_EN = 1;
}

void barrier_disable() {
  *BARRIER_EN = 0;
}

void barrier_wait() {
  *BARRIER = 1;
}