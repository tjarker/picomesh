// Exercises the runtime before any real benchmark: software multiply, divide
// and modulo, .rodata, .data, .bss and calls on the stack. Integer-only and
// without recursion, like the benchmarks. Returns 0 when everything computed
// correctly, like TACLe's main.

static const int table[8] = {3, 1, 4, 1, 5, 9, 2, 6};
static volatile int divisor = 7;
static int quotient;

// noipa keeps the compiler from folding the whole program into a constant
static int __attribute__((noipa)) weighted_sum(const int *t, int n) {
  int sum = 0;
  for (int i = 0; i < n; i++) {
    sum += t[i] * (i + 1);
  }
  return sum;
}

static int __attribute__((noipa)) fact(int n) {
  int f = 1;
  for (int i = 2; i <= n; i++) {
    f *= i;
  }
  return f;
}

int main(void) {
  int sum = weighted_sum(table, 8); // 162
  quotient = sum / divisor;         // 23
  int remainder = sum % divisor;    // 1

  return !(sum == 162 && quotient == 23 && remainder == 1 && fact(10) == 3628800);
}
