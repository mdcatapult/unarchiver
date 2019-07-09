package io.mdcatapult.unarchive.extractors

import com.typesafe.config.Config
import org.apache.commons.io.FilenameUtils

abstract class Extractor[ArchiveEntry](source: String)(implicit config: Config) {


  lazy val targetPath: String = getTargetPath(source, config.getString("unarchive.to.path"))

  /**
    * determines common root paths for two path string
    * @param paths List[String]
    * @return String common path component
    */
  protected def commonPath(paths: List[String]): String = {
    val SEP = "/"
    val BOUNDARY_REGEX = s"(?=[$SEP])(?<=[^$SEP])|(?=[^$SEP])(?<=[$SEP])"
    def common(a: List[String], b: List[String]): List[String] = (a, b) match {
      case (aa :: as, bb :: bs) if aa equals bb => aa :: common(as, bs)
      case _ => Nil
    }
    if (paths.length < 2) paths.headOption.getOrElse("")
    else paths.map(_.split(BOUNDARY_REGEX).toList).reduceLeft(common).mkString
  }

  /**
    * generate new file path maintaining file path from origin but allowing for intersection of common root paths
    * @param source String
    * @return String full path to new target
    */
  def getTargetPath(source: String, base: String): String = {
    val targetRoot = base.replaceAll("/+$", "")
    val sourceName = FilenameUtils.removeExtension(source)
    val c = commonPath(List(targetRoot, sourceName))
    val scrubbed = sourceName.replaceAll(s"^$c", "").replaceAll("^/+|/+$", "")
    s"$targetRoot/$scrubbed/"
  }


  def getEntries: Iterator[ArchiveEntry]
  def extractFile: ArchiveEntry ⇒ String
  def extract: List[String] = getEntries.map(extractFile).toList


}
