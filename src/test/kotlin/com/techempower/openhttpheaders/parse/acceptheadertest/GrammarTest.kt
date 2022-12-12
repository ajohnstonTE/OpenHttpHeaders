package com.techempower.openhttpheaders.parse.acceptheadertest

import com.techempower.openhttpheaders.parse.CharMatcherGrammar
import com.techempower.openhttpheaders.parse.Grammar
import com.techempower.openhttpheaders.parse.Tokenizer
import com.techempower.openhttpheaders.parse.TokenizerState
import com.techempower.openhttpheaders.parse.XOrMoreGrammar
import com.techempower.openhttpheaders.parse.charMatcher
import com.techempower.openhttpheaders.parse.div
import com.techempower.openhttpheaders.parse.of
import com.techempower.openhttpheaders.parse.optional
import com.techempower.openhttpheaders.parse.orMore
import com.techempower.openhttpheaders.parse.plus
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

// Certain infix functions, namely `/`, will implicitly optimize down to
// CharMatchers for example. As these tests are not only about ensuring
// grammars work generally, but also about ensuring they work with  all
// variations of each other, the specific types should be exactly as  expected,
// and for that reason most-if-not-all grammars in this file will be cast to a
// specific grammar class.
class GrammarTest : FunSpec({
  val charMatcherGrammar = CharMatcherGrammar('1')
  val oneOrMoreGrammar = 1.orMore(charMatcherGrammar) as XOrMoreGrammar<String>

  class TestCase(val str: String)
  class PartialMatchTestCase(val str: String, val index: Int)

  context("xOrMore") {
    context("child - CharMatcherGrammar") {
      val grammar = 1.orMore(charMatcherGrammar) as XOrMoreGrammar<String>
      context("should fully parse valid strings") {
        withData(
            listOf(
                TestCase("1"),
                TestCase("111")
            )
        ) {
          grammar.shouldFullyMatch(it.str)
        }
      }
      context("should partially match partially valid strings") {
        withData(
            listOf(
                PartialMatchTestCase("12", index = 1),
                PartialMatchTestCase("1112", index = 3)
            )
        ) {
          grammar.shouldOnlyMatchUpTo(it.str, index = it.index)
        }
      }
      context("should not match invalid strings") {
        withData(
            listOf(
                TestCase("21"),
                TestCase("2111")
            )
        ) {
          grammar.shouldNotMatchAtAll(it.str)
        }
      }
    }
    context("child - StringGrammar") {
      TODO()
    }
    // TODO: As I'm probably going to want to follow the 3-context
    //  'full/partial/none' format for all tests, maybe turn that into a
    //  function that all tests can use.
  }

  context("xOrMore - old") {
    test("simple") {
      val grammar = 2.orMore('y')
      grammar.shouldFullyMatch("yyy")
    }
  }
  context("capture") {
    val grammar = 1.orMore(charMatcher { range('0'..'9') })
        .capture { it.value.toInt() }
    test("match found") {
      grammar.shouldFullyMatch("1009")
    }
    test("no match found") {
      grammar.shouldNotMatchAtAll("yyyyy")
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
      grammar.shouldFullyMatch("1234")
      grammar.shouldFullyMatch("12 34")
      // It does match, just not the whole string. It will go as far as it
      // can then give up, as a parent might be able to complete the rest.
      grammar.shouldOnlyMatchUpTo("1324", index = 2)
    }
  }
  context("range") {
    test("simple") {
      val token = (2..3).of('1' / '2')
      val token2 = 1.orMore('3' / '4')
      val grammar = token + token2
      grammar.shouldFullyMatch("1234")
    }
  }
})

private fun <T> Grammar<T>.shouldFullyMatch(str: String) {
  val firstNodeIndex = 0
  Tokenizer(str).also { tokenizer ->
    this.match(
        nodeIndex = TokenizerState.NONE,
        previousSiblingIndex = TokenizerState.NONE,
        tokenizer = tokenizer
    ) shouldBe firstNodeIndex
    tokenizer.hasNext() shouldBe false
  }
}

private fun <T> Grammar<T>.shouldOnlyMatchUpTo(str: String, index: Int) {
  val firstNodeIndex = 0
  Tokenizer(str).also { tokenizer ->
    this.match(
        nodeIndex = TokenizerState.NONE,
        previousSiblingIndex = TokenizerState.NONE,
        tokenizer = tokenizer
    ) shouldBe firstNodeIndex
    tokenizer.state.getInputIndex() shouldBe index
  }
}

private fun <T> Grammar<T>.shouldNotMatchAtAll(str: String) {
  Tokenizer(str).also { tokenizer ->
    this.match(
        nodeIndex = TokenizerState.NONE,
        previousSiblingIndex = TokenizerState.NONE,
        tokenizer = tokenizer
    ) shouldBe TokenizerState.NONE
    tokenizer.state.getInputIndex() shouldBe 0
  }
}
