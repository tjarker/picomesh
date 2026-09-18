/*
 * Program 1: ALU and shift instruction timing.
 *
 * Result slots (per core, relative to CPI_RESULT_BASE):
 *   0  empty block          -- rdcycle pair only, the overhead to subtract
 *   1  addi   ALU reg + immediate
 *   2  add    ALU reg + reg
 *   3  slt    compare (TWO_CYCLE_COMPARE is off, so this should match add)
 *   4  lui    upper immediate, no operand read
 *   5  slli   shift by constant
 *   6  sll    shift by register
 *   7  and    logical reg + reg
 *
 * Shifts are the interesting ones: TWO_STAGE_SHIFT is disabled in
 * PicoRvConfig.small, so the datasheet's 4-14 range does not apply and the cost
 * may scale with the shift amount. Slots 5 and 6 shift by 1; see cpi_shift
 * below for the amount sweep.
 */

#include "cpi.h"

__attribute__((section(".text._start")))
void _start(void) {

  uint32_t a = 0x12345678u;
  uint32_t b = 3u;

  barrier_enable();
  barrier_wait(); /* release all cores together */

  /* 0: overhead of the timing pair itself */
  CPI_TIME(0, );

  /* 1: addi */
  CPI_PIN(a);
  CPI_TIME(1, asm volatile(REPN("addi %0, %0, 1\n") : "+r"(a)));

  /* 2: add */
  CPI_PIN(a); CPI_PIN(b);
  CPI_TIME(2, asm volatile(REPN("add %0, %0, %1\n") : "+r"(a) : "r"(b)));

  /* 3: slt */
  CPI_PIN(a); CPI_PIN(b);
  CPI_TIME(3, asm volatile(REPN("slt %0, %0, %1\n") : "+r"(a) : "r"(b)));

  /* 4: lui -- no source operand, isolates the register-read cost in 1..3 */
  CPI_PIN(a);
  CPI_TIME(4, asm volatile(REPN("lui %0, 1\n") : "=r"(a)));

  /* 5: slli by 1 */
  CPI_PIN(a);
  CPI_TIME(5, asm volatile(REPN("slli %0, %0, 1\n") : "+r"(a)));

  /* 6: sll by a register holding 1 */
  b = 1u;
  CPI_PIN(a); CPI_PIN(b);
  CPI_TIME(6, asm volatile(REPN("sll %0, %0, %1\n") : "+r"(a) : "r"(b)));

  /* 7: and */
  CPI_PIN(a); CPI_PIN(b);
  CPI_TIME(7, asm volatile(REPN("and %0, %0, %1\n") : "+r"(a) : "r"(b)));

  /* Keep the accumulator live so nothing above can be discarded. */
  cpi_publish(15, a);

  barrier_wait();
  if (core_id() == 0) {
    cpi_done(1);
  }

  for (;;) {
  }
}
