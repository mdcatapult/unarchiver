package io.mdcatapult.unarchive.extractors

import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers

class GzipSpec extends TestAbstract("ingressGzip") with BeforeAndAfter with Matchers {

  val files: List[(String, Int, String)] = List[(String, Int, String)](
    ("local/test.gz", 1, "application/gzip")
  )

  files foreach  { file: (String, Int, String) => {
    val f = new Gzip(getPath(file._1))
    val nf = f.targetFile

    f"The file ${file._1}" should f"contain ${file._2} entries" in {
      val result = f.getEntries
      assert(result.nonEmpty)
      assert(result.length == file._2)
    }

    it should s"extract successfully to $nf" in {
      f.extract()

      assert(nf.exists())
      assert(nf.listFiles().length > 0)
    }
  }}

  List("local/test.RData").foreach { filename => {
    val f = new Gzip(getPath(filename))
    val nf = f.targetFile

    f"The file $filename" should f"be in list of extracted entries" in {
      f.getEntries.toList.head.getName should be ("test")
    }

    it should s"not be extracted to $nf" in {
      val extractedFilenames = f.extract()
      extractedFilenames should be (List())

      assert(nf.listFiles().isEmpty)
    }
  }}

  List("local/zero_length.gz").foreach { filename => {
    val f = new Gzip(getPath(filename))
    val nf = f.targetFile

    f"The file $filename" should f"be in list of extracted entries" in {
      f.getEntries.toList.head.getName should be ("zero_length")
    }

    it should s"not be extracted to $nf" in {
      val extractedFilenames = f.extract()
      extractedFilenames should be (List())

      assert(nf.listFiles().isEmpty)
    }
  }}

}
