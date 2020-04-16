package io.mdcatapult.unarchive.extractors

import org.scalatest.BeforeAndAfter

class AutoSpec extends TestAbstract("ingressAuto") with BeforeAndAfter {

  val files: List[(String, Int, String)] = List[(String, Int, String)](
    // ("local/test.bz2", 1, "application/x-bzip2"),
    // ("local/test.laz", 1, ""),
    // ("local/test.lzh", 3, "application/x-lzh-compressed"),
    // ("local/test.rar", 3, "application/x-rar"),
    // ("local/test-compressed.rar", 2, "application/x-rar-compressed"),
    // ("local/test.skyd", 3, "application/zlib"),
    ("local/test.tar", 1, "application/x-tar"),
    ("local/derivatives/derived_test.tar", 1, "application/x-tar"),
    // ("local/test.tar.bz2", 1, "application/x-bzip2"),
    ("local/test.tar.gz", 2, "application/gzip"),
    ("local/derivatives/derived_test.tar.gz", 2, "application/gzip"),
    // ("local/test.wsz", 1, "application/zlib"),
    ("local/test.xz", 4, "application/x-xz"),
    ("local/derivatives/derived_test.xz", 4, "application/x-xz"),
    ("local/test.zip", 1, "application/zip"),
    ("local/derivatives/derived_test.zip", 1, "application/zip")
  )

  val zeroLengthFiles: List[(String, Int, String)] = List[(String, Int, String)](
    ("local/zero_length.tar.gz", 0, "application/gzip"),
//    ("local/zero_length.gz", 0, "application/gzip"),
    ("local/zero_length.zip", 0, "application/zip")
  )

  files foreach  { file: (String, Int, String) => {
    val f = new Auto(getPath(file._1))
    val target = f.getTargetPath(getPath(file._1), config.getString("unarchive.to.path"), Some("unarchived"))

    it should s"extract ${file._1} successfully to ${f.getAbsoluteFile(target)}" in {
      f.extract()
      val nf = f.getAbsoluteFile(target)

      assert(nf.exists())
      assert(nf.listFiles().length > 0)
    }
  }}

  zeroLengthFiles foreach  { file: (String, Int, String) => {
    val f = new Auto(getPath(file._1))
    val target = f.getTargetPath(getPath(file._1), config.getString("unarchive.to.path"), Some("unarchived"))

    it should s"not extract ${file._1}" in {
      f.extract()
      val nf = f.getAbsoluteFile(target)

      assert(!nf.exists())
    }
  }}

}
