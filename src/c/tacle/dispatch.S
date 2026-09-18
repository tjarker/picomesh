/*
 * First line of the program ROM for the TACLe images. The bootloader jumps here
 * on every core (default boot address); each core continues in its own window.
 * ROM_BASE and WINDOW_SHIFT come from the makefile.
 */

    .section .text
    .globl _start
_start:
    li    t0, 0xFFFF0000            /* core tag: node 3..8 for cores 0..5 */
    lw    t0, 0(t0)
    addi  t0, t0, -2                /* core k runs from window k + 1 */
    slli  t0, t0, WINDOW_SHIFT
    li    t1, ROM_BASE
    add   t0, t0, t1
    jr    t0
