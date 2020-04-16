package io.mdcatapult.unarchive.extractors

import java.io.{BufferedInputStream, FileInputStream, InputStream}
import java.util.Date

import com.typesafe.config.Config
import io.mdcatapult.doclib.loader.MagicNumberFilterInputStream
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.compressors.{CompressorInputStream, CompressorStreamFactory}
import org.apache.commons.io.FilenameUtils.removeExtension
import org.apache.commons.io.{FilenameUtils, IOUtils}

class GzipArchiveEntry(name: String, cis: CompressorInputStream) extends ArchiveEntry {

  def getName: String = name
  def getSize: Long = cis.getBytesRead
  def isDirectory: Boolean = false
  def getLastModifiedDate: Date = new Date()
}

object Gzip {

  val filterOutRData: InputStream => MagicNumberFilterInputStream =
    MagicNumberFilterInputStream.toTruncateAnyWith(List("RDX2", "RDA2").map(_.getBytes))
}

class Gzip(source: String)(implicit config: Config) extends Extractor[GzipArchiveEntry](source) {

  val input: BufferedInputStream = new BufferedInputStream(new FileInputStream(getAbsoluteFile(source)))
  val cis: CompressorInputStream = getCompressorInputStream

  def getCompressorInputStream: CompressorInputStream = new CompressorStreamFactory().createCompressorInputStream(input)

  // disable functions
  def getEntries: Iterator[GzipArchiveEntry] =
    Iterator[GzipArchiveEntry](
      new GzipArchiveEntry(
        removeExtension(FilenameUtils.getName(source)),
        cis)
    )

  def extractFile(): GzipArchiveEntry => Option[String] = _ => {
    val fileName = removeExtension(FilenameUtils.getName(source))
    val relPath = s"$targetPath/$fileName"

    writeAllContent(doclibRoot, relPath) {
      out => IOUtils.copy(Gzip.filterOutRData(cis), out)
    }
  }

}
