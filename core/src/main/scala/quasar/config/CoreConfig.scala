/*
 * Copyright 2014–2018 SlamData Inc.
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

package quasar.config

import slamdata.Predef._

import argonaut._, Argonaut._
import monocle.macros.Lenses
import scalaz._, Scalaz._

@Lenses final case class CoreConfig(metastore: Option[MetaStoreConfig])

object CoreConfig {
  implicit val configOps: ConfigOps[CoreConfig] = new ConfigOps[CoreConfig] {
    val name = "core"
    def metaStoreConfig = CoreConfig.metastore
    val default = MetaStoreConfig.default ∘ (ms => CoreConfig(ms.some))
  }

  implicit val codec: CodecJson[CoreConfig] =
    casecodec1(CoreConfig.apply, CoreConfig.unapply)("metastore")
}
