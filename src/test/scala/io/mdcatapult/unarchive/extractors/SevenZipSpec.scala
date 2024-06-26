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

import org.scalatest.BeforeAndAfter

class SevenZipSpec extends TestAbstract("ingress7Zip") with BeforeAndAfter {

  val files: List[(String, Int)] = List("local/test.7z" -> 1)

  files foreach  { file: (String, Int) => {
    val f = new SevenZip(getPath(file._1))
    val nf = f.targetFile

    s"The file ${file._1}" should f"contain ${file._2} entries" in {
      val result = new SevenZip(getPath(file._1)).getEntries

      assert(result.nonEmpty)
      assert(result.length == file._2)
    }

    it should f"extract successfully to $nf" in {
      f.extract()

      assert(nf.exists())
      assert(nf.listFiles().length > 0)
    }
  }}

  "A zero length file" should "not be extracted" in  {
    val result = new SevenZip(getPath("local/zero_length.7z")).getEntries
    assert(result.isEmpty)
  }

}
