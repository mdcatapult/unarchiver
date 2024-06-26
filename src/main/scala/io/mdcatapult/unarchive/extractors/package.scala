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
