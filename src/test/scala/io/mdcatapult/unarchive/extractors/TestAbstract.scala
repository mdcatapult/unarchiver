/*
 * Copyright 2024 Medicines Discovery Catapult
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

package io.mdcatapult.unarchive.extractors

import better.files.Dsl.pwd
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.util.path.DirectoryDeleter.deleteDirectories
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec

class TestAbstract(tempDir: String) extends AnyFlatSpec with BeforeAndAfterAll {

  def getPath(file: String): String = file

  implicit val config: Config = ConfigFactory.parseString(
    s"""
      |doclib {
      |  root: "./test-assets"
      |  local: {
      |    target-dir: "local"
      |    temp-dir: "$tempDir"
      |  }
      |  remote: {
      |    target-dir: "remote"
      |  }
      |  derivative {
      |    target-dir: "derivatives"
      |  }
      |}
      |consumer {
      |  name = "unarchiver"
      |}
    """.stripMargin)

  override def afterAll(): Unit = {
    // These may or may not exist but are all removed anyway
    deleteDirectories(List(pwd/"test-assets"/tempDir))
  }

}
