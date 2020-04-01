package io.mdcatapult.unarchive.extractors

import java.io.File

import org.scalatest.BeforeAndAfter

class SevenZipSpec extends TestAbstract("ingress7Zip") with BeforeAndAfter {

  val files: List[(String, Int)] = List[(String, Int)](
    ("local/test.7z", 1)
  )

  files foreach  { file: (String, Int) => {
    val f = new SevenZip(getPath(file._1))
    val target = f.getTargetPath(getPath(file._1), config.getString("unarchive.to.path"),Some("unarchived"))

    f"The file ${file._1}" should f"contain ${file._2} entries" in {
      val result = new SevenZip(getPath(file._1)).getEntries
      assert(result.nonEmpty)
      assert(result.length == file._2)
    }

    it should f"extract successfully to ${f.getAbsPath(target)}" in {
      f.extract()
      val nf = new File(f.getAbsPath(target))
      assert(nf.exists())
      assert(nf.listFiles().length > 0)
    }
    }
  }

  "A zero length file" should "not be extracted" in  {
    val result = new SevenZip(getPath("local/zero_length.7z")).getEntries
    assert(result.isEmpty)
  }

}
