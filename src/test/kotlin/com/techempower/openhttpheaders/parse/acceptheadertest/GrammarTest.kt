package com.techempower.openhttpheaders.parse.acceptheadertest

import com.techempower.openhttpheaders.parse.Tokenizer
import com.techempower.openhttpheaders.parse.TokenizerState
import com.techempower.openhttpheaders.parse.charMatcher
import com.techempower.openhttpheaders.wip.parse.div
import com.techempower.openhttpheaders.wip.parse.of
import com.techempower.openhttpheaders.wip.parse.optional
import com.techempower.openhttpheaders.wip.parse.orMore
import com.techempower.openhttpheaders.wip.parse.plus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GrammarTest : FunSpec({
  context("xOrMore") {
    test("simple") {
      val grammar = 2.orMore('y')
      Tokenizer("yyy").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe 0
        tokenizer.hasNext() shouldBe false
      }
    }
  }
  context("capture") {
    val grammar = 1.orMore(charMatcher { range('0'..'9') })
        .capture { it.value.toInt() }
    test("match found") {
      Tokenizer("1009").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe 0
        tokenizer.hasNext() shouldBe false
      }
    }
    test("no match found") {
      Tokenizer("yyyyy").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe TokenizerState.NONE
        tokenizer.state.getInputIndex() shouldBe 0
      }
    }
    test("parse") {
      grammar.parse("109") shouldBe 109
    }
  }
  context("andThen") {
    val token = 1.orMore('1' / '2')
    val token2 = 1.orMore('3' / '4')
    val grammar = (token + token2)
        .capture { it.value.toInt() }
    grammar.parse("122344") shouldBe 122344
  }
  context("back-off/retry") {
    test("simple") {
      val grammar = (0.orMore('1') + '1')
          .capture { it.value.toInt() }
      grammar.parse("1111") shouldBe 1111
    }
  }
  context("optional") {
    test("simple").config(timeout = null) {
      val token = 1.orMore('1' / '2')
      val token2 = 1.orMore('3' / '4')
      val ows = optional(1.orMore(' '))
      val grammar = token + ows + token2
      Tokenizer("1234").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe 0
        tokenizer.hasNext() shouldBe false
      }
      Tokenizer("12 34").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe 0
        tokenizer.hasNext() shouldBe false
      }
      Tokenizer("1324").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe 0
        // It does match, just not the whole string. It will go as far as it
        // can then give up, as a parent might be able to complete the rest.
        tokenizer.state.getInputIndex() shouldBe 2
      }
    }
  }
  context("range") {
    test("simple") {
      val token = (2..3).of('1' / '2')
      val token2 = 1.orMore('3' / '4')
      val ows = optional(1.orMore(' '))
      val grammar = token + token2
      Tokenizer("1234").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe 0
        tokenizer.hasNext() shouldBe false
      }
    }
  }
})
