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

class ExtractorSpec extends TestAbstract("ingress") {

  class dummy[T](source: String) extends Extractor[T](source) {
    override def getEntries: Iterator[T] = ???
    override def extractFile(): T => Option[String] = ???
    override def close(): Unit = ()
  }

  "getTargetPath" should "return a valid path for a local path" in {
    val path = new dummy("local/cheese/stinking-bishop.cz").targetPath
    assert(path.toString == "ingress/derivatives/cheese/unarchiver_stinking-bishop.cz")
  }
  it should "return a valid path for a remote path" in {
    val path = new dummy("remote/http/phpboyscout.uk/assets/test.zip").targetPath
    assert(path.toString == "ingress/derivatives/remote/http/phpboyscout.uk/assets/unarchiver_test.zip")
  }

}
