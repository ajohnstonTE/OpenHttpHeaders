package com.techempower.openhttpheaders.parse

import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TokenizerStateImplTest : FunSpec({
  test("add/get[GrammarBlockValue]") {
    val state = TokenizerStateImpl()
    state.add(
        grammarId = 2,
        grammarValue = 5,
        rangeStartInclusive = 8,
        rangeEndExclusive = 11,
        numberOfChildren = 15,
    )
    state.add(
        grammarId = 22,
        grammarValue = 55,
        rangeStartInclusive = 88,
        rangeEndExclusive = 111,
        numberOfChildren = 155,
    )
    state.getGrammarId(0) shouldBe 2
    state.getGrammarValue(0) shouldBe 5
    state.getRangeStartInclusive(0) shouldBe 8
    state.getRangeEndExclusive(0) shouldBe 11
    state.getNumberOfChildren(0) shouldBe 15

    state.getGrammarId(1) shouldBe 22
    state.getGrammarValue(1) shouldBe 55
    state.getRangeStartInclusive(1) shouldBe 88
    state.getRangeEndExclusive(1) shouldBe 111
    state.getNumberOfChildren(1) shouldBe 155
  }
  test("set/getCurrentIndex") {
    val state = TokenizerStateImpl()
    state.getInputIndex() shouldBe 0 // by default
    state.setInputIndex(5)
    state.getInputIndex() shouldBe 5
  }
  test("save/restore") {
    val state = TokenizerStateImpl()
    state.add(1, 2, 3, 4, 5)
    state.setInputIndex(6)
    val savePoint = state.save()
    state.add(6, 7, 8, 9, 0)
    state.setInputIndex(8)

    state.getInputIndex() shouldBe 8

    state.getGrammarId(0) shouldBe 1
    state.getGrammarValue(0) shouldBe 2
    state.getRangeStartInclusive(0) shouldBe 3
    state.getRangeEndExclusive(0) shouldBe 4
    state.getNumberOfChildren(0) shouldBe 5

    state.getGrammarId(1) shouldBe 6
    state.getGrammarValue(1) shouldBe 7
    state.getRangeStartInclusive(1) shouldBe 8
    state.getRangeEndExclusive(1) shouldBe 9
    state.getNumberOfChildren(1) shouldBe 0

    state.restore(savePoint)

    state.getInputIndex() shouldBe 6

    state.getGrammarId(0) shouldBe 1
    state.getGrammarValue(0) shouldBe 2
    state.getRangeStartInclusive(0) shouldBe 3
    state.getRangeEndExclusive(0) shouldBe 4
    state.getNumberOfChildren(0) shouldBe 5

    shouldThrowUnit<IndexOutOfBoundsException> {
      state.getGrammarId(1)
    }
    shouldThrowUnit<IndexOutOfBoundsException> {
      state.getGrammarValue(1)
    }
    shouldThrowUnit<IndexOutOfBoundsException> {
      state.getRangeStartInclusive(1)
    }
    shouldThrowUnit<IndexOutOfBoundsException> {
      state.getRangeEndExclusive(1)
    }
    shouldThrowUnit<IndexOutOfBoundsException> {
      state.getNumberOfChildren(1)
    }
  }
})
