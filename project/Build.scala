import java.io.FileOutputStream
import java.util.Properties

import sbt.Keys._
import sbt.{Def, Project, _}

import scala.collection.mutable.StringBuilder

object HortaBuild extends Build {

  lazy val `horta-hell`: Project = project in file(".")

  lazy val buildId = Def.task {
    val log = streams.value.log
    def runCommand(cmd: String): Option[String] = {
      import scala.sys.process._
      val sb = new StringBuilder
      val code = cmd ! ProcessLogger(sb append _)
      val text = sb.toString()
      if (code == 0) {
        Some(text)
      } else {
        log.warn(s"Can`t launch `$cmd` to determine buildId")
        log.warn(s"  code=$code text=$text")
        None
      }
    }
    runCommand("git describe --tags") orElse runCommand("git log -n1 --pretty=%h") getOrElse "unknown"
  }

  lazy val genVersionProperties = Def.task {
    val log = streams.value.log
    log.info("Generating version.properties resource...")
    val dir = (resourceManaged in Compile).value / "ru/org/codingteam/horta"
    dir.mkdirs()
    val file = dir / "version.properties"
    val p = new Properties()
    p.setProperty("version", version.value)
    p.setProperty("buildId", buildId.value)
    log.info(s"  version: ${p getProperty "version"}; buildId: ${p getProperty "buildId"}")
    val out = new FileOutputStream(file)
    try
      p.store(out, "Generated by sbt build")
    finally
      out.close()
    Seq(file)
  }

}
