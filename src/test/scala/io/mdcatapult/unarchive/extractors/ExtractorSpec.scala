package io.mdcatapult.unarchive.extractors

import java.nio.file.Paths

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.FlatSpec

import scala.collection.JavaConverters._

class ExtractorSpec extends TestAbstract{

  class dummy[T](source: String) extends Extractor[T](source) {
    def getEntries: Iterator[T] = ???
    def extractFile: T ⇒ String = ???
  }

  "getTargetPath" should "return a valid path for a local path" in {
    val path = new dummy("local/cheese/stinking-bishop.cz").targetPath
    assert(path == "local/derivatives/cheese/unarchived_stinking-bishop.cz")
  }
  it should "return a valid path for a remote path" in {
    val path = new dummy("remote/http/phpboyscout.uk/assets/test.zip").targetPath
    assert(path == "local/derivatives/remote/http/phpboyscout.uk/assets/unarchived_test.zip")
  }

}
