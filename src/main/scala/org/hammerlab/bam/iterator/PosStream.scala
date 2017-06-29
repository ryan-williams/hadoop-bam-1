package org.hammerlab.bam.iterator

import java.nio.channels.FileChannel

import org.hammerlab.bgzf.Pos
import org.hammerlab.bgzf.block.{ SeekableUncompressedBytes, UncompressedBytes, UncompressedBytesI }
import org.hammerlab.io.{ ByteChannel, SeekableByteChannel }
import org.hammerlab.paths.Path

/**
 * Interface for iterating over record-start [[Pos]]s in a BAM file
 */
trait PosStreamI[Stream <: UncompressedBytesI[_]]
  extends RecordIterator[Pos, Stream] {
  override protected def _advance: Option[Pos] = {
    for {
      pos ← curPos
    } yield {
      val remainingLength = uncompressedByteChannel.getInt
      uncompressedBytes.drop(remainingLength)
      pos
    }
  }
}

/**
 * Non-seekable [[PosStreamI]]
 */
case class PosStream(uncompressedBytes: UncompressedBytes)
  extends PosStreamI[UncompressedBytes]

object PosStream {
  def apply(ch: ByteChannel): PosStream =
    PosStream(
      UncompressedBytes(
        ch
      )
    )
}

/**
 * Seekable [[PosStreamI]]
 */
case class SeekablePosStream(uncompressedBytes: SeekableUncompressedBytes)
  extends PosStreamI[SeekableUncompressedBytes]
    with SeekableRecordIterator[Pos]

object SeekablePosStream {
  def apply(ch: SeekableByteChannel): SeekablePosStream =
    SeekablePosStream(
      SeekableUncompressedBytes(
        ch
      )
    )
}
