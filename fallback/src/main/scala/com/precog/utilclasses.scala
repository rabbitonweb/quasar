package com.precog.util

import blueeyes._, json._, serialization._
import DefaultSerialization._, Extractor._
import scala.{ collection => sc }
import scalaz._, Ordering._
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.Builder

// Once we move to 2.10, we can abstract this to a specialized-list. In 2.9,
// specialization is just too buggy to get it working (tried).

sealed trait IntList extends sc.LinearSeq[Int] with sc.LinearSeqOptimized[Int, IntList] { self =>
  def head: Int
  def tail: IntList

  def ::(head: Int): IntList = IntCons(head, this)

  override def foreach[@specialized B](f: Int => B): Unit = {
    @tailrec def loop(xs: IntList): Unit = xs match {
      case IntCons(h, t) => f(h); loop(t)
      case _             =>
    }
    loop(this)
  }

  override def apply(idx: Int): Int = {
    @tailrec def loop(xs: IntList, row: Int): Int = xs match {
      case IntCons(x, xs0) =>
        if (row == idx) x else loop(xs0, row + 1)
      case IntNil =>
        throw new IndexOutOfBoundsException("%d is larger than the IntList")
    }
    loop(this, 0)
  }

  override def length: Int = {
    @tailrec def loop(xs: IntList, len: Int): Int = xs match {
      case IntCons(x, xs0) => loop(xs0, len + 1)
      case IntNil          => len
    }
    loop(this, 0)
  }

  override def iterator: Iterator[Int] = new Iterator[Int] {
    private var xs: IntList = self
    def hasNext: Boolean = xs != IntNil
    def next(): Int = {
      val result = xs.head
      xs = xs.tail
      result
    }
  }

  override def reverse: IntList = {
    @tailrec def loop(xs: IntList, ys: IntList): IntList = xs match {
      case IntCons(x, xs0) => loop(xs0, x :: ys)
      case IntNil          => ys
    }
    loop(this, IntNil)
  }

  override protected def newBuilder = new IntListBuilder
}

final case class IntCons(override val head: Int, override val tail: IntList) extends IntList {
  override def isEmpty: Boolean = false
}

final case object IntNil extends IntList {
  override def head: Int        = sys.error("no head on empty IntList")
  override def tail: IntList    = IntNil
  override def isEmpty: Boolean = true
}

final class IntListBuilder extends Builder[Int, IntList] {
  private var xs: IntList = IntNil
  def +=(x: Int) = { xs = x :: xs; this }
  def clear() { xs = IntNil }
  def result() = xs.reverse
}

object IntList {
  implicit def cbf = new CanBuildFrom[IntList, Int, IntList] {
    def apply(): Builder[Int, IntList]              = new IntListBuilder
    def apply(from: IntList): Builder[Int, IntList] = apply()
  }
}

/**
  * Implicit container trait
  */
trait MapUtils {
  implicit def pimpMapUtils[A, B, CC[B] <: Traversable[B]](self: scMap[A, CC[B]]): MapPimp[A, B, CC] =
    new MapPimp(self)
}

class MapPimp[A, B, CC[B] <: Traversable[B]](left: scMap[A, CC[B]]) {
  def cogroup[C, CC2[C] <: Traversable[C], Result](right: scMap[A, CC2[C]])(
      implicit cbf: CanBuildFrom[Nothing, (A, Either3[B, (CC[B], CC2[C]), C]), Result],
      cbfLeft: CanBuildFrom[CC[B], B, CC[B]],
      cbfRight: CanBuildFrom[CC2[C], C, CC2[C]]): Result = {
    val resultBuilder = cbf()

    left foreach {
      case (key, leftValues) => {
        right get key map { rightValues =>
          resultBuilder += (key -> Either3.middle3[B, (CC[B], CC2[C]), C]((leftValues, rightValues)))
        } getOrElse {
          leftValues foreach { b =>
            resultBuilder += (key -> Either3.left3[B, (CC[B], CC2[C]), C](b))
          }
        }
      }
    }

    right foreach {
      case (key, rightValues) => {
        if (!(left get key isDefined)) {
          rightValues foreach { c =>
            resultBuilder += (key -> Either3.right3[B, (CC[B], CC2[C]), C](c))
          }
        }
      }
    }

    resultBuilder.result()
  }
}



object NumericComparisons {

  @inline def compare(a: Long, b: Long): Int = if (a < b) -1 else if (a == b) 0 else 1

  @inline def compare(a: Long, b: Double): Int = -compare(b, a)

  @inline def compare(a: Long, b: BigDecimal): Int = BigDecimal(a) compare b

  def compare(a: Double, bl: Long): Int = {
    val b = bl.toDouble
    if (b.toLong == bl) {
      if (a < b) -1 else if (a == b) 0 else 1
    } else {
      val error = math.abs(b * 2.220446049250313E-16)
      if (a < b - error) -1 else if (a > b + error) 1 else bl.signum
    }
  }

  @inline def compare(a: Double, b: Double): Int = if (a < b) -1 else if (a == b) 0 else 1

  @inline def compare(a: Double, b: BigDecimal): Int = BigDecimal(a) compare b

  @inline def compare(a: BigDecimal, b: Long): Int = a compare BigDecimal(b)

  @inline def compare(a: BigDecimal, b: Double): Int = a compare BigDecimal(b)

  @inline def compare(a: BigDecimal, b: BigDecimal): Int = a compare b

  @inline def compare(a: DateTime, b: DateTime): Int = {
    val res: Int = a compareTo b
    if (res < 0) -1
    else if (res > 0) 1
    else 0
  }

