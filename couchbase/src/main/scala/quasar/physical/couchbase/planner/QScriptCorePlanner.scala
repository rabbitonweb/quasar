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

package quasar.physical.couchbase.planner

import quasar.Predef._
import quasar.{Data => QData, NameGenerator}
import quasar.Planner.InternalError
import quasar.contrib.matryoshka._
import quasar.ejson
import quasar.fp._
import quasar.fp.ski.κ
import quasar.physical.couchbase._, N1QL.{Id, Union, _}, Case._, Select.{Filter, Value, _}
import quasar.physical.couchbase.planner.Planner._
import quasar.qscript, qscript._

import matryoshka._, Recursive.ops._
import scalaz._, Scalaz._, NonEmptyList.nels

final class QScriptCorePlanner[T[_[_]]: Recursive: Corecursive: ShowT, F[_]: Monad: NameGenerator]
  extends Planner[T, F, QScriptCore[T, ?]] {

  def int(i: Int) = Data[T[N1QL]](QData.Int(i)).embed

  def processFreeMapDefault(f: FreeMap[T], id: Id[T[N1QL]]): M[T[N1QL]] =
    freeCataM(f)(interpretM(κ(id.embed.η[M]), mapFuncPlanner[T, F].plan))

  def processFreeMap(f: FreeMap[T], id: Id[T[N1QL]]): M[T[N1QL]] =
    f.toCoEnv[T].project match {
      case MapFunc.StaticMap(elems) =>
        elems.traverse(_.bitraverse(
          // TODO: Revisit String key requirement
          {
            case Embed(ejson.Common(ejson.Str(key))) =>
              Data[T[N1QL]](QData.Str(key)).embed.η[M]
            case key =>
              EitherT(
                InternalError.fromMsg(s"Unsupported object key: ${key.shows}")
                  .left[T[N1QL]].η[PR])
          },
          v => processFreeMapDefault(v.fromCoEnv, id)
        )) ∘ (l => Obj(l.toMap).embed)
      case _ =>
        processFreeMapDefault(f, id)
    }

  def plan: AlgebraM[M, QScriptCore[T, ?], T[N1QL]] = {
    case qscript.Map(src, f) =>
      for {
        id1 <- genId[T, M]
        ff  <- processFreeMap(f, id1)
      } yield Select(
        Value(true),
        ResultExpr(ff, none).wrapNel,
        Keyspace(wrapSelect(src), id1.some).some,
        unnest  = none,
        filter  = none,
        groupBy = none,
        orderBy = Nil).embed

    case LeftShift(src, struct, id, repair) =>
      for {
        id1 <- genId[T, M]
        id2 <- genId[T, M]
        id3 <- genId[T, M]
        s   <- freeCataM(struct)(interpretM(
                 κ(id1.embed.η[M]),
                 mapFuncPlanner[T, F].plan))
        sr  =  ArrRange(int(0), LengthArr(s).embed, none)
        u   <- (id match {
                 case IdOnly =>
                   IfNull(
                     ObjNames(s).embed,
                     sr.embed)
                 case IncludeId =>
                   Case(
                     WhenThen(IsObj(s).embed, s)
                   )(
                     Else(ArrFor(
                       expr   = Arr(List(id3.embed, SelectElem(s, id3.embed).embed)).embed,
                       `var`  = id3.embed,
                       inExpr = sr.embed).embed)
                   )
                 case ExcludeId =>
                   IfNull(ObjValues(s).embed, s)
               }).embed.η[M]
        r   <- freeCataM(repair)(interpretM(
                 {
                   case LeftSide  =>
                     id1.embed.η[M]
                   case RightSide =>
                     Select(
                       Value(true),
                       ResultExpr(id2.embed, none).wrapNel,
                       Keyspace(wrapSelect(src), id1.some).some,
                       Unnest(u, id2.some).some,
                       filter  = none,
                       groupBy = none,
                       orderBy = Nil).embed.η[M]
                 },
                 mapFuncPlanner[T, F].plan))
      } yield r

    case qscript.Reduce(src, bucket, reducers, repair) =>
      for {
        id1 <- genId[T, M]
        id2 <- genId[T, M]
        id3 <- genId[T, M]
        b   <- processFreeMap(bucket, id1)
        red =  reducers.map(
                 _.traverse(
                   red => processFreeMap(red, id1)
                 ).flatMap(reduceFuncPlanner[T, F].plan)
               )
        rep <- freeCataM(repair)(interpretM(i => red(i.idx), mapFuncPlanner[T, F].plan))
      } yield {
        val s = Select(
          Value(false),
          ResultExpr(rep, id2.some).wrapNel,
          Keyspace(src, id1.some).some,
          unnest = none,
          filter = none,
          GroupBy(b).some,
          orderBy = Nil).embed

        Select(
          Value(true),
          ResultExpr(id2.embed, none).wrapNel,
          Keyspace(s, id3.some).some,
          unnest = none,
          Filter(IsNotNull(id2.embed).embed).some,
          groupBy = none,
          orderBy = Nil).embed
      }

    case qscript.Sort(src, bucket, order) =>
      for {
        id1 <- genId[T, M]
        id2 <- genId[T, M]
        id3 <- genId[T, M]
        b   <- processFreeMap(bucket, id1)
        o   <- order.traverse { case (or, d) =>
                 (processFreeMap(or, id3) ∘ (OrderBy(_, d)))
               }
      } yield {
        val s =  Select(
           Value(true),
           ResultExpr(ArrAgg(id1.embed).embed, none).wrapNel,
           Keyspace(src, id1.some).some,
           unnest  = none,
           filter  = none,
           GroupBy(IfNull(b, id1.embed).embed).some,
           orderBy = Nil).embed

        Select(
          Value(true),
          ResultExpr(id3.embed, none).wrapNel,
          Keyspace(s, id2.some).some,
          Unnest(id2.embed, id3.some).some,
          filter  = none,
          groupBy = none,
          orderBy = o.toList).embed
      }

    case qscript.Filter(src, f) =>
      for {
        id1  <- genId[T, M]
        ff   <- processFreeMap(f, id1)
      } yield Select(
        Value(true),
        ResultExpr(id1.embed, none).wrapNel,
        Keyspace(src, id1.some).some,
        unnest  = none,
        Filter(ff).some,
        groupBy = none,
        orderBy = Nil).embed

    case qscript.Union(src, lBranch, rBranch) =>
      for {
        idLB <- genId[T, M]
        idRB <- genId[T, M]
        lb   <- freeCataM(lBranch)(interpretM(
                  κ(idLB.embed.η[M]),
                  Planner[T, F, QScriptTotal[T, ?]].plan))
        rb   <- freeCataM(rBranch)(interpretM(
                  κ(idRB.embed.η[M]),
                  Planner[T, F, QScriptTotal[T, ?]].plan))
      } yield Union(
        SelectField(src, lb).embed,
        SelectField(src, rb).embed).embed

    case qscript.Subset(src, from, op, count) => op match {
      case Drop   => takeOrDrop(src, from, count.right)
      case Take   => takeOrDrop(src, from, count.left)
      case Sample => unimplementedP("Sample")
    }

    case qscript.Unreferenced() =>
      wrapSelect(Arr[T[N1QL]](Nil).embed).η[M]
  }

  def takeOrDrop(src: T[N1QL], from: FreeQS[T], takeOrDrop: FreeQS[T] \/ FreeQS[T]): M[T[N1QL]] =
    for {
      id1 <- genId[T, M]
      id2 <- genId[T, M]
      id3 <- genId[T, M]
      id4 <- genId[T, M]
      id5 <- genId[T, M]
      f   <- freeCataM(from)(interpretM(
               κ(id1.embed.η[M]),
               Planner[T, F, QScriptTotal[T, ?]].plan))
      c   <- freeCataM(takeOrDrop.merge)(interpretM(
               κ(id1.embed.η[M]),
               Planner[T, F, QScriptTotal[T, ?]].plan))
    } yield {
      val s =
        Select(
          Value(false),
          nels(
            ResultExpr(f, id2.some),
            ResultExpr(c, id3.some)),
          Keyspace(src, id1.some).some,
          unnest  = none,
          filter  = none,
          groupBy = none,
          orderBy = Nil).embed

      val cnt = SelectElem(id3.embed, int(0)).embed

      val slc =
        takeOrDrop.bimap(
           κ(Slice(
             int(0),
             Least(LengthArr(id2.embed).embed, cnt).embed.some)),
           κ(Slice(cnt, none))
         ).merge.embed

      Select(
        Value(true),
        ResultExpr(id5.embed, none).wrapNel,
        Keyspace(s, id4.some).some,
        Unnest(SelectElem(id2.embed, slc).embed, id5.some).some,
        filter  = none,
        groupBy = none,
        orderBy = Nil).embed
    }

}
