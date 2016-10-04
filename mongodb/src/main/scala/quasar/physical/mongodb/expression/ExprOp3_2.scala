/*
 * Copyright 2014–2016 SlamData Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package quasar.physical.mongodb.expression

import quasar.Predef._
import quasar._, Planner._
import quasar.fp._
import quasar.physical.mongodb.Bson
import quasar.jscore, jscore.JsFn

import matryoshka._
import scalaz._, Scalaz._

/** "Pipeline" operators added in MongoDB version 3.2. */
trait ExprOp3_2F[A]
object ExprOp3_2F {
  final case class $sqrtF[A](value: A)         extends ExprOp3_2F[A]
  final case class $absF[A](value: A)          extends ExprOp3_2F[A]
  final case class $logF[A](value: A, base: A) extends ExprOp3_2F[A]
  final case class $log10F[A](value: A)        extends ExprOp3_2F[A]
  final case class $lnF[A](value: A)           extends ExprOp3_2F[A]
  final case class $powF[A](value: A, exp: A)  extends ExprOp3_2F[A]
  final case class $expF[A](value: A)          extends ExprOp3_2F[A]
  final case class $truncF[A](value: A)        extends ExprOp3_2F[A]
  final case class $ceilF[A](value: A)         extends ExprOp3_2F[A]
  final case class $floorF[A](value: A)        extends ExprOp3_2F[A]

  // TODO: if this is needed, comment explaining why
  def unapply[EX[_], A](ex: EX[A])(implicit I: ExprOp3_2F :<: EX): Option[ExprOp3_2F[A]] =
    I.prj(ex)

  implicit val equal: Delay[Equal, ExprOp3_2F] =
    new Delay[Equal, ExprOp3_2F] {
      def apply[A](eq: Equal[A]) = {
        implicit val EQ = eq
        Equal.equal {
          case ($sqrtF(v1), $sqrtF(v2))       => v1 ≟ v2
          case ($absF(v1), $absF(v2))         => v1 ≟ v2
          case ($logF(v1, b1), $logF(v2, b2)) => (v1 ≟ v2) && (b1 ≟ b2)
          case ($log10F(v1), $log10F(v2))     => v1 ≟ v2
          case ($lnF(v1), $lnF(v2))           => v1 ≟ v2
          case ($powF(v1, e1), $powF(v2, e2)) => (v1 ≟ v2) && (e1 ≟ e2)
          case ($truncF(v1), $truncF(v2))     => v1 ≟ v2
          case ($ceilF(v1), $ceilF(v2))       => v1 ≟ v2
          case ($floorF(v1), $floorF(v2))     => v1 ≟ v2
          case _ => false
        }
      }
    }

  implicit val traverse: Traverse[ExprOp3_2F] = new Traverse[ExprOp3_2F] {
    def traverseImpl[G[_], A, B](fa: ExprOp3_2F[A])(f: A => G[B])(implicit G: Applicative[G]):
        G[ExprOp3_2F[B]] =
      fa match {
        case $sqrtF(v)      => G.map(f(v))($sqrtF(_))
        case $absF(v)       => G.map(f(v))($absF(_))
        case $logF(v, base) => (f(v) |@| f(base))($logF(_, _))
        case $log10F(v)     => G.map(f(v))($log10F(_))
        case $lnF(v)        => G.map(f(v))($lnF(_))
        case $powF(v, exp)  => (f(v) |@| f(exp))($powF(_, _))
        case $expF(v)       => G.map(f(v))($expF(_))
        case $truncF(v)     => G.map(f(v))($truncF(_))
        case $ceilF(v)      => G.map(f(v))($ceilF(_))
        case $floorF(v)     => G.map(f(v))($floorF(_))
      }
  }

