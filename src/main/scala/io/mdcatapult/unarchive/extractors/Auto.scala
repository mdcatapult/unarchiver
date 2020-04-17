package io.mdcatapult.unarchive.extractors

import java.io.{BufferedInputStream, FileInputStream}

import com.typesafe.config.Config
import org.apache.commons.compress.archivers.{ArchiveEntry, ArchiveInputStream, ArchiveStreamFactory}
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.io.IOUtils

import scala.util.{Failure, Success, Try}

class Auto(source: String)(implicit config: Config) extends Extractor[ArchiveEntry](source) {

  private val ais: ArchiveInputStream = {
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
