scalaVersion := "2.11.12"

// Set to false or remove if you want to show stubs as linking errors
nativeLinkStubs := true

enablePlugins(ScalaNativePlugin)

libraryDependencies += "com.lihaoyi" %%% "fansi" % "0.2.7"
libraryDependencies += "org.rogach" %%% "scallop" % "3.4.0"
