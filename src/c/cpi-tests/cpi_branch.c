/*
 * Program 2: control-transfer timing.
 *
 * Result slots (per core, relative to CPI_RESULT_BASE):
 *   0  empty block
 *   1  branch not taken     bne with equal operands, falls through
 *   2  branch taken         beq with equal operands, jumps to the next word
 *   3  jal                  unconditional jump to the next word
 *   4  call + return pair   jal ra, f  where f is a bare ret
 *   5  jalr                 slot 4 minus slot 3, computed on the host
 *
 * Every branch here jumps to the immediately following instruction, so the
 * target is always the sequentially next word. That keeps the fetch stream
 * identical to the fall-through case and isolates the branch cost itself. Once
 * the prefetch buffer exists this stops being true -- a taken branch to the next
 * word stays inside the current line, whereas a real branch leaves it -- so
 * re-measure with a far target then.
 *
 * Slot 4 includes one extra fetch for the `ret`, so it is a call/return pair
 * cost rather than a pure jalr; slot 5 records the difference for convenience.
 */

#include "cpi.h"

/* A function that does nothing but return. Kept out of line so the call is a
   real jal/jalr pair rather than being inlined away. */
static void __attribute__((noinline)) cpi_ret(void) {}

__attribute__((section(".text._start")))
void _start(void) {

  uint32_t a = 7u;

  barrier_enable();
  barrier_wait();

  /* 0: overhead */
  CPI_TIME(0, );

  CPI_PIN(a);

  /* 1: branch not taken -- a != a is false */
  CPI_TIME(1, asm volatile(REPN("bne %0, %0, 1f\n1:\n") : : "r"(a)));

  /* 2: branch taken -- a == a is true, target is the next word */
  CPI_TIME(2, asm volatile(REPN("beq %0, %0, 1f\n1:\n") : : "r"(a)));

  /* 3: jal to the next word, link register discarded */
  CPI_TIME(3, asm volatile(REPN("jal zero, 1f\n1:\n")));

  /* 4: call + return. ra is clobbered by the jal. */
  {
    uint32_t t0 = rdcycle();
    for (uint32_t i = 0; i < CPI_N; i++) {
      cpi_ret();
    }
    uint32_t t1 = rdcycle();
    cpi_publish(4, t1 - t0);
  }

  cpi_publish(15, a);

  barrier_wait();
  if (core_id() == 0) {
    cpi_done(2);
  }

  for (;;) {
  }
}
