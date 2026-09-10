/*
 * Concurrent throughput benchmark.
 *
 * Every core runs this same program out of the access-node ROM. The cores are
 * barrier-synchronised before each phase, so all six hammer the NoC at the same
 * time and the numbers describe the system under full load, not one core alone.
 *
 * Note that instruction fetch is itself a remote access: the program lives in
 * the access node (node 0), so every instruction costs a NoC round trip. A raw
 * cycle count therefore measures fetch traffic more than data traffic. Phase 0
 * exists to cancel that out: it runs the identical loop against the core-local
 * scratchpad, so (phase_n - phase_0) / accesses is the marginal cost of one
 * remote data access under load.
 *
 * Phases. Read and write loops differ in instruction count, so each has its own
 * local baseline; only difference a phase against the baseline of its own kind.
 *   0  local scratchpad read      read baseline, no NoC data traffic
 *   1  local scratchpad write     write baseline
 *   2  read  memLow  (node 1)     all cores on one memory node
 *   3  write memLow  (node 1)     writes do not wait for a response
 *   4  read  split                even cores -> memLow, odd cores -> memHigh
 *   5  read  neighbour scratchpad core-to-core, remote wins arbitration
 *
 * Results land in memLow and are read back over Ponte at:
 *   0x1FFFFC00 + 4 * (16 + core * 12 + phase * 2)      start cycle
 *   0x1FFFFC00 + 4 * (16 + core * 12 + phase * 2 + 1)  end cycle
 *   0x1FFFFC00                                         0x5A5AD09E when complete
 *
 * Cycle counts are absolute and share a common reset, so the host can compute
 * both per-core cost and true aggregate throughput over the overlap window.
 *
 * Keep this file free of globals: .data/.bss would be linked into the ROM
 * address range, which ignores writes.
 */

#include "common.h"

#define NUM_CORES     6u
#define NUM_PHASES    6u
#define ITERS         256u
#define ACC_PER_ITER  4u

/* memLow word layout (indices into MEM[]) */
#define W_MAGIC       0u
#define W_CHECKSUM    1u
#define W_ARRIVE      2u    /* [2 .. 2+NUM_CORES)  one arrival word per core */
#define W_RESULT      16u   /* [16 .. 88)          start/end per core, phase  */
#define W_TRAFFIC     128u  /* [128 .. 132)        phase 2-4 target on memLow */

#define MAGIC_DONE    0x5A5AD09Eu
#define TOKEN(seq)    (0xA5A50000u + (seq))

/* memHigh (node 2) words 0..63 are free; the per-core stacks live above them. */
static volatile uint32_t *const MEMHIGH = (volatile uint32_t *)0x20000020;

static inline uint32_t rdcycle(void) {
  uint32_t c;
  asm volatile("rdcycle %0" : "=r"(c));
  return c;
}

/*
 * Both loops are noinline so every phase executes one identical instruction
 * stream. The differential against phase 0 is only meaningful if the fetch
 * pattern is the same in each phase.
 */
static uint32_t __attribute__((noinline)) read_loop(volatile uint32_t *p, uint32_t n) {
  uint32_t s = 0;
  while (n--) {
    s += p[0];
    s += p[1];
    s += p[2];
    s += p[3];
  }
  return s;
}

static void __attribute__((noinline)) write_loop(volatile uint32_t *p, uint32_t n) {
  while (n--) {
    p[0] = n;
    p[1] = n;
    p[2] = n;
    p[3] = n;
  }
}

/*
 * Core 0 is the barrier master. Workers announce arrival in memLow and then spin
 * on their own scratchpad, which is a local single-cycle access -- spinning on a
 * remote flag would load the network we are trying to measure.
 */
static void barrier(uint32_t coreId, uint32_t seq) {
  uint32_t tok = TOKEN(seq);

  if (coreId == 0) {
    for (uint32_t c = 1; c < NUM_CORES; c++) {
      while (MEM[W_ARRIVE + c] != tok) {
      }
    }
    for (uint32_t c = 1; c < NUM_CORES; c++) {
      scratchpad(c)[0] = tok;
    }
  } else {
    volatile uint32_t *sp = local_scratchpad();
    MEM[W_ARRIVE + coreId] = tok;
    while (sp[0] != tok) {
    }
  }
}

__attribute__((section(".text._start")))
void _start(void) {
  uint32_t coreId = core_id();
  volatile uint32_t *sp = local_scratchpad();
  /* Watch the frame size: each core only owns 128 bytes of stack. */
  uint32_t res[NUM_PHASES * 2];
  uint32_t checksum = 0;

  /* Clear the go-flag before announcing arrival, so a stale scratchpad value
     cannot release this core early on the first barrier. */
  sp[0] = 0;

  for (uint32_t phase = 0; phase < NUM_PHASES; phase++) {
    volatile uint32_t *p;

    switch (phase) {
      /* Phase 1 writes over the go-flag in scratchpad[0]. That is harmless: the
         next barrier waits for a fresh token, and the loop only writes 0..255. */
      case 0:
      case 1:  p = sp; break;
      case 4:  p = (coreId & 1u) ? MEMHIGH : (MEM + W_TRAFFIC); break;
      /* No % here: it pulls in __umodsi3, and we link without libgcc. */
      case 5:  p = scratchpad(coreId + 1u == NUM_CORES ? 0u : coreId + 1u); break;
      default: p = MEM + W_TRAFFIC; break;
    }

    barrier(coreId, phase + 1u);

    uint32_t t0 = rdcycle();
    if (phase == 1u || phase == 3u) {
      write_loop(p, ITERS);
    } else {
      checksum += read_loop(p, ITERS);
    }
    uint32_t t1 = rdcycle();

    res[phase * 2] = t0;
    res[phase * 2 + 1] = t1;
  }

  /* Publish only after the last phase, so result traffic never overlaps a
     measurement window. */
  for (uint32_t i = 0; i < NUM_PHASES * 2; i++) {
    MEM[W_RESULT + coreId * NUM_PHASES * 2 + i] = res[i];
  }

  barrier(coreId, NUM_PHASES + 1u);

  if (coreId == 0) {
    MEM[W_CHECKSUM] = checksum;
    MEM[W_MAGIC] = MAGIC_DONE;
  }

  while (1) {
  }
}
