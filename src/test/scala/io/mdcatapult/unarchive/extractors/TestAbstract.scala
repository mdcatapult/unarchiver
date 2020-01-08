package io.mdcatapult.unarchive.extractors

import better.files.Dsl.pwd
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.util.DirectoryDelete
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

class TestAbstract extends FlatSpec with DirectoryDelete with BeforeAndAfterAll {

  def getPath(file: String): String = s"$file"

  implicit val config: Config = ConfigFactory.parseString(
    """
      |doclib {
      |  root: "./test-assets"
      |  local: {
      |    target-dir: "local"
      |    temp-dir: "ingress"
      |  }
      |  remote: {
      |    target-dir: "remote"
      |  }
      |}
      |unarchive {
      |  to {
      |    path: "derivatives"
      |  }
      |}
    """.stripMargin)

  override def afterAll(): Unit = {
    // These may or may not exist but are all removed anyway
    deleteDirectories(List(pwd/"test-assets/ingress"))
  }

}
