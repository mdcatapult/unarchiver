package io.mdcatapult.unarchive.extractors

import java.io.File

import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfter

class AutoSpec extends ExtractorSpec with BeforeAndAfter{

  val files: List[(String, Int, String)] = List[(String, Int, String)](
    // ("/test.bz2", 1, "application/x-bzip2"),
    // ("/test.laz", 1, ""),
    // ("/test.lzh", 3, "application/x-lzh-compressed"),
    // ("/test.rar", 3, "application/x-rar"),
    // ("/test-compressed.rar", 2, "application/x-rar-compressed"),
    // ("/test.skyd", 3, "application/zlib"),
    ("/test.tar", 1, "application/x-tar"),
    // ("/test.tar.bz2", 1, "application/x-bzip2"),
     ("/test.tar.gz", 2, "application/gzip"),
    // ("/test.wsz", 1, "application/zlib"),
    ("/test.xz", 4, "application/x-xz"),
    ("/test.zip", 1, "application/zip"),
  )

  files foreach  { file: (String, Int, String) ⇒ {
    f"The file ${file._1}" should f"contain ${file._2} entries" in {
      val result = new Auto(getPath(file._1)).getEntries
      assert(result.nonEmpty)
      assert(result.length == file._2)
    }

    it should "extract successfully " in {
      val f = new Auto(getPath(file._1))
      f.extract
      val target = f.getTargetPath(getPath(file._1), config.getString("unarchive.to.path"))
      assert(new File(target).exists())
      assert(new File(target).listFiles().length > 0)
    }
  }}


  after {
    val t = new File(config.getString("unarchive.to.path"))
    if (t.exists()) FileUtils.deleteQuietly(t)
  }


}
