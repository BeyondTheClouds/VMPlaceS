name := "ImgridInjector"

version := "0.5"

organization := "org.discovery"

scalaVersion := "2.10.0"

crossPaths := false

retrieveManaged := true

// Excluding the following directories for compilation: scheduling/dvms
excludeFilter in unmanagedSources := new sbt.FileFilter{
  //def accept(f: File): Boolean = "(?s).*scheduling/dvms/.*|.*scheduling/hubis/.*".r.pattern.matcher(f.getAbsolutePath).matches
  def accept(f: File): Boolean = "(?s).*scheduling/entropyBased/dvms/.*".r.pattern.matcher(f.getAbsolutePath).matches
}
