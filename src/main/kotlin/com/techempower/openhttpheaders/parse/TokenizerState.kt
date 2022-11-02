package com.techempower.openhttpheaders.parse

@JvmInline
internal value class InlineTokenizerSavePoint(val value: Long)

internal interface TokenizerState {
  // Returns the index of the first value added
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
      // May not end up being necessary but support for now.
      numberOfChildren: Int,
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

  fun save(): InlineTokenizerSavePoint

  fun restore(savePoint: InlineTokenizerSavePoint)
}

internal class TokenizerStateImpl : TokenizerState {

  private val ints = IntList()

  init {
    // Current tokenizer index
    ints += 0
  }

  override fun add(
      grammarId: Int,
      grammarValue: Int,
      rangeStartInclusive: Int,
      rangeEndExclusive: Int,
      numberOfChildren: Int
  ): Int {
    ints += grammarId
    ints += grammarValue
    ints += rangeStartInclusive
    ints += rangeEndExclusive
    ints += numberOfChildren
    return  (ints.size - 1) / BLOCK_SIZE
  }

  override fun update(
      index: Int,
      grammarValue: Int,
      rangeStartInclusive: Int,
      rangeEndExclusive: Int,
      numberOfChildren: Int
  ) {
    val offsetIndex = offsetIndex(index)
    ints[offsetIndex + 1] = grammarValue
    ints[offsetIndex + 2] = rangeStartInclusive
    ints[offsetIndex + 3] = rangeEndExclusive
    ints[offsetIndex + 4] = numberOfChildren
  }

  override fun setInputIndex(index: Int) {
    ints[0] = index
  }

  override fun getInputIndex(): Int = ints[0]

  override fun isIndexPopulated(index: Int): Boolean {
    return ints.size > BLOCK_SIZE * index + 1
  }

  override fun getGrammarId(index: Int): Int = ints[offsetIndex(index)]

  override fun getGrammarValue(index: Int): Int = ints[offsetIndex(index) + 1]

  override fun getRangeStartInclusive(index: Int): Int =
      ints[offsetIndex(index) + 2]

  override fun getRangeEndExclusive(index: Int): Int =
      ints[offsetIndex(index) + 3]

  override fun getNumberOfChildren(index: Int): Int =
      ints[offsetIndex(index) + 4]

  private fun offsetIndex(index: Int) = BLOCK_SIZE * index + 1

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
    ints.truncate(intSize)
  }

  companion object {
    // Must be equal to the number of properties set in `add(...)`
    private const val BLOCK_SIZE = 5
  }
}
