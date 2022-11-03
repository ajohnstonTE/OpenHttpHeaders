package com.techempower.openhttpheaders.parse

import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TokenizerStateImplTest : FunSpec({
  test("reserve/get[GrammarBlockValue]") {
    val state = TokenizerStateImpl()
    state.reserve(
        grammarId = 2,
        previousSiblingIndex = 17,
    ) shouldBe 0
    state.reserve(
        grammarId = 22,
        previousSiblingIndex = 177,
    ) shouldBe 1
    state.getGrammarId(0) shouldBe 2
    state.getGrammarValue(0) shouldBe TokenizerState.NONE
    state.getRangeStartInclusive(0) shouldBe TokenizerState.NONE
    state.getRangeEndExclusive(0) shouldBe TokenizerState.NONE
    state.getNumberOfChildren(0) shouldBe 0
    state.getPreviousSiblingIndex(0) shouldBe 17
    state.getNextSiblingIndex(0) shouldBe TokenizerState.NONE

    state.getGrammarId(1) shouldBe 22
    state.getGrammarValue(1) shouldBe TokenizerState.NONE
    state.getRangeStartInclusive(1) shouldBe TokenizerState.NONE
    state.getRangeEndExclusive(1) shouldBe TokenizerState.NONE
    state.getNumberOfChildren(1) shouldBe 0
    state.getPreviousSiblingIndex(1) shouldBe 177
    state.getNextSiblingIndex(1) shouldBe TokenizerState.NONE
  }
  test("set/getNextSiblingIndex") {
    val state = TokenizerStateImpl()
    state.reserve(
        grammarId = 2,
        previousSiblingIndex = 17,
    )
    state.reserve(
        grammarId = 22,
        previousSiblingIndex = 177,
    )
    // Sanity check
    state.getNextSiblingIndex(0) shouldBe TokenizerState.NONE
    state.getNextSiblingIndex(1) shouldBe TokenizerState.NONE

    state.setNextSiblingIndex(0, 0)

    state.getNextSiblingIndex(0) shouldBe 0
    state.getNextSiblingIndex(1) shouldBe TokenizerState.NONE

    shouldThrowUnit<IndexOutOfBoundsException> {
      state.setNextSiblingIndex(2, 0)
    }
    shouldThrowUnit<IndexOutOfBoundsException> {
      state.getNextSiblingIndex(2)
    }
  }
  test("set/getCurrentIndex") {
    val state = TokenizerStateImpl()
    state.getInputIndex() shouldBe 0 // by default
    state.setInputIndex(5)
    state.getInputIndex() shouldBe 5
  }
  test("save/restore") {
    val state = TokenizerStateImpl()
    val index1 = state.reserve(
        grammarId = 1,
        previousSiblingIndex = TokenizerState.NONE
    )
    state.update(
        index = index1,
        grammarValue = 2,
        rangeStartInclusive = 3,
        rangeEndExclusive = 4,
        numberOfChildren = 5,
    )
    val index2 = state.reserve(
        grammarId = 5,
        previousSiblingIndex = 0
    )
    state.update(
        index = index2,
        grammarValue = 8,
        rangeStartInclusive = 11,
        rangeEndExclusive = 14,
        numberOfChildren = 15,
    )
    state.setNextSiblingIndex(0, 1)
    state.setInputIndex(6)
    val savePoint = state.save()
    val index3 = state.reserve(
        grammarId = 6,
        previousSiblingIndex = 1
    )
    state.update(
        index = index3,
        grammarValue = 7,
        rangeStartInclusive = 8,
        rangeEndExclusive = 9,
        numberOfChildren = 0,
    )
    state.setNextSiblingIndex(1, 2)
    state.setInputIndex(8)

    state.getInputIndex() shouldBe 8

    state.getGrammarId(0) shouldBe 1
    state.getGrammarValue(0) shouldBe 2
    state.getRangeStartInclusive(0) shouldBe 3
    state.getRangeEndExclusive(0) shouldBe 4
    state.getNumberOfChildren(0) shouldBe 5
    state.getPreviousSiblingIndex(0) shouldBe TokenizerState.NONE
    state.getNextSiblingIndex(0) shouldBe 1

    state.getGrammarId(1) shouldBe 5
    state.getGrammarValue(1) shouldBe 8
    state.getRangeStartInclusive(1) shouldBe 11
    state.getRangeEndExclusive(1) shouldBe 14
    state.getNumberOfChildren(1) shouldBe 15
    state.getPreviousSiblingIndex(1) shouldBe 0
    state.getNextSiblingIndex(1) shouldBe 2

    state.getGrammarId(2) shouldBe 6
    state.getGrammarValue(2) shouldBe 7
    state.getRangeStartInclusive(2) shouldBe 8
    state.getRangeEndExclusive(2) shouldBe 9
    state.getNumberOfChildren(2) shouldBe 0
    state.getPreviousSiblingIndex(2) shouldBe 1
    state.getNextSiblingIndex(2) shouldBe TokenizerState.NONE

    state.restore(savePoint)

    state.getInputIndex() shouldBe 6

    state.getGrammarId(0) shouldBe 1
    state.getGrammarValue(0) shouldBe 2
    state.getRangeStartInclusive(0) shouldBe 3
    state.getRangeEndExclusive(0) shouldBe 4
    state.getNumberOfChildren(0) shouldBe 5
    state.getPreviousSiblingIndex(0) shouldBe TokenizerState.NONE
    state.getNextSiblingIndex(0) shouldBe 1

    state.getGrammarId(1) shouldBe 5
    state.getGrammarValue(1) shouldBe 8
    state.getRangeStartInclusive(1) shouldBe 11
    state.getRangeEndExclusive(1) shouldBe 14
    state.getNumberOfChildren(1) shouldBe 15
    state.getPreviousSiblingIndex(1) shouldBe 0
    state.getNextSiblingIndex(1) shouldBe TokenizerState.NONE

    shouldThrowUnit<IndexOutOfBoundsException> {
      state.getGrammarId(2)
    }
    shouldThrowUnit<IndexOutOfBoundsException> {
      state.getGrammarValue(2)
    }
    shouldThrowUnit<IndexOutOfBoundsException> {
      state.getRangeStartInclusive(2)
    }
    shouldThrowUnit<IndexOutOfBoundsException> {
      state.getRangeEndExclusive(2)
    }
    shouldThrowUnit<IndexOutOfBoundsException> {
      state.getNumberOfChildren(2)
    }
  }
  test("isIndexPopulated") {
    val state = TokenizerStateImpl()
    state.isIndexPopulated(0) shouldBe false
    state.isIndexPopulated(1) shouldBe false
    state.reserve(1, TokenizerState.NONE)
    state.isIndexPopulated(0) shouldBe true
    state.isIndexPopulated(1) shouldBe false
    state.reserve(1, 2)
    state.isIndexPopulated(0) shouldBe true
    state.isIndexPopulated(1) shouldBe true
    state.isIndexPopulated(2) shouldBe false
  }
  test("update") {
    val state = TokenizerStateImpl()
    val index1 = state.reserve(
        grammarId = 2,
        previousSiblingIndex = TokenizerState.NONE
    )
    state.update(
        index = index1,
        grammarValue = 5,
        rangeStartInclusive = 8,
        rangeEndExclusive = 11,
        numberOfChildren = 15,
    )
    val index2 = state.reserve(
        grammarId = 22,
        previousSiblingIndex = 0
    )
    state.update(
        index = index2,
        grammarValue = 55,
        rangeStartInclusive = 88,
        rangeEndExclusive = 111,
        numberOfChildren = 155,
    )
    state.setNextSiblingIndex(0, 0)

    // Sanity check
    state.getGrammarId(0) shouldBe 2
    state.getGrammarValue(0) shouldBe 5
    state.getRangeStartInclusive(0) shouldBe 8
    state.getRangeEndExclusive(0) shouldBe 11
    state.getNumberOfChildren(0) shouldBe 15
    state.getPreviousSiblingIndex(0) shouldBe TokenizerState.NONE
    state.getNextSiblingIndex(0) shouldBe 0

    // Sanity check
    state.getGrammarId(1) shouldBe 22
    state.getGrammarValue(1) shouldBe 55
    state.getRangeStartInclusive(1) shouldBe 88
    state.getRangeEndExclusive(1) shouldBe 111
    state.getNumberOfChildren(1) shouldBe 155
    state.getPreviousSiblingIndex(1) shouldBe 0
    state.getNextSiblingIndex(1) shouldBe TokenizerState.NONE

    state.update(
        index = 0,
        grammarValue = 3,
        rangeStartInclusive = 5,
        rangeEndExclusive = 18,
        numberOfChildren = 19
    )

    state.getGrammarId(0) shouldBe 2
    state.getGrammarValue(0) shouldBe 3
    state.getRangeStartInclusive(0) shouldBe 5
    state.getRangeEndExclusive(0) shouldBe 18
    state.getNumberOfChildren(0) shouldBe 19
    state.getPreviousSiblingIndex(0) shouldBe TokenizerState.NONE
    state.getNextSiblingIndex(0) shouldBe 0

    state.update(
        index = 1,
        grammarValue = 33,
        rangeStartInclusive = 55,
        rangeEndExclusive = 188,
        numberOfChildren = 199
    )

    state.getGrammarId(1) shouldBe 22
    state.getGrammarValue(1) shouldBe 33
    state.getRangeStartInclusive(1) shouldBe 55
    state.getRangeEndExclusive(1) shouldBe 188
    state.getNumberOfChildren(1) shouldBe 199
    state.getPreviousSiblingIndex(1) shouldBe 0
    state.getNextSiblingIndex(1) shouldBe TokenizerState.NONE

    shouldThrowUnit<IndexOutOfBoundsException> {
      state.update(
          index = 2,
          grammarValue = 0,
          rangeStartInclusive = 0,
          rangeEndExclusive = 0,
          numberOfChildren = 0
      )
    }
  }
  test("getNthChildIndex") {
    val state = TokenizerStateImpl()
    // Note that this functionality is also dependent on the grammars using it
    // correctly. If a block is added in an unexpected order, this will break.
    // With that in mind, this test will reflect the intended usage.

    // Parent grammar adds itself before checking to see if any children match
    val parentIndex = state.reserve(
        grammarId = 2,
        previousSiblingIndex = TokenizerState.NONE
    )
    // Child #1 matches
    val child1Index = state.reserve(
        grammarId = 7,
        previousSiblingIndex = TokenizerState.NONE
    )
    // Grandchild 1, should be skipped over
    val grandChild1Index = state.reserve(
        grammarId = 8,
        previousSiblingIndex = TokenizerState.NONE
    )
    state.update(
        index = grandChild1Index,
        grammarValue = 8,
        rangeStartInclusive = 10,
        rangeEndExclusive = 18,
        numberOfChildren = 0,
    )
    // Child #1, having matched, updates itself
    state.update(
        index = child1Index,
        grammarValue = 8,
        rangeStartInclusive = 10,
        rangeEndExclusive = 18,
        numberOfChildren = 1
    )
    // Child #1 matches
    val child2Index = state.reserve(
        grammarId = 9,
        previousSiblingIndex = child1Index
    )
    // Grandchild 2, should be skipped over
    val grandChild2Index = state.reserve(
        grammarId = 13,
        previousSiblingIndex = TokenizerState.NONE
    )
    state.update(
        index = grandChild2Index,
        grammarValue = 5,
        rangeStartInclusive = 18,
        rangeEndExclusive = 23,
        numberOfChildren = 0,
    )
    // Child #2, having matched, updates itself
    state.update(
        index = child2Index,
        grammarValue = 5,
        rangeStartInclusive = 18,
        rangeEndExclusive = 23,
        numberOfChildren = 1
    )
    // The parent now updates the first child to be aware of the second
    state.setNextSiblingIndex(child1Index, child2Index)
    // The parent, now done matching, updates itself
    state.update(
        index = parentIndex,
        grammarValue = 2,
        rangeStartInclusive = 10,
        rangeEndExclusive = 23,
        numberOfChildren = 2
    )

    state.getNthChildIndex(parentIndex, 0) shouldBe child1Index
    state.getNthChildIndex(parentIndex, 1) shouldBe child2Index

    shouldThrowUnit<IndexOutOfBoundsException> {
      state.getNthChildIndex(parentIndex, 2)
    }
  }
})
