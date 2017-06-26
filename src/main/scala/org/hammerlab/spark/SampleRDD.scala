package org.hammerlab.spark

import org.apache.spark.rdd.RDD
import org.hammerlab.io.SampleSize

import scala.reflect.ClassTag

object SampleRDD {
  def sample[T: ClassTag](rdd: RDD[T],
                          size: Long)(
      implicit sampleSize: SampleSize
  ): Array[T] =
    sampleSize match {
      case SampleSize(Some(ss))
        if size > ss ⇒
        rdd.take(ss)
      case _  if size == 0 ⇒
        Array()
      case _ ⇒
        rdd.collect()
    }
}
