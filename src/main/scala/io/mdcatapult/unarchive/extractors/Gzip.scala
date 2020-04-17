package io.mdcatapult.unarchive.extractors

import java.io.{BufferedInputStream, FileInputStream, InputStream}
import java.nio.file.Path
import java.util.Date

import com.typesafe.config.Config
import io.mdcatapult.doclib.loader.MagicNumberFilterInputStream
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
