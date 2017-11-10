/*
 * Copyright 2014–2017 SlamData Inc.
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

package quasar.physical.rdbms.planner

import slamdata.Predef._
import quasar._
  import quasar.contrib.pathy._
import quasar.fs.FileSystemError
import quasar.Qspec
import quasar.physical.rdbms.planner.sql.SqlExpr
import quasar.qscript._
import quasar.sql._

import eu.timepit.refined.auto._
import matryoshka._
import matryoshka.data._
import matryoshka.implicits._
import org.specs2.execute.NoDetails
import org.specs2.matcher._
import pathy.Path._
import scalaz._
import Scalaz._
import scalaz.concurrent.Task

class PlannerSpec extends Qspec with SqlExprSupport {

  case class equalToRepr[L](expected: Fix[SqlExpr]) extends Matcher[\/[L, Fix[SqlExpr]]] {
    def apply[S <: \/[L, Fix[SqlExpr]]](s: Expectable[S]) = {
      val expectedTree = RenderTreeT[Fix].render(expected).right[L]
      val actualTree = s.value.map(repr => RenderTreeT[Fix].render(repr))
      val diff = (expectedTree ⊛ actualTree) {
        case (e, a) => (e diff a).shows
      }

      result(expected.right[L] == s.value,
             "\ntrees are equal:\n" + diff,
             "\ntrees are not equal:\n" + diff,
             s,
             NoDetails)
    }
  }

  def beRepr(expected: SqlExpr[Fix[SqlExpr]]) =
    equalToRepr[FileSystemError](Fix(expected))

  import SqlExpr._
  import SqlExpr.Select._

  def selection(
      v: Fix[SqlExpr],
      alias: Option[SqlExpr.Id[Fix[SqlExpr]]] = id0.some): Selection[Fix[SqlExpr]] =
    Selection[Fix[SqlExpr]](v, alias)


  def id0Token: String = "_0"
  def id0 : Id[Fix[SqlExpr]] = Id(id0Token)
  def * : Fix[SqlExpr] = Fix(AllCols(id0Token))

  def fromTable(
      name: String,
      alias: Option[SqlExpr.Id[Fix[SqlExpr]]] = None): From[Fix[SqlExpr]] =
    From[Fix[SqlExpr]](Fix(Table(name)), alias)

  def select[T](selection: Selection[T],
                from: From[T],
                filter: Option[Filter[T]] = None) =
    Select(selection, from, filter)

  "Shifted read" should {
    type SR[A] = Const[ShiftedRead[AFile], A]

    "build plan for column wildcard" in {
      plan(sqlE"select * from foo") must
        beRepr({
          select(
            selection(*, alias = None),
            From(Fix(SelectRow(selection(*), fromTable("db.foo"))),
              alias = id0.some))
        })
    }
    def expectShiftedReadRepr(forIdStatus: IdStatus,
                              expectedRepr: SqlExpr[Fix[SqlExpr]]) = {
      val path: AFile = rootDir </> dir("db") </> file("foo")

      val qs: Fix[SR] =
        Fix(Inject[SR, SR].inj(Const(ShiftedRead(path, forIdStatus))))
      val planner = Planner.constShiftedReadFilePlanner[Fix, Task]
      val repr = qs.cataM(planner.plan).map(_.convertTo[Fix[SqlExpr]]).unsafePerformSync

      repr.right[FileSystemError] must
        beRepr(expectedRepr)
    }

    "build plan including ids" in {
      expectShiftedReadRepr(forIdStatus = IncludeId, expectedRepr = {
        SelectRow(selection(Fix(WithIds(*))), fromTable("db.foo"))
      })
    }

    "build plan only for ids" in {
      expectShiftedReadRepr(forIdStatus = IdOnly, expectedRepr = {
        SelectRow(selection(Fix(RowIds())), fromTable("db.foo"))
      })
    }

    "build plan only for excluded ids" in {
      expectShiftedReadRepr(forIdStatus = ExcludeId, expectedRepr = {
        SelectRow(selection(*), fromTable("db.foo"))
      })
    }
  }

}
