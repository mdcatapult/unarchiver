package io.mdcatapult.unarchive.extractors

import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers

class GzipSpec extends TestAbstract("ingressGzip") with BeforeAndAfter with Matchers {

  val files: List[(String, Int, String)] = List[(String, Int, String)](
    ("local/test.gz", 1, "application/gzip")
  )

  files foreach  { file: (String, Int, String) => {
    val f = new Gzip(getPath(file._1))
    val target = f.getTargetPath(getPath(file._1), config.getString("unarchive.to.path"), Some("unarchived"))

    f"The file ${file._1}" should f"contain ${file._2} entries" in {
      val result = f.getEntries
      assert(result.nonEmpty)
      assert(result.length == file._2)
    }

    it should s"extract successfully to ${f.getAbsoluteFile(target)}" in {
      f.extract()
      val nf = f.getAbsoluteFile(target)

      assert(nf.exists())
      assert(nf.listFiles().length > 0)
    }
  }}

  List("local/test.RData").foreach { filename => {
    val f = new Gzip(getPath(filename))
    val target = f.getTargetPath(getPath(filename), config.getString("unarchive.to.path"), Some("unarchived"))

    f"The file $filename" should f"be in list of extracted entries" in {
      f.getEntries.toList.head.getName should be ("test")
    }

    it should s"not be extracted to ${f.getAbsoluteFile(target)}" in {
      val extractedFilenames = f.extract()
      extractedFilenames should be (List())

      val nf = f.getAbsoluteFile(target)

      assert(nf.listFiles().isEmpty)
    }
  }}

  List("local/zero_length.gz").foreach { filename => {
    val f = new Gzip(getPath(filename))
    val target = f.getTargetPath(getPath(filename), config.getString("unarchive.to.path"), Some("unarchived"))

    f"The file $filename" should f"be in list of extracted entries" in {
      f.getEntries.toList.head.getName should be ("zero_length")
    }

    it should s"not be extracted to ${f.getAbsoluteFile(target)}" in {
      val extractedFilenames = f.extract()
      extractedFilenames should be (List())

      val nf = f.getAbsoluteFile(target)

      assert(nf.listFiles().isEmpty)
    }
  }}

}
