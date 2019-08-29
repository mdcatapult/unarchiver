package io.mdcatapult.unarchive.extractors

import java.io.{BufferedInputStream, File, FileInputStream, FileOutputStream}

import com.typesafe.config.Config
import org.apache.commons.compress.archivers.{ArchiveEntry, ArchiveInputStream, ArchiveStreamFactory}
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.io.IOUtils

import scala.util.{Failure, Success, Try}

class Auto(source: String)(implicit config: Config) extends Extractor[ArchiveEntry](source) {

  val input: BufferedInputStream = new BufferedInputStream(new FileInputStream(source))
  val ais: ArchiveInputStream = getArchiveInputStream

  def getArchiveInputStream: ArchiveInputStream =
    new ArchiveStreamFactory().createArchiveInputStream(
      Try (new CompressorStreamFactory().createCompressorInputStream(input) ) match {
        case Success (cs) ⇒ new BufferedInputStream(cs)
        case Failure (_) ⇒ input
      })

  def getEntries: Iterator[ArchiveEntry] = {
    Iterator.continually(ais.getNextEntry)
      .takeWhile(ais.canReadEntryData)
      .filterNot(_.isDirectory)
  }

  def extractFile: ArchiveEntry ⇒ String = (entry: ArchiveEntry) ⇒ {
    val target = new File(s"$targetPath/${entry.getName}")
    target.getParentFile.mkdirs()
    val ois = new FileOutputStream(target)
    IOUtils.copy(ais, ois)
    ois.flush()
    ois.close()
    target.getPath
  }

}
