/*
 * Startup for one core's TACLe image.
 *
 * Every core starts main in the same cycle (barrier), and reports through its
 * local scratchpad, which the testbench reads over the ponte port:
 *   [0] return value of main, 0 when the benchmark's own check passed
 *   [1] instructions retired in main
 *   [2] cycles spent in main
 *   [3] DONE_MARKER
 * It then writes its config register, which holds the core in reset.
 */

#define CORE_TAG    0xFFFF0000
#define BARRIER     0xFFFF0004
#define BARRIER_EN  0xFFFF0008
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
    li    t0, BARRIER_EN
    li    t1, 1
    sw    t1, 0(t0)
    li    t0, BARRIER               /* blocks until all six cores arrive */
    sw    t1, 0(t0)

    csrr  s0, 0xC00                 /* cycle */
    csrr  s1, 0xC02                 /* instret */
    call  main
    csrr  t0, 0xC00
    csrr  t1, 0xC02
    sub   t0, t0, s0
    sub   t1, t1, s1

    li    t2, CORE_TAG
    lw    t2, 0(t2)
    slli  t2, t2, 28
    li    s0, 0x01000000
    or    t2, t2, s0                /* local scratchpad */

    sw    a0, 0(t2)
    sw    t1, 4(t2)
    sw    t0, 8(t2)
    li    s0, DONE_MARKER
    sw    s0, 12(t2)

    li    s0, 1
    sw    s0, 0x14(t2)              /* config register: reset this core */
3:  j     3b
