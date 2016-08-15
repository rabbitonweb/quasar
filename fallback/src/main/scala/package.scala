import scalaz._

import quasar.{ precog => p }

package object blueeyes extends p.PackageTime with p.PackageAliases with p.PackageMethods {
  // Temporary
  type BitSet             = quasar.precog.BitSet
  type JobId              = String
  type ByteBufferPoolS[A] = State[com.precog.util.ByteBufferPool -> List[ByteBuffer], A]

  val HNil = shapeless.HNil
  val Iso  = shapeless.Generic

  implicit def comparableOrder[A <: Comparable[A]] : ScalazOrder[A] =
    scalaz.Order.order[A]((x, y) => ScalazOrdering.fromInt(x compareTo y))

  implicit def ValidationFlatMapRequested[E, A](d: Validation[E, A]): ValidationFlatMap[E, A] =
    Validation.FlatMap.ValidationFlatMapRequested[E, A](d)

  implicit def bigDecimalOrder: scalaz.Order[blueeyes.BigDecimal] =
    scalaz.Order.order((x, y) => Ordering.fromInt(x compare y))

  implicit class ScalaSeqOps[A](xs: scala.collection.Seq[A]) {
    def sortMe(implicit z: scalaz.Order[A]): Vector[A] =
      xs sortWith ((a, b) => z.order(a, b) == Ordering.LT) toVector
  }

  implicit class QuasarAnyOps[A](private val x: A) extends AnyVal {
    def |>[B](f: A => B): B = f(x)
    def unsafeTap(f: A => Any): A = doto(x)(f)
  }

  implicit class LazyMapValues[A, B](source: Map[A, B]) {
    def lazyMapValues[C](f: B => C): Map[A, C] = new LazyMap[A, B, C](source, f)
  }

  implicit def bitSetOps(bs: BitSet): BitSetUtil.BitSetOperations = new BitSetUtil.BitSetOperations(bs)
}
