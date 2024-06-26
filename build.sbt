import sbtrelease.ReleaseStateTransformations.*
import Release.*

import scala.collection.Seq

lazy val configVersion = "1.4.3"
lazy val pekkoVersion = "1.0.2"
lazy val catsVersion = "2.12.0"
lazy val doclibCommonVersion = "5.0.1"
lazy val scalacticVersion = "3.2.15"
lazy val scalaTestVersion = "3.2.15"
lazy val scalaMockVersion = "5.2.0"
lazy val scalaLoggingVersion = "3.9.5"
lazy val logbackClassicVersion = "1.5.6"
lazy val commonsCompressVersion = "1.26.2"
lazy val xzVersion = "1.9"

val meta = """META.INF/(blueprint|cxf).*""".r

lazy val creds = {
  sys.env.get("CI_JOB_TOKEN") match {
    case Some(token) =>
      Credentials("GitLab Packages Registry", "gitlab.com", "gitlab-ci-token", token)
    case _ =>
      Credentials(Path.userHome / ".sbt" / ".credentials")
  }
}

// Registry ID is the project ID of the project where the package is published, this should be set in the CI/CD environment
val registryId = sys.env.get("REGISTRY_HOST_PROJECT_ID").getOrElse("")

lazy val root = (project in file(".")).
  settings(
    name              := "consumer-unarchive",
    scalaVersion      := "2.13.3",
    useCoursier   := false,
    scalacOptions ++= Seq(
      "-encoding", "utf-8",
      "-unchecked",
      "-deprecation",
      "-explaintypes",
      "-feature",
      "-Xlint",
      "-Xfatal-warnings",
    ),
    resolvers ++= Seq(
      "gitlab" at s"https://gitlab.com/api/v4/projects/$registryId/packages/maven",
      "Maven Public" at "https://repo1.maven.org/maven2"),
    publishTo := {
      Some("gitlab" at s"https://gitlab.com/api/v4/projects/$registryId/packages/maven")
    },
    credentials += creds,
    libraryDependencies ++= Seq(
      "org.scalactic" %% "scalactic"                  % scalacticVersion,
      "org.scalatest" %% "scalatest"                  % scalaTestVersion % Test,
      "org.apache.pekko" %% "pekko-slf4j"             % pekkoVersion,
      "ch.qos.logback" % "logback-classic"            % logbackClassicVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
      "com.typesafe" % "config"                       % configVersion,
      "org.typelevel" %% "cats-kernel"                % catsVersion,
      "org.typelevel" %% "cats-core"                  % catsVersion,
      "io.mdcatapult.doclib" %% "common"              % doclibCommonVersion,
      "org.apache.commons" % "commons-compress"       % commonsCompressVersion,
      "org.tukaani" % "xz"                            % xzVersion
    ).map(
      _.exclude(org = "javax.ws.rs", name = "javax.ws.rs-api")
        .exclude(org = "com.google.protobuf", name = "protobuf-java")
        .exclude(org = "com.typesafe.play", name = "shaded-asynchttpclient")
    ),
  )
  .settings(
    assemblyJarName := "consumer.jar",
    assembly / test := {},
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", "INDEX.LIST") => MergeStrategy.discard
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
      case PathList("com", "sun", _*) => MergeStrategy.first
      case PathList("javax", "servlet", _*) => MergeStrategy.first
      case PathList("javax", "activation", _*) => MergeStrategy.first
      case PathList("org", "apache", "commons", _*) => MergeStrategy.first
      case PathList("com", "ctc", "wstx", _*) => MergeStrategy.first
      case PathList("scala", "collection", "compat", _*) => MergeStrategy.first
      case PathList("scala", "util", "control", "compat", _*) => MergeStrategy.first
      case PathList(xs @ _*) if xs.last endsWith ".DSA" => MergeStrategy.discard
      case PathList(xs @ _*) if xs.last endsWith ".SF" => MergeStrategy.discard
      case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == "module-info.class" => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == "public-suffix-list.txt" => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == ".gitkeep" => MergeStrategy.discard
      case "META-INF/jpms.args" => MergeStrategy.discard
      case n if n.startsWith("application.conf") => MergeStrategy.first
      case n if n.startsWith("scala-collection-compat.properties") => MergeStrategy.first
      case n if n.endsWith(".conf") => MergeStrategy.concat
      case n if n.startsWith("logback.xml") => MergeStrategy.first
      case meta(_) => MergeStrategy.first
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    })
  .settings(
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      getShortSha,
      writeReleaseVersionFile,
      commitAllRelease,
      tagRelease,
      runAssembly,
      setNextVersion,
      writeNextVersionFile,
      commitAllNext,
      pushChanges
    )
  )
