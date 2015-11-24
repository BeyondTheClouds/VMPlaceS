import sbtassembly.Plugin.{MergeStrategy, AssemblyKeys}
import AssemblyKeys._

name := "VMPlaceS"

version := "0.5"

organization := "org.discovery"

scalaVersion := "2.10.4"

crossPaths := false

retrieveManaged := true

// Excluding the following directories for compilation: scheduling/dvms
excludeFilter in unmanagedSources := new sbt.FileFilter{
  //def accept(f: File): Boolean = "(?s).*scheduling/dvms/.*|.*scheduling/hubis/.*".r.pattern.matcher(f.getAbsolutePath).matches
  def accept(f: File): Boolean = "(?s).*scheduling/entropyBased/dvms/.*".r.pattern.matcher(f.getAbsolutePath).matches
}

seq(assemblySettings: _*)

mainClass in assembly := Some("simulation.Main")

excludedJars in assembly <<= (fullClasspath in assembly) map { cp => 
  cp filter {_.data.getName == "simgrid_full.jar"}
}

test in assembly := {}

jarName in assembly := "simulation.jar"

assemblyOption in assembly ~= { _.copy(includeScala = false) }

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case "application.conf" => MergeStrategy.rename
    case "META-INF/MANIFEST.MF" => old("META-INF/MANIFEST.MF")
    case x => MergeStrategy.first
  }
}

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= Seq(
  "org.btrplace" % "scheduler-api" % "0.42",
  "org.btrplace" % "scheduler-choco" % "0.42",
  "org.btrplace" % "scheduler-json" % "0.42",
  "org.btrplace" % "btrpsl" % "0.42",
  "org.btrplace" % "scheduler" % "0.42",
  "org.btrplace" % "bench" % "0.42"
)
