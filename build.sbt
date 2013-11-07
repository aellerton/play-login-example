name := "play-login-example"

version := "1.0-SNAPSHOT"

resolvers += "org.sedis" at "http://pk11-scratch.googlecode.com/svn/trunk"

libraryDependencies ++= Seq(
  cache,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.2.3",
  "org.sedis" % "sedis_2.10.0" % "1.1.8"
)     

play.Project.playScalaSettings
