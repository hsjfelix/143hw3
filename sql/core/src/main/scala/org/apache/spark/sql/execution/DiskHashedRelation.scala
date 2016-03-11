package org.apache.spark.sql.execution

import java.io._
import java.nio.file.{Path, StandardOpenOption, Files}
import java.util.{ArrayList => JavaArrayList}

import org.apache.spark.SparkException
import org.apache.spark.sql.catalyst.expressions.{Projection, Row}
import org.apache.spark.sql.execution.CS143Utils._

import scala.collection.JavaConverters._

/**
 * This trait represents a regular relation that is hash partitioned and spilled to
 * disk.
 */
private[sql] sealed trait DiskHashedRelation {
  /**
   *
   * @return an iterator of the [[DiskPartition]]s that make up this relation.
   */
  def getIterator(): Iterator[DiskPartition]

  /**
   * Close all the partitions for this relation. This should involve deleting the files hashed into.
   */
  def closeAllPartitions()
}

/**
 * A general implementation of [[DiskHashedRelation]].
 *
 * @param partitions the disk partitions that we are going to spill to
 */
protected [sql] final class GeneralDiskHashedRelation(partitions: Array[DiskPartition])
    extends DiskHashedRelation with Serializable {

  override def getIterator() : Iterator[DiskPartition] = {
    var Partition_list: JavaArrayList[DiskPartition] = new JavaArrayList[DiskPartition]()
    for(i <- 0 until partitions.length){
        partitions(i).closeInput()
        Partition_list.add(partitions(i))
    }
    
    return Partition_list.iterator().asScala
  }

  override def closeAllPartitions() = {
    for(i <- 0 until partitions.length){
        
        partitions(i).closePartition()
    }
  }
}

private[sql] class DiskPartition (
                                  filename: String,
                                  blockSize: Int) {
  private val path: Path = Files.createTempFile("", filename)
  private val data: JavaArrayList[Row] = new JavaArrayList[Row]
  private val outStream: OutputStream = Files.newOutputStream(path)
  private val inStream: InputStream = Files.newInputStream(path)
  private val chunkSizes: JavaArrayList[Int] = new JavaArrayList[Int]()
  private var writtenToDisk: Boolean = false
  private var inputClosed: Boolean = false
  private var iterator_inite: Boolean = false
  /**
   * This method inserts a new row into this particular partition. If the size of the partition
   * exceeds the blockSize, the partition is spilled to disk.
   *
   * @param row the [[Row]] we are adding
   */
  def insert(row: Row) = {
    if(inputClosed){
      throw new SparkException("input closed already!")
    }
    data.add(row)
    if( measurePartitionSize()> blockSize){
        spillPartitionToDisk()
        data.clear()
    }


  }

  /**
   * This method converts the data to a byte array and returns the size of the byte array
   * as an estimation of the size of the partition.
   *
   * @return the estimated size of the data
   */
  private[this] def measurePartitionSize(): Int = {
    CS143Utils.getBytesFromList(data).size
  }

  /**
   * Uses the [[Files]] API to write a byte array representing data to a file.
   */
  private[this] def spillPartitionToDisk() = {
    val bytes: Array[Byte] = getBytesFromList(data)

    // This array list stores the sizes of chunks written in order to read them back correctly.
    chunkSizes.add(bytes.size)

    Files.write(path, bytes, StandardOpenOption.APPEND)
    writtenToDisk = true
  }

  /**
   * If this partition has been closed, this method returns an Iterator of all the
   * data that was written to disk by this partition.
   *
   * @return the [[Iterator]] of the data
   */
  def getData(): Iterator[Row] = {
    if (!inputClosed) {
      throw new SparkException("Should not be reading from file before closing input. Bad things will happen!")
    }

    new Iterator[Row] {
      var currentIterator: Iterator[Row] = data.iterator.asScala
      val chunkSizeIterator: Iterator[Int] = chunkSizes.iterator().asScala
      var byteArray: Array[Byte] = null
      var temp_data: JavaArrayList[Row] = new JavaArrayList[Row]
      override def next() : Row = {
        if(hasNext()){
            return currentIterator.next()
        }
        return null
      }

      override def hasNext() : Boolean= {
        if(!iterator_inite){
            if(!fetchNextChunk()){
              return false
            }
            iterator_inite = true
        }
        if(currentIterator.hasNext){
          return true
        }
        else{
          if(fetchNextChunk()){
            return true
          }
          else{
            return false
          }
        }
      }

      /**
       * Fetches the next chunk of the file and updates the iterator. Should return true
       * unless the iterator is empty.
       *
       * @return true unless the iterator is empty.
       */
      private[this] def fetchNextChunk(): Boolean = {
        var current_chunksize = 0
        if(chunkSizeIterator.hasNext){
            current_chunksize = chunkSizeIterator.next()
        }
        else{
          return false
        }
        byteArray = CS143Utils.getNextChunkBytes(inStream,current_chunksize,byteArray)
        temp_data = CS143Utils.getListFromBytes(byteArray)
        currentIterator = temp_data.iterator.asScala
        return true
      }
    }
  }

  /**
   * Closes this partition, implying that no more data will be written to this partition. If getData()
   * is called without closing the partition, an error will be thrown.
   *
   * If any data has not been written to disk yet, it should be written. The output stream should
   * also be closed.
   */
  def closeInput() = {
    //closePartition()
    if(!data.isEmpty()){
      spillPartitionToDisk()
    }
    outStream.close()
    inputClosed = true
  }


  /**
   * Closes this partition. This closes the input stream and deletes the file backing the partition.
   */
  private[sql] def closePartition() = {
    inStream.close()
    Files.deleteIfExists(path)
  }
}

private[sql] object DiskHashedRelation {

  /**
   * Given an input iterator, partitions each row into one of a number of [[DiskPartition]]s
   * and constructors a [[DiskHashedRelation]].
   *
   * This executes the first phase of external hashing -- using a course-grained hash function
   * to partition the tuples to disk.
   *
   * The block size is approximately set to 64k because that is a good estimate of the average
   * buffer page.
   *
   * @param input the input [[Iterator]] of [[Row]]s
   * @param keyGenerator a [[Projection]] that generates the keys for the input
   * @param size the number of [[DiskPartition]]s
   * @param blockSize the threshold at which each partition will spill
   * @return the constructed [[DiskHashedRelation]]
   */
  def apply (
                input: Iterator[Row],
                keyGenerator: Projection,
                size:Int = 64,
                blockSize:Int = 64000) : GeneralDiskHashedRelation = {
    var  partitions : Array[DiskPartition] = new Array[DiskPartition](size)
    val  r_value = scala.util.Random
    val  r_name  = r_value.toString
    for(i <- 0 until partitions.length ){
        val name = i.toString + "-" + r_name
        partitions(i) = new DiskPartition(name,blockSize)
    }
    while(input.hasNext){
        var temp_row = input.next()
        var hash = temp_row.hashCode() % size
        partitions(hash).insert(temp_row)
    }
    val result = new GeneralDiskHashedRelation(partitions)
    return result
  }
}
