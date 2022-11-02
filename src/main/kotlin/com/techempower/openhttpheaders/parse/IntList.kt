package com.techempower.openhttpheaders.parse

class IntList {
  private var ints = IntArray(DEFAULT_CAPACITY)
  // Similar to `size` from List, this should appear to be a val
  val size: Int
    get() {
      return listSize
    }
  private var listSize = 0

  // Entirely copied from
  // https://hg.openjdk.java.net/jdk/jdk/file/ee1d592a9f53/src/java.base/share/classes/java/util/ArrayList.java#l231
  // and adapted for Kotlin
  private fun grow(minCapacity: Int): IntArray {
    val oldCapacity: Int = ints.size
    ints = if (oldCapacity > 0) {
      listOf<Int>().size
      val newCapacity = newLength(
          oldCapacity,
          minCapacity - oldCapacity,  /* minimum growth */
          oldCapacity shr 1 /* preferred growth */
      )
      ints.copyOf(newCapacity)
    } else {
      IntArray(DEFAULT_CAPACITY.coerceAtLeast(minCapacity))
    }
    return ints
  }

  /**
   * Replaces the element at the specified position in this list with the
   * specified element.
   *
   * @param index index of the element to replace
   * @param value element to be stored at the specified position
   * @return the element previously at the specified position
   * @throws IndexOutOfBoundsException if the index is out of range
   *         (`index < 0 || index >= size`)
   */
  operator fun set(index: Int, value: Int) {
    if (index >= listSize) {
      throw IndexOutOfBoundsException(index)
    }
    ints[index] = value
  }

  /**
   * Returns the element at the specified position in this list.
   *
   * @param index index of the element to return
   * @return the element at the specified position in this list
   * @throws IndexOutOfBoundsException if the index is out of range
   *         (`index < 0 || index >= size`)
   */
  operator fun get(index: Int): Int {
    if (index >= listSize) {
      throw IndexOutOfBoundsException(index)
    }
    return ints[index]
  }

  /**
   * Appends the specified element to the end of this list.
   *
   * @param value element to be appended to this list
   */
  operator fun plusAssign(value: Int) {
    if (listSize == ints.size) {
      grow(ints.size + 1)
    }
    ints[listSize] = value
    listSize += 1
  }

  /**
   * Removes from this list all elements at or after the specified index.
   *
   * @param index the index at and after which to remove elements
   * @throws IndexOutOfBoundsException if the index is out of range
   *         (`index < 0 || index >= size`)
   */
  fun truncate(index: Int) {
    if (index >= listSize) {
      throw IndexOutOfBoundsException(index)
    }
    // No need to reduce the size of the backing array, just change `listSize`
    // in order to consider all elements after `listSize` unpopulated.
    listSize = index
  }

  companion object {
    private const val DEFAULT_CAPACITY = 10

    // Entirely copied from
    // https://hg.openjdk.java.net/jdk/jdk/file/ee1d592a9f53/src/java.base/share/classes/jdk/internal/util/ArraysSupport.java#l614
    // and adapted for Kotlin
    private const val SOFT_MAX_ARRAY_LENGTH = Int.MAX_VALUE - 8

    // Entirely copied from
    // https://hg.openjdk.java.net/jdk/jdk/file/ee1d592a9f53/src/java.base/share/classes/jdk/internal/util/ArraysSupport.java#l636
    // and adapted for Kotlin
    private fun newLength(oldLength: Int, minGrowth: Int, prefGrowth: Int): Int {
      // preconditions not checked because of inlining
      // assert oldLength >= 0
      // assert minGrowth > 0
      val prefLength =
          oldLength + minGrowth.coerceAtLeast(prefGrowth) // might overflow
      return if (prefLength in 1..SOFT_MAX_ARRAY_LENGTH) {
        prefLength
      } else {
        // put code cold in a separate method
        hugeLength(oldLength, minGrowth)
      }
    }

    // Entirely copied from
    // https://hg.openjdk.java.net/jdk/jdk/file/ee1d592a9f53/src/java.base/share/classes/jdk/internal/util/ArraysSupport.java#l647
    // and adapted for Kotlin
    private fun hugeLength(oldLength: Int, minGrowth: Int): Int {
      val minLength = oldLength + minGrowth
      return if (minLength < 0) { // overflow
        throw OutOfMemoryError(
            "Required array length $oldLength + $minGrowth is too large"
        )
      } else if (minLength <= SOFT_MAX_ARRAY_LENGTH) {
        SOFT_MAX_ARRAY_LENGTH
      } else {
        minLength
      }
    }
  }
}