  implicit def ops[F[_]: Functor](implicit I: ExprOp3_2F :<: F): ExprOpOps.Aux[ExprOp3_2F, F] = new ExprOpOps[ExprOp3_2F] {
    type OUT[A] = F[A]

    val simplify: AlgebraM[Option, ExprOp3_2F, Fix[F]] = κ(None)

    def bson: Algebra[ExprOp3_2F, Bson] = {
      case $sqrtF(value)      => Bson.Doc("$sqrt" -> value)
      case $absF(value)       => Bson.Doc("$abs" -> value)
      case $logF(value, base) => Bson.Doc("$log" -> Bson.Arr(value, base))
      case $log10F(value)     => Bson.Doc("$log10" -> value)
      case $lnF(value)        => Bson.Doc("$ln" -> value)
      case $powF(value, exp)  => Bson.Doc("$pow" -> Bson.Arr(value, exp))
      case $truncF(value)     => Bson.Doc("$trunc" -> value)
      case $ceilF(value)      => Bson.Doc("$ceil" -> value)
      case $floorF(value)     => Bson.Doc("$floor" -> value)
    }

    // FIXME: Define a proper `Show[ExprOp3_0F]` instance.
    @SuppressWarnings(Array("org.wartremover.warts.ToString"))
    def toJsSimple: AlgebraM[PlannerError \/ ?, ExprOp3_2F, JsFn] =
      // TODO: it's not clear that this will be needed prior to swtiching to the QScript backend
      expr => UnsupportedJS(expr.toString).left

    def rewriteRefs0(applyVar: PartialFunction[DocVar, DocVar]) = κ(None)
  }

  final case class fixpoint[T[_[_]]: Corecursive, EX[_]: Functor](implicit I: ExprOp3_2F :<: EX) {
    @inline private implicit def convert(expr: ExprOp3_2F[T[EX]]): T[EX] =
      I.inj(expr).embed

    def $sqrt(value: T[EX]): T[EX]             = $sqrtF(value)
    def $abs(value: T[EX]): T[EX]              = $absF(value)
    def $log(value: T[EX], base: T[EX]): T[EX] = $logF(value, base)
    def $log10(value: T[EX]): T[EX]            = $log10F(value)
    def $ln(value: T[EX]): T[EX]               = $lnF(value)
    def $pow(value: T[EX], exp: T[EX]): T[EX]  = $powF(value, exp)
    def $exp(value: T[EX]): T[EX]              = $expF(value)
    def $trunc(value: T[EX]): T[EX]            = $truncF(value)
    def $ceil(value: T[EX]): T[EX]             = $ceilF(value)
    def $floor(value: T[EX]): T[EX]            = $floorF(value)
  }
}

object $sqrtF {
  def apply[EX[_], A](value: A)(implicit I: ExprOp3_2F :<: EX): EX[A] =
    I.inj(ExprOp3_2F.$sqrtF(value))
  def unapply[EX[_], A](expr: EX[A])(implicit I: ExprOp3_2F :<: EX): Option[A] =
    I.prj(expr) collect {
      case ExprOp3_2F.$sqrtF(value) => (value)
    }
}

object $absF {
  def apply[EX[_], A](value: A)(implicit I: ExprOp3_2F :<: EX): EX[A] =
    I.inj(ExprOp3_2F.$absF(value))
  def unapply[EX[_], A](expr: EX[A])(implicit I: ExprOp3_2F :<: EX): Option[A] =
    I.prj(expr) collect {
      case ExprOp3_2F.$absF(value) => (value)
    }
}

object $logF {
  def apply[EX[_], A](value: A, base: A)(implicit I: ExprOp3_2F :<: EX): EX[A] =
    I.inj(ExprOp3_2F.$logF(value, base))
  def unapply[EX[_], A](expr: EX[A])(implicit I: ExprOp3_2F :<: EX): Option[(A, A)] =
    I.prj(expr) collect {
      case ExprOp3_2F.$logF(value, base) => (value, base)
    }
}

object $log10F {
  def apply[EX[_], A](value: A)(implicit I: ExprOp3_2F :<: EX): EX[A] =
    I.inj(ExprOp3_2F.$log10F(value))
  def unapply[EX[_], A](expr: EX[A])(implicit I: ExprOp3_2F :<: EX): Option[A] =
    I.prj(expr) collect {
      case ExprOp3_2F.$log10F(value) => (value)
    }
}

object $lnF {
  def apply[EX[_], A](value: A)(implicit I: ExprOp3_2F :<: EX): EX[A] =
    I.inj(ExprOp3_2F.$lnF(value))
  def unapply[EX[_], A](expr: EX[A])(implicit I: ExprOp3_2F :<: EX): Option[A] =
    I.prj(expr) collect {
      case ExprOp3_2F.$lnF(value) => (value)
    }
}

object $powF {
  def apply[EX[_], A](value: A, exp: A)(implicit I: ExprOp3_2F :<: EX): EX[A] =
    I.inj(ExprOp3_2F.$powF(value, exp))
  def unapply[EX[_], A](expr: EX[A])(implicit I: ExprOp3_2F :<: EX): Option[(A, A)] =
    I.prj(expr) collect {
      case ExprOp3_2F.$powF(value, base) => (value, base)
    }
}

