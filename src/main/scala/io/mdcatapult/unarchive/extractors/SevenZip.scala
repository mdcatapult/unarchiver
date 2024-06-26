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

import com.typesafe.config.Config
import org.apache.commons.compress.archivers.sevenz.{SevenZArchiveEntry, SevenZFile}

import scala.jdk.CollectionConverters._

class SevenZip(source: String)(implicit config: Config) extends Extractor[SevenZArchiveEntry](source) {

  private val sevenZFile = SevenZFile.builder().setFile(file).get()

  override def getEntries: Iterator[SevenZArchiveEntry] = sevenZFile.getEntries.iterator.asScala.filterNot(_.getSize == 0)

  override def extractFile(): SevenZArchiveEntry => Option[String] =
    (entry: SevenZArchiveEntry) => {
      sevenZFile.getNextEntry

      val content = new Array[Byte](entry.getSize.asInstanceOf[Int])
      sevenZFile.read(content, 0, content.length)

      writeAllContent(doclibRoot, targetPath.resolve(entry.getName)) {
        _.write(content)
      }
    }

  override def close(): Unit = sevenZFile.close()
}
