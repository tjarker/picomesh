//scalaVersion := "2.13.14"

scalacOptions ++= Seq(
  "-feature",
  "-language:reflectiveCalls",
)
fork := true


scalaVersion := "2.13.14"
val chiselVersion = "3.6.1"
addCompilerPlugin("edu.berkeley.cs" %% "chisel3-plugin" % chiselVersion cross CrossVersion.full)
libraryDependencies += "edu.berkeley.cs" %% "chisel3" % chiselVersion
libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "0.6.2"

Compile / unmanagedSourceDirectories += baseDirectory.value / "soc-comm/src"
Compile / unmanagedSourceDirectories += baseDirectory.value / "ip-contributions/src"

libraryDependencies += "com.fazecast" % "jSerialComm" % "[2.0.0,3.0.0)"
libraryDependencies += "net.fornwall" % "jelf" % "0.9.0"

libraryDependencies += "io.github.tjarker" %% "liftoff" % "0.0.1"