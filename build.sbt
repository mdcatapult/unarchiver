import java.io.PrintWriter

import sbtrelease.ReleaseStateTransformations._
import sbtrelease.{Vcs, Version}
import sbt._
import Keys._
import sbt.complete.DefaultParsers._
import scala.sys.process.ProcessLogger

lazy val configVersion = "1.3.2"
lazy val akkaVersion = "2.5.18"
lazy val catsVersion = "1.5.0-RC1"
lazy val opRabbitVersion = "2.1.0"
lazy val mongoVersion = "2.5.0"
lazy val awsScalaVersion = "0.8.1"
lazy val tikaVersion = "1.20"
lazy val doclibCommonVersion = "0.0.23"

val meta = """META.INF/(blueprint|cxf).*""".r

lazy val root = (project in file(".")).
  settings(
    name              := "consumer-unarchive",
    scalaVersion      := "2.12.8",
    coverageEnabled   := false,
    scalacOptions     += "-Ypartial-unification",
    resolvers         ++= Seq(
      "MDC Nexus Releases" at "http://nexus.mdcatapult.io/repository/maven-releases/",
      "MDC Nexus Snapshots" at "http://nexus.mdcatapult.io/repository/maven-snapshots/"),
    updateOptions := updateOptions.value.withLatestSnapshots(false),
    credentials       += {
      val nexusPassword = sys.env.get("NEXUS_PASSWORD")
      if ( nexusPassword.nonEmpty ) {
        Credentials("Sonatype Nexus Repository Manager", "nexus.mdcatapult.io", "gitlab", nexusPassword.get)
      } else {
        Credentials(Path.userHome / ".sbt" / ".credentials")
      }
    },
    libraryDependencies ++= Seq(
      "org.scalactic" %% "scalactic"                  % "3.0.5",
      "org.scalatest" %% "scalatest"                  % "3.0.5" % "test",
      "com.typesafe.akka" %% "akka-slf4j"             % akkaVersion,
      "ch.qos.logback" % "logback-classic"            % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
      "com.typesafe" % "config"                       % configVersion,
      "org.typelevel" %% "cats-macros"                % catsVersion,
      "org.typelevel" %% "cats-kernel"                % catsVersion,
      "org.typelevel" %% "cats-core"                  % catsVersion,
      "io.mdcatapult.doclib" %% "common"              % doclibCommonVersion,
      "org.apache.tika" % "tika-core"                 % tikaVersion,
      "org.apache.tika" % "tika-parsers"              % tikaVersion,
      "jakarta.ws.rs" % "jakarta.ws.rs-api"           % "2.1.4"
    ).map(_ exclude("javax.ws.rs", "javax.ws.rs-api")),
  )
  .settings(
    assemblyJarName := "consumer.jar",
    test in assembly := {},
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", "INDEX.LIST") => MergeStrategy.discard
      case PathList("com", "sun", xs @ _*) => MergeStrategy.first
      case PathList("javax", "servlet", xs @ _*) => MergeStrategy.first
      case PathList("javax", "activation", xs @ _*) => MergeStrategy.first
      case PathList("org", "apache", "commons", xs @ _*) => MergeStrategy.first
      case PathList("com", "ctc", "wstx", xs @ _*) => MergeStrategy.first
      case PathList(xs @ _*) if xs.last endsWith ".DSA" => MergeStrategy.discard
      case PathList(xs @ _*) if xs.last endsWith ".SF" => MergeStrategy.discard
      case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == "module-info.class" => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == "public-suffix-list.txt" => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == ".gitkeep" => MergeStrategy.discard
      case "META-INF/jpms.args" => MergeStrategy.discard
      case n if n.startsWith("application.conf") => MergeStrategy.concat
      case n if n.endsWith(".conf") => MergeStrategy.concat
      case meta(_) => MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
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
      { st: State ⇒
        // write version.conf
        st.get(ReleaseKeys.versions) match {
          case Some(v) ⇒ writeVersionFile(v._1, st.get(AttributeKey[String]("hash")))
          case None ⇒ sys.error("Aborting release. no version number present.")
        }
        st
      },
      commitAllRelease,
      tagRelease,
      runAssembly,
      setNextVersion,
      { st: State ⇒
        // write version.conf
        st.get(ReleaseKeys.versions) match {
          case Some(v) ⇒ writeVersionFile(v._2)
          case None ⇒ sys.error("Aborting release. no version number present.")
        }
        st
      },
      commitAllNext,
      pushChanges
    )
  )


def getShortSha: State ⇒ State = { st: State ⇒
  val extracted: Extracted = Project.extract( st )
  val vcs: Vcs = extracted.get(releaseVcs)
    .getOrElse(sys.error("Aborting release. Working directory is not a repository of a recognized VCS."))
  st.put(AttributeKey[String]("hash"), vcs.currentHash.slice(0, 8))
}

def runAssembly = ReleaseStep(action = (st: State) ⇒ {
  val extracted = Project.extract(st)
  val ref = extracted.get(thisProjectRef)
  extracted.runAggregated(assembly in Global in ref, st)
})

def commitAllRelease = ReleaseStep(action = (st: State) => commitAll(st, releaseCommitMessage))
def commitAllNext = ReleaseStep(action = (st: State) => commitAll(st, releaseNextCommitMessage))

def commitAll = { (st: State, commitMessage: TaskKey[String]) ⇒
  val log = toTempProcessLogger(st)
  val extract = Project.extract(st)
  val vcs = getVcs(st)
  val sign = extract.get(releaseVcsSign)
  val signOff = extract.get(releaseVcsSignOff)
  vcs.add("./*") !! log
  val status = vcs.status.!!.trim
  val newState = if (status.nonEmpty) {
    val (state, msg) = extract.runTask(commitMessage, st)
    vcs.commit(msg, sign, signOff) ! log
    state
  } else {
    // nothing to commit. this happens if the version.sbt file hasn't changed.
    st
  }
  newState
}

def getVcs(st: State): Vcs = {
  val extracted: Extracted = Project.extract( st )
  extracted.get(releaseVcs)
    .getOrElse(sys.error("Aborting release. Working directory is not a repository of a recognized VCS."))
}

def toTempProcessLogger(st: State): ProcessLogger = new ProcessLogger {
  override def err(s: => String): Unit = st.log.info(s)
  override def out(s: => String): Unit = st.log.info(s)
  override def buffer[T](f: => T): T = st.log.buffer(f)
}

def writeVersionFile(version: String, hash: Option[String] = None): Unit = {
  val ver: Version = Version(version).get
  val writer = new PrintWriter(new File("src/main/resources/version.conf"))
  writer.write(
    s"""version {
       |  number = "${ver.string}",
       |  major = ${ver.major},
       |  minor =  ${ver.subversions.head},
       |  patch = ${ver.subversions(1)},
       |  hash =  "${hash.getOrElse(ver.qualifier.get.replaceAll("^-", ""))}"
       |  hash =  $${?VERSION_HASH}
       |}
       |""".stripMargin)
  writer.close()
}