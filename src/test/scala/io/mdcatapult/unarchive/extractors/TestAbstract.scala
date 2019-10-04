package io.mdcatapult.unarchive.extractors

import java.nio.file.Paths

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.FlatSpec
import scala.collection.JavaConverters._

class TestAbstract extends FlatSpec{

  def getPath(file: String): String = s"$file"

  implicit val config: Config = ConfigFactory.parseString(
    """
      |doclib {
      |  root: "./test-assets"
      |}
      |unarchive {
      |  to {
      |    path: "derivatives"
      |  }
      |}
    """.stripMargin)


}
