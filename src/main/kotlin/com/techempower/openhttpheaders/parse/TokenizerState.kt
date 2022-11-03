package com.techempower.openhttpheaders.parse

@JvmInline
internal value class InlineTokenizerSavePoint(val value: Long)

internal interface TokenizerState {
  // Returns the index of the block added. Can be used to modify/retrieve
  // values for/from all other functions.
  fun add(
      // Unique per grammar instance. Incremented from 0 using a static
      // AtomicInteger in the Grammar class.
      grammarId: Int,
      // Used for things like the number of matches, or which a/b the OR
      // evaluated to, etc. Different meaning per grammar.
      grammarValue: Int,
      // Refers to the start/end of the text the grammar matched
      rangeStartInclusive: Int,
      rangeEndExclusive: Int,
      // Always used to indicate the number of children following the grammar.
      numberOfChildren: Int,
      // The index of the previous sibling. Not guaranteed to be the previous block over.
      previousSiblingIndex: Int,
  ): Int

  fun update(
      index: Int,
      grammarValue: Int,
      rangeStartInclusive: Int,
      rangeEndExclusive: Int,
      numberOfChildren: Int,
  )

  fun setInputIndex(index: Int)

  fun getInputIndex(): Int

  fun isIndexPopulated(index: Int): Boolean

  fun getGrammarId(index: Int): Int

  fun getGrammarValue(index: Int): Int

  fun getRangeStartInclusive(index: Int): Int

  fun getRangeEndExclusive(index: Int): Int

  fun getNumberOfChildren(index: Int): Int

  fun getPreviousSiblingIndex(index: Int): Int

  fun getNextSiblingIndex(index: Int): Int

  fun setNextSiblingIndex(index: Int, nextSiblingIndex: Int)

  fun getNthChildIndex(parentIndex: Int, childIndexFromParent: Int): Int

  fun save(): InlineTokenizerSavePoint

  fun restore(savePoint: InlineTokenizerSavePoint)

  companion object {
    const val NOT_FOUND = -1
    const val SKIP = -2
  }
}

internal class TokenizerStateImpl : TokenizerState {

  // Current tokenizer index
  private var inputIndex = 0
  private val ints = IntList()

  override fun add(
      grammarId: Int,
      grammarValue: Int,
      rangeStartInclusive: Int,
      rangeEndExclusive: Int,
      numberOfChildren: Int,
      previousSiblingIndex: Int
  ): Int {
    val index = ints.size / BLOCK_SIZE
    ints += grammarId
    ints += grammarValue
    ints += rangeStartInclusive
    ints += rangeEndExclusive
    ints += numberOfChildren
    ints += previousSiblingIndex
    ints += TokenizerState.NOT_FOUND
    return index
  }

  override fun update(
      index: Int,
      grammarValue: Int,
      rangeStartInclusive: Int,
      rangeEndExclusive: Int,
      numberOfChildren: Int,
  ) {
    val offsetIndex = offsetIndex(index)
    ints[offsetIndex + 1] = grammarValue
    ints[offsetIndex + 2] = rangeStartInclusive
    ints[offsetIndex + 3] = rangeEndExclusive
    ints[offsetIndex + 4] = numberOfChildren
  }

  override fun setInputIndex(index: Int) {
    inputIndex = index
  }

  override fun getInputIndex(): Int = inputIndex

  override fun isIndexPopulated(index: Int): Boolean {
    return index >= 0 && ints.size > offsetIndex(index)
  }

  override fun getGrammarId(index: Int): Int = ints[offsetIndex(index)]

  override fun getGrammarValue(index: Int): Int = ints[offsetIndex(index) + 1]

  override fun getRangeStartInclusive(index: Int): Int =
      ints[offsetIndex(index) + 2]

  override fun getRangeEndExclusive(index: Int): Int =
      ints[offsetIndex(index) + 3]

  override fun getNumberOfChildren(index: Int): Int =
      ints[offsetIndex(index) + 4]

  override fun getPreviousSiblingIndex(index: Int): Int =
      ints[offsetIndex(index) + 5]

  override fun getNextSiblingIndex(index: Int): Int =
      ints[offsetIndex(index) + 6]

  override fun setNextSiblingIndex(index: Int, nextSiblingIndex: Int) {
    ints[offsetIndex(index) + 6] = nextSiblingIndex
  }

  override fun getNthChildIndex(parentIndex: Int, childIndexFromParent: Int): Int {
    val numberOfChildren = getNumberOfChildren(parentIndex)
    if (numberOfChildren <= childIndexFromParent) {
      throw IndexOutOfBoundsException("Child index ($childIndexFromParent) " +
          "is not within the bounds of the parent index's number of " +
          "children ($numberOfChildren)")
    }
    // This for loop does not require allocation:
    // https://pancake.coffee/2021/08/12/kotlin-for-loop-with-no-additional-object-allocation/
    var nextIndex = parentIndex + 1
    for(i in 0 until childIndexFromParent) {
      nextIndex = getNextSiblingIndex(nextIndex)
    }
    return nextIndex
  }

  private fun offsetIndex(index: Int) = BLOCK_SIZE * index

  override fun save(): InlineTokenizerSavePoint {
    // Two ints to a long based on https://stackoverflow.com/a/12772968
    val inputIndex = getInputIndex()
    val intsSize = ints.size
    val value = (inputIndex.toLong() shl 32) or
        (intsSize.toLong() and 0xffffffffL)
    return InlineTokenizerSavePoint(value)
  }

  override fun restore(savePoint: InlineTokenizerSavePoint) {
    // Two ints from a long based on https://stackoverflow.com/a/12772968
    val value = savePoint.value
    val inputIndex = (value shr 32).toInt()
    val intSize = value.toInt()
    setInputIndex(inputIndex)
    val blockIndex = intSize / BLOCK_SIZE
    if (isIndexPopulated(blockIndex)) {
      val previousSiblingIndex = getPreviousSiblingIndex(blockIndex)
      if (isIndexPopulated(blockIndex)) {
        setNextSiblingIndex(previousSiblingIndex, -1)
      }
    }
    ints.truncate(intSize)
  }

  companion object {
    // Must be equal to the number of properties set in `add(...)`
    private const val BLOCK_SIZE = 7
  }
}
