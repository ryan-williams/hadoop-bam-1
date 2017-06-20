package org.hammerlab.io

import java.io.{ EOFException, IOException, InputStream }

import org.apache.hadoop.conf.Configuration
import org.hammerlab.hadoop.Path
import org.hammerlab.io.SeekableByteChannel.SeekableHadoopByteChannel
import org.hammerlab.test.Suite
import org.hammerlab.test.resources.File

case class InputStreamStub(reads: String*)
  extends InputStream {
  val bytes =
    reads
      .flatMap(
        _
          .map(_.toInt)
          .toVector :+ -1
      )
      .iterator

  override def read(): Int =
    if (bytes.hasNext)
      bytes.next()
    else
      throw new EOFException()
}

class ByteChannelTest
  extends Suite {
  test("incomplete InputStream read") {
    val ch: ByteChannel =
      InputStreamStub(
        "12345",
        "67890",
        "1",
        "",
        "234"
      )

    val b4 = Buffer(4)

    ch.read(b4)
    b4.array.map(_.toChar).mkString("") should be("1234")

    b4.position(0)
    ch.read(b4)
    b4.array.map(_.toChar).mkString("") should be("5678")

    b4.position(0)
    intercept[IOException] {
      ch.read(b4)
    }.getMessage should be("Only read 3 (2 then 1) of 4 bytes from position 8")
  }

  test("CachingChannel") {
    val path = Path(File("5k.bam").uri)
    val conf = new Configuration

    val ch = SeekableHadoopByteChannel(path, conf)

    val cc: CachingChannel = SeekableHadoopByteChannel(path, conf)

    for {
      i ← 0 until 100
    } {
      ch.seek(i)
      cc.seek(i)

      withClue(s"idx: $i: ") {
        cc.getInt should be(ch.getInt)
      }
    }

  }
}
