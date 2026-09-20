// Exercises the runtime before any real benchmark: software multiply, divide and
// modulo, .rodata, .data, .bss and calls on the stack. Integer-only and without
// recursion, like the benchmarks, and split into init/main/return so runner.c times
// it the same way. smoke_return() gives 0 when everything computed correctly.

static const int table[8] = {3, 1, 4, 1, 5, 9, 2, 6};
static volatile int divisor = 7;
static int sum;
static int quotient;
static int remainder;
static int factorial;

// noipa keeps the compiler from folding the whole program into a constant
static int __attribute__((noipa)) weighted_sum(const int *t, int n) {
  int s = 0;
  for (int i = 0; i < n; i++) {
    s += t[i] * (i + 1);
  }
  return s;
}

static int __attribute__((noipa)) fact(int n) {
  int f = 1;
  for (int i = 2; i <= n; i++) {
    f *= i;
  }
  return f;
}

void smoke_init(void) {
  sum = 0;
  quotient = 0;
  remainder = 0;
  factorial = 0;
}

void smoke_main(void) {
  sum = weighted_sum(table, 8); // 162
  quotient = sum / divisor;     // 23
  remainder = sum % divisor;    // 1
  factorial = fact(10);         // 3628800
}

int smoke_return(void) {
  return !(sum == 162 && quotient == 23 && remainder == 1 && factorial == 3628800);
}
