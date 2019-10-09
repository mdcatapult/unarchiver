package io.mdcatapult.unarchive.extractors

import java.nio.file.Paths

import com.typesafe.config.Config

abstract class Extractor[ArchiveEntry](source: String)(implicit config: Config) {


  lazy val targetPath: String = getTargetPath(source, config.getString("unarchive.to.path"), Some("unarchived"))
  val doclibRoot: String = s"${config.getString("doclib.root").replaceFirst("""/+$""", "")}/"

  def getAbsPath(path: String): String = {
    Paths.get(doclibRoot, path).toAbsolutePath.toString
  }

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
      case regex(path, file) ⇒
        val c = commonPath(List(targetRoot, path))
        val scrubbed = path.replaceAll(s"^$c", "").replaceAll("^/+|/+$", "")
        val targetPath = scrubbed match {
          case path if path.startsWith(config.getString("doclib.local.target-dir")) => path.replaceFirst(s"^${config.getString("doclib.local.target-dir")}/*", "")
          case path if path.startsWith(config.getString("doclib.remote.target-dir")) => path
        }
        Paths.get(config.getString("doclib.local.target-dir"), targetRoot, targetPath, s"${prefix.getOrElse("")}_$file").toString
      case _ ⇒ source
    }
  }


  def getEntries: Iterator[ArchiveEntry]
  def extractFile: ArchiveEntry ⇒ String
  def extract: List[String] = getEntries.map(extractFile).toList


}
