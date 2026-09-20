// Runs one benchmark the way its own main does, but reads the counters directly around
// <bench>_main, so the published numbers cover the benchmark's entry function. That is the
// code other platforms report for the same programs. TACLe's own main is renamed away at
// compile time (-Dmain=...), and calling the benchmark from this separate translation unit
// keeps the compiler from inlining the entry function into its caller.
//
//   TACLE_BENCH     name of the benchmark
//   TACLE_EXPECTED  what <bench>_return() gives after a correct run, which TACLe's main
//                   compares against (0 for all but binarysearch)

#include <stdint.h>

#define CONCAT_(a, b) a##b
#define CONCAT(a, b) CONCAT_(a, b)
#define BENCH_INIT CONCAT(TACLE_BENCH, _init)
#define BENCH_MAIN CONCAT(TACLE_BENCH, _main)
#define BENCH_RETURN CONCAT(TACLE_BENCH, _return)

void BENCH_INIT(void);
void BENCH_MAIN(void);
int BENCH_RETURN(void);

#define CORE_TAG ((volatile uint32_t *)0xFFFF0000)
#define BARRIER ((volatile uint32_t *)0xFFFF0004)
#define BARRIER_EN ((volatile uint32_t *)0xFFFF0008)

static inline uint32_t rdcycle(void) {
  uint32_t v;
  __asm__ volatile("csrr %0, 0xC00" : "=r"(v));
  return v;
}

static inline uint32_t rdinstret(void) {
  uint32_t v;
  __asm__ volatile("csrr %0, 0xC02" : "=r"(v));
  return v;
}

int main(void) {
  volatile uint32_t *scratchpad = (volatile uint32_t *)((*CORE_TAG << 28) | 0x01000000);

  BENCH_INIT();

  // all six cores enter the measured region in the same cycle
  *BARRIER_EN = 1;
  *BARRIER = 1;

  uint32_t c0 = rdcycle();
  uint32_t i0 = rdinstret();
  BENCH_MAIN();
  uint32_t i1 = rdinstret();
  uint32_t c1 = rdcycle();

  scratchpad[1] = i1 - i0;
  scratchpad[2] = c1 - c0;

  return BENCH_RETURN() != TACLE_EXPECTED;
}