  @inline def eps(b: Double): Double = math.abs(b * 2.220446049250313E-16)

  def approxCompare(a: Double, b: Double): Int = {
    val aError = eps(a)
    val bError = eps(b)
    if (a + aError < b - bError) -1 else if (a - aError > b + bError) 1 else 0
  }

  @inline def order(a: Long, b: Long): scalaz.Ordering =
    if (a < b) LT else if (a == b) EQ else GT

  @inline def order(a: Double, b: Double): scalaz.Ordering =
    if (a < b) LT else if (a == b) EQ else GT

  @inline def order(a: Long, b: Double): scalaz.Ordering =
    scalaz.Ordering.fromInt(compare(a, b))

  @inline def order(a: Double, b: Long): scalaz.Ordering =
    scalaz.Ordering.fromInt(compare(a, b))

  @inline def order(a: Long, b: BigDecimal): scalaz.Ordering =
    scalaz.Ordering.fromInt(compare(a, b))

  @inline def order(a: Double, b: BigDecimal): scalaz.Ordering =
    scalaz.Ordering.fromInt(compare(a, b))

  @inline def order(a: BigDecimal, b: Long): scalaz.Ordering =
    scalaz.Ordering.fromInt(compare(a, b))

  @inline def order(a: BigDecimal, b: Double): scalaz.Ordering =
    scalaz.Ordering.fromInt(compare(a, b))

  @inline def order(a: BigDecimal, b: BigDecimal): scalaz.Ordering =
    scalaz.Ordering.fromInt(compare(a, b))

  @inline def order(a: DateTime, b: DateTime): scalaz.Ordering =
    scalaz.Ordering.fromInt(compare(a, b))
}


/**
  * This class exists as a replacement for Unit in Unit-returning functions.
  * The main issue with unit is that the coercion of any return value to
  * unit means that we were sometimes masking mis-returns of functions. In
  * particular, functions returning IO[Unit] would happily coerce IO => Unit,
  * which essentially discarded the inner IO work.
  */
sealed trait PrecogUnit

object PrecogUnit extends PrecogUnit {
  implicit def liftUnit(unit: Unit): PrecogUnit = this
}

/**
  * Unchecked and unboxed (fast!) deque implementation with a fixed bound.  None
  * of the operations on this datastructure are checked for bounds.  You are
  * trusted to get this right on your own.  If you do something weird, you could
  * end up overwriting data, reading old results, etc.  Don't do that.
  *
  * No objects were allocated in the making of this film.
  */
final class RingDeque[@specialized(Boolean, Int, Long, Double, Float, Short) A: CTag](_bound: Int) {
  val bound = _bound + 1

  private val ring = new Array[A](bound)
  private var front = 0
  private var back  = rotate(front, 1)

  def isEmpty = front == rotate(back, -1)

  def empty() {
    back = rotate(front, 1)
  }

  def popFront(): A = {
    val result = ring(front)
    moveFront(1)
    result
  }

  def pushFront(a: A) {
    moveFront(-1)
    ring(front) = a
  }

  def popBack(): A = {
    moveBack(-1)
    ring(rotate(back, -1))
  }

  def pushBack(a: A) {
    ring(rotate(back, -1)) = a
    moveBack(1)
  }

  def removeBack(length: Int) {
    moveBack(-length)
  }

  def length: Int =
    (if (back > front) back - front else (back + bound) - front) - 1

  def toList: List[A] = {
    @inline
    @tailrec
    def buildList(i: Int, accum: List[A]): List[A] =
      if (i < front)
        accum
      else
        buildList(i - 1, ring(i % bound) :: accum)

    buildList(front + length - 1, Nil)
  }

  @inline
  private[this] def rotate(target: Int, delta: Int) =
    (target + delta + bound) % bound

  @inline
  private[this] def moveFront(delta: Int) {
    front = rotate(front, delta)
  }

  @inline
  private[this] def moveBack(delta: Int) {
    back = rotate(back, delta)
  }
}




case class VectorClock(map: Map[Int, Int]) {
  def get(producerId: Int): Option[Int] = map.get(producerId)

  def update(producerId: Int, sequenceId: Int): VectorClock =
    if (map.get(producerId) forall { _ <= sequenceId }) {
      VectorClock(map + (producerId -> sequenceId))
    } else {
      this
    }

  def isDominatedBy(other: VectorClock): Boolean = map forall {
    case (prodId, maxSeqId) => other.get(prodId).forall(_ >= maxSeqId)
  }
}

trait VectorClockSerialization {
  implicit val VectorClockDecomposer: Decomposer[VectorClock] = new Decomposer[VectorClock] {
    override def decompose(clock: VectorClock): JValue = clock.map.serialize
  }

  implicit val VectorClockExtractor: Extractor[VectorClock] = new Extractor[VectorClock] {
    override def validated(obj: JValue): Validation[Error, VectorClock] =
      (obj.validated[Map[Int, Int]]).map(VectorClock(_))
  }
}

object VectorClock extends VectorClockSerialization {
  def empty = apply(Map.empty)

  implicit object order extends scalaz.Order[VectorClock] {
    def order(c1: VectorClock, c2: VectorClock) =
      if (c2.isDominatedBy(c1)) {
        if (c1.isDominatedBy(c2)) EQ else GT
      } else {
        LT
      }
  }

  // Computes the maximal merge of two clocks
  implicit object semigroup extends Semigroup[VectorClock] {
    def append(c1: VectorClock, c2: => VectorClock) = {
      c2.map.foldLeft(c1) {
        case (acc, (producerId, sequenceId)) => acc.update(producerId, sequenceId)
      }
    }
  }
}

