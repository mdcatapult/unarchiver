package io.mdcatapult.unarchive.extractors

import java.io.File

import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfter

class GzipSpec extends TestAbstract with BeforeAndAfter{

  val files: List[(String, Int, String)] = List[(String, Int, String)](
    ("/test.gz", 1, "application/gzip")
  )

  files foreach  { file: (String, Int, String) ⇒ {
    f"The file ${file._1}" should f"contain ${file._2} entries" in {
      val result = new Gzip(getPath(file._1)).getEntries
      assert(result.nonEmpty)
      assert(result.length == file._2)
    }

    it should "extract successfully " in {
      val f = new Gzip(getPath(file._1))
      f.extract
      val target = f.getTargetPath(getPath(file._1), config.getString("unarchive.to.path"), Some("unarchived"))
      assert(new File(target).exists())
      assert(new File(target).listFiles().length > 0)
    }
  }}

//  after {
//    val t = new File(config.getString("unarchive.to.path"))
//    if (t.exists()) FileUtils.deleteQuietly(t)
//  }


}
