/*
 * Copyright 2024 Medicines Discovery Catapult
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.mdcatapult.unarchive.extractors

import com.typesafe.config.Config
import java.io.File
import java.nio.file.{Path, Paths}
import scala.util.Try

abstract class Extractor[ArchiveEntry](source: String)(implicit config: Config) {

  private val localTargetDir = config.getString("doclib.local.target-dir")
  private val unarchiveToDir = config.getString("doclib.derivative.target-dir")
  private val tempDir = config.getString("doclib.local.temp-dir")

  val targetPath: Path = getTargetPath(source, unarchiveToDir, Try(config.getString("consumer.name")).toOption)
  val doclibRoot: Path = Path.of(s"${config.getString("doclib.root").replaceFirst("""/+$""", "")}/")

  val file: File = absoluteFile(doclibRoot, Path.of(source))
  val targetFile: File = absoluteFile(doclibRoot, targetPath)

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

    paths match {
      case List() =>
        ""
      case List(x) =>
        x
      case _ =>
        paths.map(_.split(BOUNDARY_REGEX).toList).reduceLeft(common).mkString
    }
  }

  /**
    * generate new file path maintaining file path from origin but allowing for intersection of common root paths
    * @param source String
    * @return String full path to new target
    */
  def getTargetPath(source: String, base: String, prefix: Option[String] = None): Path = {
    val targetRoot = base.replaceAll("/+$", "")
    val regex = """(.*)/(.*)$""".r

    source match {
      case regex(path, file) =>
        val c = commonPath(List(targetRoot, path))
        val scrubbedPath  = scrub(path.replaceAll(s"^$c", "").replaceAll("^/+|/+$", ""))

        Paths.get(tempDir, targetRoot, scrubbedPath, s"${prefix.getOrElse("")}_$file")

      case _ => new File(source).toPath
    }
  }

  def scrub(path: String):String = path match {
    case path if path.startsWith(localTargetDir) =>
      scrub(path.replaceFirst(s"^$localTargetDir/*", ""))
    case path if path.startsWith(unarchiveToDir)  =>
      scrub(path.replaceFirst(s"^$unarchiveToDir/*", ""))
    case _ => path
  }

  def getEntries: Iterator[ArchiveEntry]

  /** Write the contents of an archive entry into a file.  It is valid for this method to determine from the entry
    * contents that the file is to not be written, in which None is returned.
    *
    * @return some file name if the file is written or None otherwise
    */
  def extractFile(): ArchiveEntry => Option[String]

  /** Close any open resources. */
  def close(): Unit

  /** Extract all archive entries into files - one file per entry.
    *
    * @return list of file names or all written entries
    */
  def extract(): List[String] =
    try {
      getEntries.map(extractFile()).toList.flatten
    } finally {
      close()
    }
}
