ENTRY(_start)

SECTIONS {
  . = 0x1FFFFC00;

  .text : {
    KEEP(*(.text._start))
    *(.text*)
  }

  .rodata : {
    *(.rodata*)
  }

  .data : {
    *(.data*)
  }

  .bss : {
    *(.bss*)
    *(COMMON)
  }
}