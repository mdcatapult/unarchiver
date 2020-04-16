package io.mdcatapult.unarchive

import java.io.{File, FileOutputStream, OutputStream}
import java.nio.file.Paths

package object extractors {

  def absoluteFile(doclibRoot: String, relativePath: String): File =
    Paths.get(doclibRoot, relativePath).toAbsolutePath.toFile

  def writeAllContent(doclibRoot: String, relativePath: String)(writer: OutputStream => Unit): Option[String] = {
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
      Option(relativePath)
  }

}
