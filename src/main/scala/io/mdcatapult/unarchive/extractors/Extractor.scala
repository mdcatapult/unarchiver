package io.mdcatapult.unarchive.extractors

import java.io.File
import java.nio.file.Paths

import com.typesafe.config.Config

abstract class Extractor[ArchiveEntry](source: String)(implicit config: Config) {


  lazy val targetPath: String = getTargetPath(source, config.getString("unarchive.to.path"), Some("unarchived"))
  val doclibRoot: String = s"${config.getString("doclib.root").replaceFirst("""/+$""", "")}/"

  def getAbsoluteFile(relativePath: String): File =
    absoluteFile(doclibRoot, relativePath)

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
  def getTargetPath(source: String, base: String, prefix: Option[String] = None): String = {
    val targetRoot = base.replaceAll("/+$", "")
    val regex = """(.*)/(.*)$""".r
    source match {
      case regex(path, file) =>
        val c = commonPath(List(targetRoot, path))
        val targetPath  = scrub(path.replaceAll(s"^$c", "").replaceAll("^/+|/+$", ""))
        Paths.get(config.getString("doclib.local.temp-dir"), targetRoot, targetPath, s"${prefix.getOrElse("")}_$file").toString
      case _ => source
    }
  }

  def scrub(path: String):String  = path match {
    case path if path.startsWith(config.getString("doclib.local.target-dir")) =>
      scrub(path.replaceFirst(s"^${config.getString("doclib.local.target-dir")}/*", ""))
    case path if path.startsWith(config.getString("unarchive.to.path"))  =>
      scrub(path.replaceFirst(s"^${config.getString("unarchive.to.path")}/*", ""))
    case _ => path
  }

  def getEntries: Iterator[ArchiveEntry]

  /** Write the contents of an archive entry into a file.  It is valid for this method to determine from the entry
    * contents that the file is to not be written, in which None is returned.
    *
    * @return some file name if the file is written or None otherwise
    */
  def extractFile(): ArchiveEntry => Option[String]

  /** Extract all archive entries into files - one file per entry.
    *
    * @return list of file names or all written entries
    */
  def extract(): List[String] = getEntries.map(extractFile()).toList.flatten
}
