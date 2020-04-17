package io.mdcatapult.unarchive

import java.io.{File, FileOutputStream, OutputStream}
import java.nio.file.Path

package object extractors {

  def absoluteFile(doclibRoot: Path, relativePath: Path): File =
    doclibRoot.resolve(relativePath).toAbsolutePath.toFile

  def writeAllContent(doclibRoot: Path, relativePath: Path)(writer: OutputStream => Unit): Option[String] = {
    val target: File = absoluteFile(doclibRoot, relativePath)
    target.getParentFile.mkdirs()

    val out = new FileOutputStream(target)
    try {
      writer(out)
      out.flush()
    } finally {
      out.close()
    }

    if (target.length() == 0) {
      target.delete()
      None
    } else
      Option(relativePath.toString)
  }

}
