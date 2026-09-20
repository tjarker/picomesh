/*
 * Startup for one core's image. The benchmark itself is timed by runner.c, which
 * publishes the counters; this only sets up C and reports the outcome:
 *   [0] return value of main, 0 when the benchmark's own check passed
 *   [1] instructions retired in <bench>_main   (written by runner.c)
 *   [2] cycles spent in <bench>_main           (written by runner.c)
 *   [3] DONE_MARKER
 * It then writes its config register, which holds the core in reset.
 */

#define CORE_TAG    0xFFFF0000
#define DONE_MARKER 0x7AC1E0D0

    .section .text._start, "ax"
    .globl _start
_start:
    la    sp, __stack_top

    la    t0, __bss_start
    la    t1, __bss_end
1:  bgeu  t0, t1, 2f
    sw    zero, 0(t0)
    addi  t0, t0, 4
    j     1b
2:
    call  main

    li    t2, CORE_TAG
    lw    t2, 0(t2)
    slli  t2, t2, 28
    li    s0, 0x01000000
    or    t2, t2, s0                /* local scratchpad */

    sw    a0, 0(t2)
    li    s0, DONE_MARKER
    sw    s0, 12(t2)

    li    s0, 1
    sw    s0, 0x14(t2)              /* config register: reset this core */
3:  j     3b
