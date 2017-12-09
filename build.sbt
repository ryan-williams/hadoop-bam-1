
val defaults = Seq(
  organization := "org.hammerlab.bam",
  r"1.1.0",
  versions ++= Seq(
                   bytes → "1.1.0"           ,
                case_cli → "2.2.0"           ,
                 channel → "1.3.0"           ,
    hammerlab_hadoop_bam → "7.9.0"           ,
                io_utils → "4.0.0"           ,
               iterators → "2.0.0"           ,
                    loci → "2.0.1"           ,
              magic_rdds → "4.1.0"           ,
                    math → "2.1.2"           ,
                   paths → "1.4.0"           ,
               reference → "1.4.0"           ,
              spark_util → "2.0.1"           ,
                   stats → "1.2.0"           ,
                   types → "1.0.1"
  )
)

lazy val bgzf = project.settings(
  defaults,
  organization := "org.hammerlab",
  dep(
    case_app,
    case_cli + testtest,
    cats,
    channel,
    io_utils,
    iterators,
    math,
    paths,
    slf4j,
    spark_util,
    stats
  ),
  addSparkDeps
).dependsOn(
  test_bams test
)

lazy val check = project.settings(
  defaults,
  dep(
    bytes,
    case_app,
    case_cli + testtest,
    cats,
    channel,
    htsjdk,
    iterators,
    loci + testtest,
    magic_rdds,
    io_utils,
    paths,
    seqdoop_hadoop_bam,
    slf4j,
    spark_util
  ),
  addSparkDeps,
  fork := true  // ByteRangesTest exposes an SBT bug that this works around; see https://github.com/sbt/sbt/issues/2824
).dependsOn(
  bgzf,
  test_bams test
)

lazy val cli = project.settings(
  defaults,
  dep(
    bytes,
    case_app,
    case_cli + testtest,
    cats,
    channel,
    hammerlab_hadoop_bam,
    io_utils,
    iterators,
    magic_rdds,
    paths,
    spark_util,
    stats,
    types
  ),

  // Bits that depend on the seqdoop module use org.hammerlab:hadoop-bam; make sure we don't get the org.seqdoop one.
  excludes += seqdoop_hadoop_bam,
  
  addSparkDeps,

  shadedDeps += shapeless,

  // Spark 2.1.0 (spark-submit is an easy way to run this library's Main) puts shapeless 2.0.0 on the classpath, but we
  // need 2.3.2.
  shadeRenames += "shapeless.**" → "shaded.shapeless.@1",

  main := "org.hammerlab.bam.Main",

  // It can be convenient to keep google-cloud-nio and gcs-connecter shaded JARs in lib/, though they're not checked into
  // git. However, we exclude them from the assembly JAR by default, on the assumption that they'll be provided otherwise
  // at runtime (by Dataproc in the case of gcs-connector, and by manually adding to the classpath in the case of
  // google-cloud-nio).
  assemblyExcludeLib,

  publishAssemblyJar,

  consolePkg("spark_bam")
).dependsOn(
  bgzf,
  check,
  load,
  seqdoop,
  test_bams test
)

lazy val load = project.settings(
  defaults,

  // When running all tests in this project with `sbt test`, sometimes a Kryo
  // "Class is not registered: org.hammerlab.genomics.loci.set.LociSet" exception is thrown by
  // LoadBAMTest:"indexed disjoint regions"; this works around it.
  fork := true,

  dep(
    channel,
    htsjdk,
    iterators,
    loci + testtest,
    magic_rdds % tests,
    math,
    paths,
    reference,
    seqdoop_hadoop_bam,
    slf4j,
    spark_util
  ),
  addSparkDeps
).dependsOn(
  bgzf,
  check,
  test_bams test
)

lazy val seqdoop = project.settings(
  defaults,
  dep(
    channel,
    hammerlab_hadoop_bam,
    htsjdk,
    paths
  ),
  // Make sure we get org.hammerlab:hadoop-bam, not org.seqdoop
  excludes += seqdoop_hadoop_bam,
  addSparkDeps
).dependsOn(
  bgzf,
  check,
  test_bams test
)

lazy val test_bams = project.settings(
  defaults,
  name := "test-bams",
  r"1.0.0",
  dep(
    paths,
    testUtils
  ),
  testDeps := Nil
)

// named this module "metrics" instead of "benchmarks" to work around bizarre IntelliJ-scala-plugin bug, cf.
// https://youtrack.jetbrains.com/issue/SCL-12628#comment=27-2439322
lazy val metrics = project.in(file("benchmarks")).settings(
  defaults,
  dep(
    paths,
    bytes
  )
)

lazy val spark_bam =
  rootProject(
    bgzf,
    check,
    cli,
    load,
    seqdoop,
    test_bams
  )
