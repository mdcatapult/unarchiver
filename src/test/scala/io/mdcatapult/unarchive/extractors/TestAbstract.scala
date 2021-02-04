package io.mdcatapult.unarchive.extractors

import better.files.Dsl.pwd
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.util.path.DirectoryDeleter.deleteDirectories
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec

class TestAbstract(tempDir: String) extends AnyFlatSpec with BeforeAndAfterAll {

  def getPath(file: String): String = file

  implicit val config: Config = ConfigFactory.parseString(
    s"""
      |doclib {
      |  root: "./test-assets"
      |  local: {
      |    target-dir: "local"
      |    temp-dir: "$tempDir"
      |  }
      |  remote: {
      |    target-dir: "remote"
      |  }
      |  derivative {
      |    target-dir: "derivatives"
      |  }
      |}
      |consumer {
      |  name = "unarchiver"
      |}
    """.stripMargin)

  override def afterAll(): Unit = {
    // These may or may not exist but are all removed anyway
    deleteDirectories(List(pwd/"test-assets"/tempDir))
  }

}
