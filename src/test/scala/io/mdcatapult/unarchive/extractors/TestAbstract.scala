package io.mdcatapult.unarchive.extractors

import java.nio.file.Paths

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.FlatSpec
import scala.collection.JavaConverters._

class TestAbstract extends FlatSpec{

  def getPath(file: String): String = Paths.get(getClass.getResource(file).toURI).toString

  implicit val config: Config = ConfigFactory.parseString(
    """
      |unarchive {
      |  to {
      |    path: "local/derivatives"
      |  }
      |}
    """.stripMargin)


}
