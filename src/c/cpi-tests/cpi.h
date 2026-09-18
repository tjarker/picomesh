/*
 * Shared harness for the per-instruction-type timing tests.
 *
 * Method. Every instruction on this machine costs its execution cycles plus one
 * instruction fetch over the NoC, so a single measurement cannot separate the
 * two. What it can do exactly is:
 *
 *   raw[i]              = CPI_N * (c_exec(i) + t_fetch) + overhead
 *   raw[i] - raw[EMPTY] = CPI_N * (c_exec(i) + t_fetch)
 *   raw[i] - raw[j]     = CPI_N * (c_exec(i) - c_exec(j))     <- exact
 *
 * The last line is the useful one: t_fetch is identical for every instruction
 * issued by a given core, so it cancels in a difference between two types. To
 * get absolute execution cycles, subtract the t_fetch that ScheduleAlign
 * reports for this core (5..7 cycles depending on the core).
 *
 * Raw cycle deltas are published rather than per-instruction averages: the
 * division would pull in libgcc, and the host can divide by CPI_N instead.
 *
 * Results are written as soon as each test finishes, so a program that hangs
 * leaves the index of the offending test as the last populated slot.
 *
 * Requires ENABLE_COUNTERS in the core (PicoRvConfig.small sets it).
 */

#ifndef CPI_H
#define CPI_H

#include "common.h"

/* Instructions per timed block. Powers of two only, so the host divides with a
   shift and the unrolled block stays a round number of ROM words. */
#define CPI_N 32u

/* memLow layout, in words */
#define CPI_MAGIC_WORD 0u
#define CPI_N_WORD 1u
#define CPI_RESULT_BASE 16u
#define CPI_SLOTS_PER_CORE 16u

#define CPI_MAGIC 0x0C910000u

static inline uint32_t rdcycle(void) {
  uint32_t c;
  asm volatile("rdcycle %0" : "=r"(c));
  return c;
}

#define REP2(x) x x
#define REP4(x) REP2(x) REP2(x)
#define REP8(x) REP4(x) REP4(x)
#define REP16(x) REP8(x) REP8(x)
#define REP32(x) REP16(x) REP16(x)

/* Keep in step with CPI_N. */
#define REPN(x) REP32(x)

static inline void cpi_publish(uint32_t idx, uint32_t value) {
  MEM[CPI_RESULT_BASE + core_id() * CPI_SLOTS_PER_CORE + idx] = value;
}

/* Announce which program produced the results and with what block size. Only
   core 0 writes these, after every core has finished. */
static inline void cpi_done(uint32_t programId) {
  MEM[CPI_N_WORD] = CPI_N;
  MEM[CPI_MAGIC_WORD] = CPI_MAGIC | programId;
}

/* Force a value into a register before timing starts. Without this the compiler
   is free to materialise a constant operand between the first rdcycle and the
   block, which lands the setup inside the measurement. The volatile asm makes
   the value opaque, so it cannot be rematerialised later either. */
#define CPI_PIN(x) asm volatile("" : "+r"(x))

/* Time one unrolled block. `stmt` must be a full asm statement so each test can
   declare its own operands. Pin every operand before calling this. */
#define CPI_TIME(idx, stmt)                                                    \
  do {                                                                         \
    uint32_t _t0 = rdcycle();                                                  \
    stmt;                                                                      \
    uint32_t _t1 = rdcycle();                                                  \
    cpi_publish((idx), _t1 - _t0);                                             \
  } while (0)

#endif
