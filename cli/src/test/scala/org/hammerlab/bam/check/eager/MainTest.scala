package org.hammerlab.bam.check.eager

import org.hammerlab.bam.spark.MainSuite
import org.hammerlab.bam.test.resources.{ bam1, bam1Unindexed }
import org.hammerlab.paths.Path
import org.hammerlab.test.resources.File

class MainTest
  extends MainSuite(Main) {

  override def defaultOpts(outPath: Path) =
    Seq(
      "-m", "200k",
      "-o", outPath
    )

  val seqdoopTCGAExpectedOutput = File("output/check-bam/1.bam")

  test("compare 1.bam") {
    checkFile(
      bam1
    )(
      seqdoopTCGAExpectedOutput
    )
  }

  test("compare 1.noblocks.bam") {
    checkFile(
      bam1Unindexed
    )(
      seqdoopTCGAExpectedOutput
    )
  }

  test("seqdoop 1.bam") {
    checkFile(
      "-u", bam1
    )(
      seqdoopTCGAExpectedOutput
    )
  }

  test("eager 1.bam") {
    check(
      "-s", bam1
    )(
      """1608257 uncompressed positions
        |583K compressed
        |Compression ratio: 2.69
        |4917 reads
        |All calls matched!
        |"""
        .stripMargin
    )
  }
}
