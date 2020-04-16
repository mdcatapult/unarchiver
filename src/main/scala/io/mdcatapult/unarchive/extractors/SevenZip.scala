package io.mdcatapult.unarchive.extractors

import com.typesafe.config.Config
import org.apache.commons.compress.archivers.sevenz.{SevenZArchiveEntry, SevenZFile}

import scala.jdk.CollectionConverters._

class SevenZip(source: String)(implicit config: Config) extends Extractor[SevenZArchiveEntry](source) {

  val file: SevenZFile =  new SevenZFile(getAbsoluteFile(source))

  def getEntries: Iterator[SevenZArchiveEntry] = file.getEntries.iterator.asScala.filterNot(_.getSize == 0)

  def extractFile(): SevenZArchiveEntry => Option[String] = (entry: SevenZArchiveEntry) => {
    file.getNextEntry

    val content = new Array[Byte](entry.getSize.asInstanceOf[Int])
    file.read(content, 0, content.length)

    val relPath = s"$targetPath/${entry.getName}"

    writeAllContent(doclibRoot, relPath) {
      _.write(content)
    }
  }
}
