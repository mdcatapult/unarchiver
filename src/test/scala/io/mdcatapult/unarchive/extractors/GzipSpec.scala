package io.mdcatapult.unarchive.extractors

import java.io.File

import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfter

class GzipSpec extends TestAbstract with BeforeAndAfter{

  val files: List[(String, Int, String)] = List[(String, Int, String)](
    ("local/test.gz", 1, "application/gzip")
  )

  files foreach  { file: (String, Int, String) ⇒ {
    val f = new Gzip(getPath(file._1))
    val target = f.getTargetPath(getPath(file._1), config.getString("unarchive.to.path"), Some("unarchived"))

    f"The file ${file._1}" should f"contain ${file._2} entries" in {
      val result = f.getEntries
      assert(result.nonEmpty)
      assert(result.length == file._2)
    }

    it should s"extract successfully to ${f.getAbsPath(target)}" in {
      f.extract
      val nf = new File(f.getAbsPath(target))
      assert(nf.exists())
      assert(nf.listFiles().length > 0)
    }
  }}

//  after {
//    val t = new File(config.getString("unarchive.to.path"))
//    if (t.exists()) FileUtils.deleteQuietly(t)
//  }


}
