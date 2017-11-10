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

import matryoshka._
import quasar.Planner.PlannerError
import quasar.physical.rdbms.planner.sql.SqlExpr
import slamdata.Predef._

import scalaz.\/

trait RenderQuery {
  def asString[T[_[_]]: BirecursiveT](a: T[SqlExpr]): PlannerError \/ String
}
