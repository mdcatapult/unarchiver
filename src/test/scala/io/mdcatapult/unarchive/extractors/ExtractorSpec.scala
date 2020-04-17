package io.mdcatapult.unarchive.extractors

class ExtractorSpec extends TestAbstract("ingress") {

  class dummy[T](source: String) extends Extractor[T](source) {
    override def getEntries: Iterator[T] = ???
    override def extractFile(): T => Option[String] = ???
    override def close(): Unit = ()
  }

  "getTargetPath" should "return a valid path for a local path" in {
    val path = new dummy("local/cheese/stinking-bishop.cz").targetPath
    assert(path.toString == "ingress/derivatives/cheese/unarchived_stinking-bishop.cz")
  }
  it should "return a valid path for a remote path" in {
    val path = new dummy("remote/http/phpboyscout.uk/assets/test.zip").targetPath
    assert(path.toString == "ingress/derivatives/remote/http/phpboyscout.uk/assets/unarchived_test.zip")
  }

}
