package org.hammerlab.bam.spark.compare

import caseapp.{ AppName, ProgName, Recurse, ExtraName ⇒ O }
import cats.Show
import cats.implicits.catsKernelStdGroupForInt
import cats.syntax.all._
import com.esotericsoftware.kryo.Kryo
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat.SPLIT_MAXSIZE
import org.apache.spark.SparkContext
import org.hammerlab.args.{ FindBlockArgs, FindReadArgs, IntRanges, SplitSize }
import org.hammerlab.bam.kryo.Registrar
import org.hammerlab.bgzf.block.BGZFBlocksToCheck
import org.hammerlab.cli.app.{ SparkPathApp, SparkPathAppArgs }
import org.hammerlab.cli.args.OutputArgs
import org.hammerlab.hadoop.Configuration
import org.hammerlab.hadoop.splits.MaxSplitSize
import org.hammerlab.io.Printer._
import org.hammerlab.kryo.serializeAs
import org.hammerlab.paths.Path
import org.hammerlab.stats.Stats
import shapeless._

@AppName("Compare splits computed from many BAM files listed in a given file")
@ProgName("… org.hammerlab.bam.spark.compare")
case class Opts(@Recurse output: OutputArgs,
                @Recurse splitSizeArgs: SplitSize.Args,
                @Recurse findReadArgs: FindReadArgs,
                @Recurse findBlockArgs: FindBlockArgs,

                @O("r")
                fileRanges: Option[IntRanges] = None
               )
  extends SparkPathAppArgs

object Main
  extends SparkPathApp[Opts](classOf[Registrar]) {

  sparkConf(
    "spark.kryo.classesToRegister" →
      Seq[Class[_]](
        classOf[_ :: _],
        HNil.getClass,
        classOf[Result]
      )
      .map(_.getName)
      .mkString(",")
  )

  override protected def run(opts: Opts): Unit = {

    val lines =
      path
        .lines
        .map(_.trim)
        .filter(_.nonEmpty)
        .zipWithIndex
        .collect {
          case (path, idx)
            if opts.fileRanges.forall(_.contains(idx)) ⇒
            path
        }
        .toVector

    val numBams = lines.length

    implicit val splitSize: MaxSplitSize = opts.splitSizeArgs.maxSplitSize

    implicit val FindReadArgs(maxReadSize, readsToCheck) = opts.findReadArgs

    implicit val bgzfBlocksToCheck = opts.findBlockArgs.bgzfBlocksToCheck

    conf.setLong(SPLIT_MAXSIZE, splitSize)

    val pathResults =
      new PathChecks(lines, numBams)
        .results

    import cats.implicits.catsKernelStdMonoidForVector
    import org.hammerlab.types.Monoid._
    import shapeless._
    import record._

    val (
      timingRatios ::
      numSparkBamSplits ::
      numHadoopBamSplits ::
      sparkOnlySplits ::
      hadoopOnlySplits ::
      hadoopBamMS ::
      sparkBamMS ::
      HNil
    ) =
      pathResults
        .values
        .map {
          result ⇒
            // Create an HList with a Vector of timing-ratio Doubles followed by the Int fields from Result
            Vector(result.sparkBamMS.toDouble / result.hadoopBamMS) ::
              Result.gen.to(result).filter[Int]  // drop the differing-splits vector, leave+sum just the other (numeric) fields
        }
        .reduce { _ |+| _ }

    val diffs =
      pathResults
        .filter(_._2.diffs.nonEmpty)
        .collect

    import cats.Show.show
    import org.hammerlab.io.show.int

    implicit val showDouble: Show[Double] =
      show {
        "%.1f".format(_)
      }

    def printTimings(): Unit = {
      echo(
        "Total split-computation time:",
        s"\thadoop-bam:\t$hadoopBamMS",
        s"\tspark-bam:\t$sparkBamMS",
        ""
      )

      if (timingRatios.size > 1)
        echo(
          "Ratios:",
          Stats(timingRatios, onlySampleSorted = true),
          ""
        )
      else if (timingRatios.size == 1)
        echo(
          show"Ratio: ${timingRatios.head}",
          ""
        )
    }

    if (diffs.isEmpty) {
      echo(
        s"All $numBams BAMs' splits (totals: $numSparkBamSplits, $numHadoopBamSplits) matched!",
        ""
      )
      printTimings()
    } else {
      echo(
        s"${diffs.length} of $numBams BAMs' splits didn't match (totals: $numSparkBamSplits, $numHadoopBamSplits; $sparkOnlySplits, $hadoopOnlySplits unmatched)",
        ""
      )
      printTimings()
      diffs.foreach {
        case (
          path,
          Result(
            numSparkSplits,
            numHadoopSplits,
            diffs,
            numSparkOnlySplits,
            numHadoopOnlySplits,
            _,
            _
          )
        ) ⇒
          val totalsMsg =
            s"totals: $numSparkSplits, $numHadoopSplits; mismatched: $numSparkOnlySplits, $numHadoopOnlySplits"

          print(
            diffs
              .map {
                case Left(ours) ⇒
                  show"\t$ours"
                case Right(theirs) ⇒
                  show"\t\t$theirs"
              },
            s"\t${path.basename}: ${diffs.length} splits differ ($totalsMsg):",
            n ⇒ s"\t${path.basename}: first $n of ${diffs.length} splits that differ ($totalsMsg):"
          )
          echo("")
      }
    }
  }

  def register(kryo: Kryo): Unit = {
    /** A [[Configuration]] gets broadcast */
    Configuration.register(kryo)

    /** BAM-files are distributed as [[Path]]s which serialize as [[String]]s */
    kryo.register(
      classOf[Path],
      serializeAs[Path, String](_.toString, Path(_))
    )
    kryo.register(classOf[Array[String]])

    /** [[Result]]s get [[org.apache.spark.rdd.RDD.collect collected]] */
    kryo.register(classOf[Result])
  }
}
