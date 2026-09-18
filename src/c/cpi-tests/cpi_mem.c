/*
 * Program 3: load and store timing, by destination.
 *
 * This is the one that measures the network rather than the core. A local
 * scratchpad access never leaves the tile; every other access is a NoC round
 * trip, so the difference between slot 1 and slots 3/5/7 is the data access
 * latency t_m for this core and destination.
 *
 * Result slots (per core, relative to CPI_RESULT_BASE):
 *   0  empty block
 *   1  lw  local scratchpad      no NoC traffic
 *   2  sw  local scratchpad
 *   3  lw  memLow  (node 1)
 *   4  sw  memLow  (node 1)
 *   5  lw  memHigh (node 2)
 *   6  sw  memHigh (node 2)
 *   7  lw  access node ROM (node 0), the instruction memory used as data
 *
 * Ordering note: the store tests come after their matching load tests on
 * purpose. BlockingRequesterNi enters WaitForResp after every request including
 * writes, so if the responder does not answer a write, the store test is where
 * the program stops -- and because results are published as they are taken, the
 * last populated slot says so.
 *
 * The stores target scratch words, not the result area.
 */

#include "cpi.h"

/* Scratch words in each memory node, clear of the result area (16..111). */
#define MEM_SCRATCH 200u
static volatile uint32_t *const MEMHIGH = (volatile uint32_t *)0x20000020;
static volatile uint32_t *const ROMWORD = (volatile uint32_t *)0x08000000;

__attribute__((section(".text._start")))
void _start(void) {

  /* Locals, not the file-scope constants: CPI_PIN needs an lvalue, and without
     pinning the compiler materialises the address with lui/addi between the
     first rdcycle and the block, putting the setup inside the measurement. */
  volatile uint32_t *sp = local_scratchpad();
  volatile uint32_t *low = MEM + MEM_SCRATCH;
  volatile uint32_t *high = MEMHIGH;
  volatile uint32_t *rom = ROMWORD;
  uint32_t v = 0u;

  barrier_enable();
  barrier_wait();

  /* 0: overhead */
  CPI_TIME(0, );

  CPI_PIN(sp); CPI_PIN(low); CPI_PIN(high); CPI_PIN(rom); CPI_PIN(v);

  /* 1: local scratchpad load */
  CPI_TIME(1, asm volatile(REPN("lw %0, 0(%1)\n") : "=r"(v) : "r"(sp)));

  CPI_PIN(sp); CPI_PIN(low); CPI_PIN(high); CPI_PIN(rom); CPI_PIN(v);

  /* 2: local scratchpad store */
  CPI_TIME(2, asm volatile(REPN("sw %0, 0(%1)\n") : : "r"(v), "r"(sp)));

  CPI_PIN(sp); CPI_PIN(low); CPI_PIN(high); CPI_PIN(rom); CPI_PIN(v);

  /* 3: remote load, memLow */
  CPI_TIME(3, asm volatile(REPN("lw %0, 0(%1)\n") : "=r"(v) : "r"(low)));

  CPI_PIN(sp); CPI_PIN(low); CPI_PIN(high); CPI_PIN(rom); CPI_PIN(v);

  /* 4: remote store, memLow */
  CPI_TIME(4, asm volatile(REPN("sw %0, 0(%1)\n") : : "r"(v), "r"(low)));

  CPI_PIN(sp); CPI_PIN(low); CPI_PIN(high); CPI_PIN(rom); CPI_PIN(v);

  /* 5: remote load, memHigh */
  CPI_TIME(5, asm volatile(REPN("lw %0, 0(%1)\n") : "=r"(v) : "r"(high)));

  CPI_PIN(sp); CPI_PIN(low); CPI_PIN(high); CPI_PIN(rom); CPI_PIN(v);

  /* 6: remote store, memHigh */
  CPI_TIME(6, asm volatile(REPN("sw %0, 0(%1)\n") : : "r"(v), "r"(high)));

  CPI_PIN(sp); CPI_PIN(low); CPI_PIN(high); CPI_PIN(rom); CPI_PIN(v);

  /* 7: load from the instruction memory, i.e. node 0 as a data source */
  CPI_TIME(7, asm volatile(REPN("lw %0, 0(%1)\n") : "=r"(v) : "r"(rom)));

  cpi_publish(15, v);

  barrier_wait();
  if (core_id() == 0) {
    cpi_done(3);
  }

  for (;;) {
  }
}
