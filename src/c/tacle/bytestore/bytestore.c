// Byte and half-word stores into remote memory. picorv32 replicates the stored value
// across all four bytes of mem_wdata and picks the target bytes with mem_wstrb, so a
// memory path that drops the strobe overwrites the neighbouring bytes of the word.
// bytestore_return() gives 0 when every store left its neighbours intact; bit 0 flags
// the byte stores, bit 1 the half-word stores.

// volatile, so the stores are not merged and the checks really read the word back
static volatile unsigned int word;
static int errors;

void bytestore_init(void) {
  errors = 0;
  word = 0;
}

void bytestore_main(void) {
  volatile unsigned char *b = (volatile unsigned char *)&word;
  volatile unsigned short *h = (volatile unsigned short *)&word;

  b[0] = 0x11;
  b[1] = 0x22;
  b[2] = 0x33;
  b[3] = 0x44;
  if (word != 0x44332211u) {
    errors |= 1;
  }

  h[0] = 0x5566;
  h[1] = 0x7788;
  if (word != 0x77885566u) {
    errors |= 2;
  }
}

int bytestore_return(void) {
  return errors;
}
