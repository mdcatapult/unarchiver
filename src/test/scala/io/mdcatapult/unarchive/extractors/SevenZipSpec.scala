package io.mdcatapult.unarchive.extractors

import org.scalatest.BeforeAndAfter

class SevenZipSpec extends TestAbstract("ingress7Zip") with BeforeAndAfter {

  val files: List[(String, Int)] = List("local/test.7z" -> 1)

  files foreach  { file: (String, Int) => {
    val f = new SevenZip(getPath(file._1))
    val nf = f.targetFile

    s"The file ${file._1}" should f"contain ${file._2} entries" in {
      val result = new SevenZip(getPath(file._1)).getEntries

      assert(result.nonEmpty)
      assert(result.length == file._2)
    }

    it should f"extract successfully to $nf" in {
      f.extract()

      assert(nf.exists())
      assert(nf.listFiles().length > 0)
    }
  }}

  "A zero length file" should "not be extracted" in  {
    val result = new SevenZip(getPath("local/zero_length.7z")).getEntries
    assert(result.isEmpty)
  }

}
