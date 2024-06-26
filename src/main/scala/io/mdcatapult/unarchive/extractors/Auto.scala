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

import java.io.{BufferedInputStream, FileInputStream}

import com.typesafe.config.Config
import org.apache.commons.compress.archivers.{ArchiveEntry, ArchiveInputStream, ArchiveStreamFactory}
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.io.IOUtils

import scala.util.{Failure, Success, Try}

class Auto(source: String)(implicit config: Config) extends Extractor[ArchiveEntry](source) {

  private val ais: ArchiveInputStream[ArchiveEntry] = {
    val input = new BufferedInputStream(new FileInputStream(file))
    val cis =
      Try(new CompressorStreamFactory().createCompressorInputStream(input)) match {
        case Success(cs) => new BufferedInputStream(cs)
        case Failure(_) => input
      }

    new ArchiveStreamFactory().createArchiveInputStream(cis)
  }

  override def getEntries: Iterator[ArchiveEntry] = {
    Iterator.continually(ais.getNextEntry)
      .takeWhile(ais.canReadEntryData)
      .filterNot(_.isDirectory)
      .filterNot(_.getSize == 0)
  }

  override def extractFile(): ArchiveEntry => Option[String] = (entry: ArchiveEntry) => {
    val relPath = targetPath.resolve(entry.getName)

    writeAllContent(doclibRoot, relPath) {
      out => IOUtils.copy(ais, out)
    }
  }

  override def close(): Unit = ais.close()
}
