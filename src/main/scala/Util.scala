import chisel3._

object Util {


  implicit class BundleExpander[T <: Bundle](x: T) {
    def expand(fs: T => Any*): Unit = {
      for (f <- fs) {
        f(x)
      }
    }
  }


  object Binary {
    def load(file: String): Seq[BigInt] = {
      val bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(file))
      bytes.grouped(4).map { group =>
        group.reverse.foldLeft(0)((acc, b) => (acc << 8) | (b & 0xFF))
      }.map(w => BigInt(w & 0xFFFFFFFFL)).toSeq
    }
  }

}


