/* One core's image: .text in its program ROM window, everything else in its SRAM
   window with the stack at the top. TEXT_BASE, DATA_BASE, WINDOW and STACK_RESERVE
   are given with --defsym. */

OUTPUT_ARCH(riscv)
ENTRY(_start)

SECTIONS
{
  .text TEXT_BASE : {
    KEEP(*(.text._start))
    *(.text .text.*)
  }
  ASSERT(. <= TEXT_BASE + WINDOW, "text does not fit its program ROM window")

  /* the marker makes the data image start at DATA_BASE even without .rodata */
  .rodata DATA_BASE : {
    LONG(0x7AC1EDA7)
    *(.rodata .rodata.* .srodata .srodata.*)
  }
  .data : ALIGN(4) {
    *(.data .data.* .sdata .sdata.*)
  }
  .bss (NOLOAD) : ALIGN(4) {
    __bss_start = .;
    *(.bss .bss.* .sbss .sbss.* COMMON)
    . = ALIGN(4);
    __bss_end = .;
  }
  __stack_top = DATA_BASE + WINDOW;
  ASSERT(__bss_end + STACK_RESERVE <= __stack_top, "data and stack reserve do not fit the SRAM window")

  /DISCARD/ : { *(.eh_frame .eh_frame_hdr .comment .note .note.*) }
}
