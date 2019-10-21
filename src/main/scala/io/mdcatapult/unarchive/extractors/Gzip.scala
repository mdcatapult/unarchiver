package io.mdcatapult.unarchive.extractors

import java.io.{BufferedInputStream, File, FileInputStream, FileOutputStream}
import java.util.Date

import com.typesafe.config.Config
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.compressors.{CompressorInputStream, CompressorStreamFactory}
import org.apache.commons.io.FilenameUtils

class GzipArchiveEntry(name: String, cis: CompressorInputStream) extends ArchiveEntry {
  val bytes: Array[Byte] = cis.readAllBytes()
  def getName: String = name
  def getSize: Long = cis.getBytesRead
  def isDirectory: Boolean = false
  def getLastModifiedDate: Date = new Date()
}

class Gzip(source: String)(implicit config: Config) extends Extractor[GzipArchiveEntry](source) {

  val input: BufferedInputStream = new BufferedInputStream(new FileInputStream(getAbsPath(source)))
  val cis: CompressorInputStream = getCompressorInputStream

  def getCompressorInputStream: CompressorInputStream = new CompressorStreamFactory().createCompressorInputStream(input)

  // disable functions
  def getEntries: Iterator[GzipArchiveEntry] = Iterator[GzipArchiveEntry](new GzipArchiveEntry(
    FilenameUtils.removeExtension(FilenameUtils.getName(source)), cis))


  def extractFile: GzipArchiveEntry ⇒ String = (a: GzipArchiveEntry) ⇒ {
    val fileName = FilenameUtils.removeExtension(FilenameUtils.getName(source))
    val relPath = s"$targetPath/$fileName"
    val target = new File(getAbsPath(relPath))
    target.getParentFile.mkdirs()
    val ois = new FileOutputStream(target)
    ois.write(a.bytes)
    ois.close()
    relPath
  }

}
