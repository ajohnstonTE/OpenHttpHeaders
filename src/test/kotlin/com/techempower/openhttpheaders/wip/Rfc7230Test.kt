package com.techempower.openhttpheaders.wip

import com.techempower.openhttpheaders.parse.Tokenizer
import com.techempower.openhttpheaders.parse.TokenizerState
import com.techempower.openhttpheaders.parse.div
import com.techempower.openhttpheaders.parse.orMore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class Rfc7230Test : FunSpec({
  context("1#") {
    test("should require that ignorable elements are not considered by xOrMore grammars") {
      val binary = 1.orMore('0' / '1')
      val grammar = Rfc7230.`1#`(binary)
      Tokenizer("01").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe 0
        tokenizer.hasNext() shouldBe false
      }
      // Note that per the RFC grammar, OWS must be followed by a comma or
      // element, but the examples provided suggest that OWS can be followed by
      // the end of the match value. For that reason, the spec here will assume
      // that OWS can be followed by the end of the match value, even if the
      // RFC grammar/notation indicates otherwise.
      Tokenizer("01    ").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe 0
        tokenizer.hasNext() shouldBe false
      }
      Tokenizer("    01").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe 0
        tokenizer.hasNext() shouldBe false
      }
      Tokenizer(",").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe TokenizerState.NONE
      }
      Tokenizer(",    ").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe TokenizerState.NONE
      }
      Tokenizer("    ,    ").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe TokenizerState.NONE
      }
      Tokenizer("    ,").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe TokenizerState.NONE
      }
      Tokenizer("").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe TokenizerState.NONE
      }
      Tokenizer("     ").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe TokenizerState.NONE
      }
      Tokenizer(",1").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe 0
        tokenizer.hasNext() shouldBe false
      }
      Tokenizer(",    1").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe 0
        tokenizer.hasNext() shouldBe false
      }
      Tokenizer("    ,1").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe 0
        tokenizer.hasNext() shouldBe false
      }
      Tokenizer(",1     ").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe 0
        tokenizer.hasNext() shouldBe false
      }
      Tokenizer(",      1     ").also { tokenizer ->
        grammar.match(
            nodeIndex = TokenizerState.NONE,
            previousSiblingIndex = TokenizerState.NONE,
            tokenizer = tokenizer
        ) shouldBe 0
        tokenizer.hasNext() shouldBe false
      }
      Tokenizer("     ,      1     ").also { tokenizer ->
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
