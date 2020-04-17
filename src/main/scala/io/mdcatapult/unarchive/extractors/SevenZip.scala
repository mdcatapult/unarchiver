package io.mdcatapult.unarchive.extractors

import com.typesafe.config.Config
import org.apache.commons.compress.archivers.sevenz.{SevenZArchiveEntry, SevenZFile}

import scala.jdk.CollectionConverters._

class SevenZip(source: String)(implicit config: Config) extends Extractor[SevenZArchiveEntry](source) {

  private val sevenZFile = new SevenZFile(file)

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
