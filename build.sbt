name := "geophoto_backend"

version := "0.1"

scalaVersion := "2.13.6"

addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.13.2" cross CrossVersion.full)

libraryDependencies ++= Dependencies.all

scalacOptions ++= ScalaOpts.all