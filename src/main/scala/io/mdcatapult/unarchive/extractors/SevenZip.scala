package io.mdcatapult.unarchive.extractors

import java.io.{File, FileOutputStream}

import com.typesafe.config.Config
import org.apache.commons.compress.archivers.sevenz.{SevenZArchiveEntry, SevenZFile}

import scala.collection.JavaConverters._

class SevenZip(source: String)(implicit config: Config) extends Extractor[SevenZArchiveEntry](source) {

  val file: SevenZFile =  new SevenZFile(new File(source))

  def getEntries: Iterator[SevenZArchiveEntry] = file.getEntries.iterator.asScala

  def extractFile: SevenZArchiveEntry ⇒ String = (entry: SevenZArchiveEntry) ⇒ {
    file.getNextEntry
    val content = new Array[Byte](entry.getSize.asInstanceOf[Int])
    file.read(content, 0, content.length)

    val target = new File(s"$targetPath/${entry.getName}")
    target.getParentFile.mkdirs()
    val output = new FileOutputStream(target)
    output.write(content)
    output.close()
    target.getPath
  }
}
