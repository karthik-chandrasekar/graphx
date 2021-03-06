/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.streaming.api.java

import java.util.{List => JList}
import java.lang.{Long => JLong}

import scala.collection.JavaConversions._
import scala.reflect.ClassTag

import org.apache.spark.streaming._
import org.apache.spark.api.java.{JavaPairRDD, JavaRDDLike, JavaRDD}
import org.apache.spark.api.java.function.{Function => JFunction, Function2 => JFunction2}
import org.apache.spark.api.java.function.{Function3 => JFunction3, _}
import java.util
import org.apache.spark.rdd.RDD
import JavaDStream._
import org.apache.spark.streaming.dstream.DStream

trait JavaDStreamLike[T, This <: JavaDStreamLike[T, This, R], R <: JavaRDDLike[T, R]]
    extends Serializable {
  implicit val classTag: ClassTag[T]

  def dstream: DStream[T]

  def wrapRDD(in: RDD[T]): R

  implicit def scalaIntToJavaLong(in: DStream[Long]): JavaDStream[JLong] = {
    in.map(new JLong(_))
  }

  /**
   * Print the first ten elements of each RDD generated in this DStream. This is an output
   * operator, so this DStream will be registered as an output stream and there materialized.
   */
  def print() = dstream.print()

  /**
   * Return a new DStream in which each RDD has a single element generated by counting each RDD
   * of this DStream.
   */
  def count(): JavaDStream[JLong] = dstream.count()

  /**
   * Return a new DStream in which each RDD contains the counts of each distinct value in
   * each RDD of this DStream.  Hash partitioning is used to generate the RDDs with
   * Spark's default number of partitions.
   */
  def countByValue(): JavaPairDStream[T, JLong] = {
    JavaPairDStream.scalaToJavaLong(dstream.countByValue())
  }

  /**
   * Return a new DStream in which each RDD contains the counts of each distinct value in
   * each RDD of this DStream. Hash partitioning is used to generate the RDDs with `numPartitions`
   * partitions.
   * @param numPartitions  number of partitions of each RDD in the new DStream.
   */
  def countByValue(numPartitions: Int): JavaPairDStream[T, JLong] = {
    JavaPairDStream.scalaToJavaLong(dstream.countByValue(numPartitions))
  }


  /**
   * Return a new DStream in which each RDD has a single element generated by counting the number
   * of elements in a window over this DStream. windowDuration and slideDuration are as defined in the
   * window() operation. This is equivalent to window(windowDuration, slideDuration).count()
   */
  def countByWindow(windowDuration: Duration, slideDuration: Duration) : JavaDStream[JLong] = {
    dstream.countByWindow(windowDuration, slideDuration)
  }

  /**
   * Return a new DStream in which each RDD contains the count of distinct elements in
   * RDDs in a sliding window over this DStream. Hash partitioning is used to generate the RDDs with
   * Spark's default number of partitions.
   * @param windowDuration width of the window; must be a multiple of this DStream's
   *                       batching interval
   * @param slideDuration  sliding interval of the window (i.e., the interval after which
   *                       the new DStream will generate RDDs); must be a multiple of this
   *                       DStream's batching interval
   */
  def countByValueAndWindow(windowDuration: Duration, slideDuration: Duration)
    : JavaPairDStream[T, JLong] = {
    JavaPairDStream.scalaToJavaLong(
      dstream.countByValueAndWindow(windowDuration, slideDuration))
  }

  /**
   * Return a new DStream in which each RDD contains the count of distinct elements in
   * RDDs in a sliding window over this DStream. Hash partitioning is used to generate the RDDs with `numPartitions`
   * partitions.
   * @param windowDuration width of the window; must be a multiple of this DStream's
   *                       batching interval
   * @param slideDuration  sliding interval of the window (i.e., the interval after which
   *                       the new DStream will generate RDDs); must be a multiple of this
   *                       DStream's batching interval
   * @param numPartitions  number of partitions of each RDD in the new DStream.
   */
  def countByValueAndWindow(windowDuration: Duration, slideDuration: Duration, numPartitions: Int)
    : JavaPairDStream[T, JLong] = {
    JavaPairDStream.scalaToJavaLong(
      dstream.countByValueAndWindow(windowDuration, slideDuration, numPartitions))
  }

  /**
   * Return a new DStream in which each RDD is generated by applying glom() to each RDD of
   * this DStream. Applying glom() to an RDD coalesces all elements within each partition into
   * an array.
   */
  def glom(): JavaDStream[JList[T]] = {
    new JavaDStream(dstream.glom().map(x => new java.util.ArrayList[T](x.toSeq)))
  }


  /** Return the [[org.apache.spark.streaming.StreamingContext]] associated with this DStream */
  def context(): StreamingContext = dstream.context()

  /** Return a new DStream by applying a function to all elements of this DStream. */
  def map[R](f: JFunction[T, R]): JavaDStream[R] = {
    new JavaDStream(dstream.map(f)(f.returnType()))(f.returnType())
  }

  /** Return a new DStream by applying a function to all elements of this DStream. */
  def map[K2, V2](f: PairFunction[T, K2, V2]): JavaPairDStream[K2, V2] = {
    def cm = implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[Tuple2[K2, V2]]]
    new JavaPairDStream(dstream.map(f)(cm))(f.keyType(), f.valueType())
  }

  /**
   * Return a new DStream by applying a function to all elements of this DStream,
   * and then flattening the results
   */
  def flatMap[U](f: FlatMapFunction[T, U]): JavaDStream[U] = {
    import scala.collection.JavaConverters._
    def fn = (x: T) => f.apply(x).asScala
    new JavaDStream(dstream.flatMap(fn)(f.elementType()))(f.elementType())
  }

  /**
   * Return a new DStream by applying a function to all elements of this DStream,
   * and then flattening the results
   */
  def flatMap[K2, V2](f: PairFlatMapFunction[T, K2, V2]): JavaPairDStream[K2, V2] = {
    import scala.collection.JavaConverters._
    def fn = (x: T) => f.apply(x).asScala
    def cm = implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[Tuple2[K2, V2]]]
    new JavaPairDStream(dstream.flatMap(fn)(cm))(f.keyType(), f.valueType())
  }

    /**
   * Return a new DStream in which each RDD is generated by applying mapPartitions() to each RDDs
   * of this DStream. Applying mapPartitions() to an RDD applies a function to each partition
   * of the RDD.
   */
  def mapPartitions[U](f: FlatMapFunction[java.util.Iterator[T], U]): JavaDStream[U] = {
    def fn = (x: Iterator[T]) => asScalaIterator(f.apply(asJavaIterator(x)).iterator())
    new JavaDStream(dstream.mapPartitions(fn)(f.elementType()))(f.elementType())
  }

  /**
   * Return a new DStream in which each RDD is generated by applying mapPartitions() to each RDDs
   * of this DStream. Applying mapPartitions() to an RDD applies a function to each partition
   * of the RDD.
   */
  def mapPartitions[K2, V2](f: PairFlatMapFunction[java.util.Iterator[T], K2, V2])
  : JavaPairDStream[K2, V2] = {
    def fn = (x: Iterator[T]) => asScalaIterator(f.apply(asJavaIterator(x)).iterator())
    new JavaPairDStream(dstream.mapPartitions(fn))(f.keyType(), f.valueType())
  }

  /**
   * Return a new DStream in which each RDD has a single element generated by reducing each RDD
   * of this DStream.
   */
  def reduce(f: JFunction2[T, T, T]): JavaDStream[T] = dstream.reduce(f)

  /**
   * Return a new DStream in which each RDD has a single element generated by reducing all
   * elements in a sliding window over this DStream.
   * @param reduceFunc associative reduce function
   * @param windowDuration width of the window; must be a multiple of this DStream's
   *                       batching interval
   * @param slideDuration  sliding interval of the window (i.e., the interval after which
   *                       the new DStream will generate RDDs); must be a multiple of this
   *                       DStream's batching interval
   */
  def reduceByWindow(
      reduceFunc: (T, T) => T,
      windowDuration: Duration,
      slideDuration: Duration
    ): DStream[T] = {
    dstream.reduceByWindow(reduceFunc, windowDuration, slideDuration)
  }

  /**
   * Return a new DStream in which each RDD has a single element generated by reducing all
   * elements in a sliding window over this DStream. However, the reduction is done incrementally
   * using the old window's reduced value :
   *  1. reduce the new values that entered the window (e.g., adding new counts)
   *  2. "inverse reduce" the old values that left the window (e.g., subtracting old counts)
   *  This is more efficient than reduceByWindow without "inverse reduce" function.
   *  However, it is applicable to only "invertible reduce functions".
   * @param reduceFunc associative reduce function
   * @param invReduceFunc inverse reduce function
   * @param windowDuration width of the window; must be a multiple of this DStream's
   *                       batching interval
   * @param slideDuration  sliding interval of the window (i.e., the interval after which
   *                       the new DStream will generate RDDs); must be a multiple of this
   *                       DStream's batching interval
   */
  def reduceByWindow(
      reduceFunc: JFunction2[T, T, T],
      invReduceFunc: JFunction2[T, T, T],
      windowDuration: Duration,
      slideDuration: Duration
    ): JavaDStream[T] = {
    dstream.reduceByWindow(reduceFunc, invReduceFunc, windowDuration, slideDuration)
  }

  /**
   * Return all the RDDs between 'fromDuration' to 'toDuration' (both included)
   */
  def slice(fromTime: Time, toTime: Time): JList[R] = {
    new util.ArrayList(dstream.slice(fromTime, toTime).map(wrapRDD(_)).toSeq)
  }

  /**
   * Apply a function to each RDD in this DStream. This is an output operator, so
   * 'this' DStream will be registered as an output stream and therefore materialized.
   *
   * @deprecated  As of release 0.9.0, replaced by foreachRDD
   */
  @Deprecated
  def foreach(foreachFunc: JFunction[R, Void]) {
    foreachRDD(foreachFunc)
  }

  /**
   * Apply a function to each RDD in this DStream. This is an output operator, so
   * 'this' DStream will be registered as an output stream and therefore materialized.
   *
   * @deprecated  As of release 0.9.0, replaced by foreachRDD
   */
  @Deprecated
  def foreach(foreachFunc: JFunction2[R, Time, Void]) {
    foreachRDD(foreachFunc)
  }

  /**
   * Apply a function to each RDD in this DStream. This is an output operator, so
   * 'this' DStream will be registered as an output stream and therefore materialized.
   */
  def foreachRDD(foreachFunc: JFunction[R, Void]) {
    dstream.foreachRDD(rdd => foreachFunc.call(wrapRDD(rdd)))
  }

  /**
   * Apply a function to each RDD in this DStream. This is an output operator, so
   * 'this' DStream will be registered as an output stream and therefore materialized.
   */
  def foreachRDD(foreachFunc: JFunction2[R, Time, Void]) {
    dstream.foreachRDD((rdd, time) => foreachFunc.call(wrapRDD(rdd), time))
  }

  /**
   * Return a new DStream in which each RDD is generated by applying a function
   * on each RDD of 'this' DStream.
   */
  def transform[U](transformFunc: JFunction[R, JavaRDD[U]]): JavaDStream[U] = {
    implicit val cm: ClassTag[U] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[U]]
    def scalaTransform (in: RDD[T]): RDD[U] =
      transformFunc.call(wrapRDD(in)).rdd
    dstream.transform(scalaTransform(_))
  }

  /**
   * Return a new DStream in which each RDD is generated by applying a function
   * on each RDD of 'this' DStream.
   */
  def transform[U](transformFunc: JFunction2[R, Time, JavaRDD[U]]): JavaDStream[U] = {
    implicit val cm: ClassTag[U] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[U]]
    def scalaTransform (in: RDD[T], time: Time): RDD[U] =
      transformFunc.call(wrapRDD(in), time).rdd
    dstream.transform(scalaTransform(_, _))
  }

  /**
   * Return a new DStream in which each RDD is generated by applying a function
   * on each RDD of 'this' DStream.
   */
  def transform[K2, V2](transformFunc: JFunction[R, JavaPairRDD[K2, V2]]):
  JavaPairDStream[K2, V2] = {
    implicit val cmk: ClassTag[K2] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[K2]]
    implicit val cmv: ClassTag[V2] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[V2]]
    def scalaTransform (in: RDD[T]): RDD[(K2, V2)] =
      transformFunc.call(wrapRDD(in)).rdd
    dstream.transform(scalaTransform(_))
  }

  /**
   * Return a new DStream in which each RDD is generated by applying a function
   * on each RDD of 'this' DStream.
   */
  def transform[K2, V2](transformFunc: JFunction2[R, Time, JavaPairRDD[K2, V2]]):
  JavaPairDStream[K2, V2] = {
    implicit val cmk: ClassTag[K2] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[K2]]
    implicit val cmv: ClassTag[V2] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[V2]]
    def scalaTransform (in: RDD[T], time: Time): RDD[(K2, V2)] =
      transformFunc.call(wrapRDD(in), time).rdd
    dstream.transform(scalaTransform(_, _))
  }

  /**
   * Return a new DStream in which each RDD is generated by applying a function
   * on each RDD of 'this' DStream and 'other' DStream.
   */
  def transformWith[U, W](
      other: JavaDStream[U],
      transformFunc: JFunction3[R, JavaRDD[U], Time, JavaRDD[W]]
    ): JavaDStream[W] = {
    implicit val cmu: ClassTag[U] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[U]]
    implicit val cmv: ClassTag[W] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[W]]
    def scalaTransform (inThis: RDD[T], inThat: RDD[U], time: Time): RDD[W] =
      transformFunc.call(wrapRDD(inThis), other.wrapRDD(inThat), time).rdd
    dstream.transformWith[U, W](other.dstream, scalaTransform(_, _, _))
  }

  /**
   * Return a new DStream in which each RDD is generated by applying a function
   * on each RDD of 'this' DStream and 'other' DStream.
   */
  def transformWith[U, K2, V2](
      other: JavaDStream[U],
      transformFunc: JFunction3[R, JavaRDD[U], Time, JavaPairRDD[K2, V2]]
    ): JavaPairDStream[K2, V2] = {
    implicit val cmu: ClassTag[U] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[U]]
    implicit val cmk2: ClassTag[K2] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[K2]]
    implicit val cmv2: ClassTag[V2] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[V2]]
    def scalaTransform (inThis: RDD[T], inThat: RDD[U], time: Time): RDD[(K2, V2)] =
      transformFunc.call(wrapRDD(inThis), other.wrapRDD(inThat), time).rdd
    dstream.transformWith[U, (K2, V2)](other.dstream, scalaTransform(_, _, _))
  }

  /**
   * Return a new DStream in which each RDD is generated by applying a function
   * on each RDD of 'this' DStream and 'other' DStream.
   */
  def transformWith[K2, V2, W](
      other: JavaPairDStream[K2, V2],
      transformFunc: JFunction3[R, JavaPairRDD[K2, V2], Time, JavaRDD[W]]
    ): JavaDStream[W] = {
    implicit val cmk2: ClassTag[K2] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[K2]]
    implicit val cmv2: ClassTag[V2] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[V2]]
    implicit val cmw: ClassTag[W] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[W]]
    def scalaTransform (inThis: RDD[T], inThat: RDD[(K2, V2)], time: Time): RDD[W] =
      transformFunc.call(wrapRDD(inThis), other.wrapRDD(inThat), time).rdd
    dstream.transformWith[(K2, V2), W](other.dstream, scalaTransform(_, _, _))
  }

  /**
   * Return a new DStream in which each RDD is generated by applying a function
   * on each RDD of 'this' DStream and 'other' DStream.
   */
  def transformWith[K2, V2, K3, V3](
      other: JavaPairDStream[K2, V2],
      transformFunc: JFunction3[R, JavaPairRDD[K2, V2], Time, JavaPairRDD[K3, V3]]
    ): JavaPairDStream[K3, V3] = {
    implicit val cmk2: ClassTag[K2] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[K2]]
    implicit val cmv2: ClassTag[V2] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[V2]]
    implicit val cmk3: ClassTag[K3] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[K3]]
    implicit val cmv3: ClassTag[V3] =
      implicitly[ClassTag[AnyRef]].asInstanceOf[ClassTag[V3]]
    def scalaTransform (inThis: RDD[T], inThat: RDD[(K2, V2)], time: Time): RDD[(K3, V3)] =
      transformFunc.call(wrapRDD(inThis), other.wrapRDD(inThat), time).rdd
    dstream.transformWith[(K2, V2), (K3, V3)](other.dstream, scalaTransform(_, _, _))
  }

  /**
   * Enable periodic checkpointing of RDDs of this DStream.
   * @param interval Time interval after which generated RDD will be checkpointed
   */
  def checkpoint(interval: Duration) = {
    dstream.checkpoint(interval)
  }
}