object $expF {
  def apply[EX[_], A](value: A)(implicit I: ExprOp3_2F :<: EX): EX[A] =
    I.inj(ExprOp3_2F.$expF(value))
  def unapply[EX[_], A](expr: EX[A])(implicit I: ExprOp3_2F :<: EX): Option[A] =
    I.prj(expr) collect {
      case ExprOp3_2F.$expF(value) => (value)
    }
}

object $truncF {
  def apply[EX[_], A](value: A)(implicit I: ExprOp3_2F :<: EX): EX[A] =
    I.inj(ExprOp3_2F.$truncF(value))
  def unapply[EX[_], A](expr: EX[A])(implicit I: ExprOp3_2F :<: EX): Option[A] =
    I.prj(expr) collect {
      case ExprOp3_2F.$truncF(value) => (value)
    }
}

object $ceilF {
  def apply[EX[_], A](value: A)(implicit I: ExprOp3_2F :<: EX): EX[A] =
    I.inj(ExprOp3_2F.$ceilF(value))
  def unapply[EX[_], A](expr: EX[A])(implicit I: ExprOp3_2F :<: EX): Option[A] =
    I.prj(expr) collect {
      case ExprOp3_2F.$ceilF(value) => (value)
    }
}

object $floorF {
  def apply[EX[_], A](value: A)(implicit I: ExprOp3_2F :<: EX): EX[A] =
    I.inj(ExprOp3_2F.$floorF(value))
  def unapply[EX[_], A](expr: EX[A])(implicit I: ExprOp3_2F :<: EX): Option[A] =
    I.prj(expr) collect {
      case ExprOp3_2F.$floorF(value) => (value)
    }
}

object $sqrt {
  def unapply[T[_[_]]: Recursive, EX[_]: Functor](expr: T[EX])(implicit I: ExprOp3_2F :<: EX): Option[T[EX]] =
    $sqrtF.unapply(Recursive[T].project(expr))
}
object $abs {
  def unapply[T[_[_]]: Recursive, EX[_]: Functor](expr: T[EX])(implicit I: ExprOp3_2F :<: EX): Option[T[EX]] =
    $absF.unapply(Recursive[T].project(expr))
}
object $log {
  def unapply[T[_[_]]: Recursive, EX[_]: Functor](expr: T[EX])(implicit I: ExprOp3_2F :<: EX): Option[(T[EX], T[EX])] =
    $logF.unapply(Recursive[T].project(expr))
}
object $log10 {
  def unapply[T[_[_]]: Recursive, EX[_]: Functor](expr: T[EX])(implicit I: ExprOp3_2F :<: EX): Option[T[EX]] =
    $log10F.unapply(Recursive[T].project(expr))
}
object $ln {
  def unapply[T[_[_]]: Recursive, EX[_]: Functor](expr: T[EX])(implicit I: ExprOp3_2F :<: EX): Option[T[EX]] =
    $lnF.unapply(Recursive[T].project(expr))
}
object $pow {
  def unapply[T[_[_]]: Recursive, EX[_]: Functor](expr: T[EX])(implicit I: ExprOp3_2F :<: EX): Option[(T[EX], T[EX])] =
    $powF.unapply(Recursive[T].project(expr))
}
object $exp {
  def unapply[T[_[_]]: Recursive, EX[_]: Functor](expr: T[EX])(implicit I: ExprOp3_2F :<: EX): Option[T[EX]] =
    $expF.unapply(Recursive[T].project(expr))
}
object $trunc {
  def unapply[T[_[_]]: Recursive, EX[_]: Functor](expr: T[EX])(implicit I: ExprOp3_2F :<: EX): Option[T[EX]] =
    $truncF.unapply(Recursive[T].project(expr))
}
object $ceil {
  def unapply[T[_[_]]: Recursive, EX[_]: Functor](expr: T[EX])(implicit I: ExprOp3_2F :<: EX): Option[T[EX]] =
    $ceilF.unapply(Recursive[T].project(expr))
}
object $floor {
  def unapply[T[_[_]]: Recursive, EX[_]: Functor](expr: T[EX])(implicit I: ExprOp3_2F :<: EX): Option[T[EX]] =
    $floorF.unapply(Recursive[T].project(expr))
}