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

import java.io.{BufferedInputStream, FileInputStream, InputStream}
import java.nio.file.Path
import java.util.Date

import com.typesafe.config.Config
import io.mdcatapult.util.io.MagicNumberFilterInputStream
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.compressors.{CompressorInputStream, CompressorStreamFactory}
import org.apache.commons.io.FilenameUtils.removeExtension
import org.apache.commons.io.{FilenameUtils, IOUtils}

object Gzip {

  val filterOutRData: InputStream => MagicNumberFilterInputStream =
    MagicNumberFilterInputStream.toTruncateAnyWith(List("RDX2", "RDA2").map(_.getBytes))
}

class Gzip(source: String)(implicit config: Config) extends Extractor[ArchiveEntry](source) {

  private val cis: CompressorInputStream = {
    val input = new BufferedInputStream(new FileInputStream(file))
    new CompressorStreamFactory().createCompressorInputStream(input)
  }

  private class Entry(name: String) extends ArchiveEntry {
    def getName: String = name
    def getSize: Long = cis.getBytesRead
    def isDirectory: Boolean = false
    def getLastModifiedDate: Date = new Date()
  }

  // disable functions
  override def getEntries: Iterator[ArchiveEntry] = {
    val name = removeExtension(FilenameUtils.getName(source))
    Iterator(new Entry(name))
  }

  override def extractFile(): ArchiveEntry => Option[String] = _ => {
    val fileName = removeExtension(FilenameUtils.getName(source))
    val relPath = Path.of(s"$targetPath/$fileName")

    writeAllContent(doclibRoot, relPath) {
      out => IOUtils.copy(Gzip.filterOutRData(cis), out)
    }
  }

  override def close(): Unit = cis.close()
}
