Subset of LLVM compiler-rt `lib/builtins` from tag `llvmorg-20.1.8`, unmodified,
licensed under Apache-2.0 WITH LLVM-exception (see `LICENSE.TXT`).

The toolchain has no rv32e/ilp32e multilib, so there is no libgcc for the cores.
These provide the integer multiply/divide and soft-float routines GCC calls
instead. `riscv/mulsi3.S` only uses a0-a3, so it is valid on RV32E.
