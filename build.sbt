name := "spark-bam"
version := "1.1.0-SNAPSHOT"
deps ++= Seq(
  libs.value('iterators).copy(revision = "1.3.0-SNAPSHOT"),
  libs.value('slf4j),
  "com.github.alexarchambault" %% "case-app" % "1.2.0-SNAPSHOT",
  "org.hammerlab" %% "magic-rdds" % "1.5.0-SNAPSHOT",
  libs.value('paths).copy(revision = "1.1.1-SNAPSHOT"),
  libs.value('reference),
  "org.hammerlab" %% "spark-util" % "1.2.0-SNAPSHOT",
  "org.hammerlab" % "hadoop-bam" % "7.8.1-SNAPSHOT" exclude("org.apache.hadoop", "hadoop-client")
)

compileAndTestDeps += libs.value('loci)

addSparkDeps

testUtilsVersion := "1.2.4-SNAPSHOT"
sparkTestsVersion := "2.1.0-SNAPSHOT"

shadedDeps += "com.chuusai" %% "shapeless" % "2.3.2"

// Spark 2.1.0 (spark-submit is an easy way to run this library's Main) puts shapeless 2.0.0 on the classpath, but we
// need 2.3.2.
shadeRenames ++= Seq(
  "shapeless.**" → "org.hammerlab.shapeless.@1"
)

main := "org.hammerlab.bam.spark.Main"

// It can be convenient to keep google-cloud-nio and gcs-connecter shaded JARs in lib/, though they're not checked into
// git. However, we exclude them from the assembly JAR by default, on the assumption that they'll be provided otherwise
// at runtime (by Dataproc in the case of gcs-connector, and by manually adding to the classpath in the case of
// google-cloud-nio).
assemblyExcludedJars in assembly := {
  (fullClasspath in assembly).value.filter {
    _.data.getParent.endsWith("/lib")
  }
}
