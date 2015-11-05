addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.11.2")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.2")

libraryDependencies ++= Seq(
  "org.btrplace" % "scheduler-api" % "0.41",
  "org.btrplace" % "scheduler-choco" % "0.41",
  "org.btrplace" % "scheduler" % "0.41"
)
