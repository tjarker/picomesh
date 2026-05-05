
// coreid at 0xFFFF_0000

volatile int* const CORE_ID = (volatile int*)0xFFFF0000;

volatile int* const SCRATCHPAD = (volatile int*)0x01000000;
volatile int* const BOOT_ADDR = (volatile int*)0x01000010;
volatile int* const CONFIG_REG = (volatile int*)0x01000014;

void _start(void) {
    int coreId = *CORE_ID;
    int bootAddr = *BOOT_ADDR;
    int configReg = *CONFIG_REG;
    SCRATCHPAD[0] = coreId;
    SCRATCHPAD[1] = bootAddr;
    SCRATCHPAD[2] = configReg;

    *CONFIG_REG = 1; // Set config reg to 1 to reset the core
    ((void (*)(void))bootAddr)();

    while (1) {
      // Loop forever
    }
}