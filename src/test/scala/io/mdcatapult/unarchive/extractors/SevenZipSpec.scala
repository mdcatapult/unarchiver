package io.mdcatapult.unarchive.extractors

import java.io.File

import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfter

class SevenZipSpec extends TestAbstract with BeforeAndAfter{



  val files: List[(String, Int)] = List[(String, Int)](
    ("local/test.7z", 1)
  )

  files foreach  { file: (String, Int) ⇒ {
      f"The file ${file._1}" should f"contain ${file._2} entries" in {
        val result = new SevenZip(getPath(file._1)).getEntries
        assert(result.nonEmpty)
        assert(result.length == file._2)
      }

      it should "extract successfully" in {
        val f = new SevenZip(getPath(file._1))
        f.extract
        val target = f.getTargetPath(getPath(file._1), config.getString("unarchive.to.path"),Some("unarchived"))
        val nf = new File(f.getAbsPath(target))
        assert(nf.exists())
        assert(nf.listFiles().length > 0)
      }
    }
  }


//  after {
//    val t = new File(config.getString("unarchive.to.path"))
//    if (t.exists()) FileUtils.deleteQuietly(t)
//  }

}
