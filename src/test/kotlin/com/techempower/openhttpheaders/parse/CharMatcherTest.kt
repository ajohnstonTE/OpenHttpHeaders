package com.techempower.openhttpheaders.parse

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CharMatcherTest : FunSpec({
  context("charMatcher") {
    class TestCase(
      val name: String,
      val charMatcher: CharMatcher,
      val char: Char,
      val matches: Boolean
    )
    context("ascii") {
      withData(
        nameFn = { it.name },
        listOf(
          TestCase(
            name = "should match the first ascii character",
            charMatcher = CharMatcher.ASCII,
            char = '\u0000',
            matches = true
          ),
          TestCase(
            name = "should match the last ascii character",
            charMatcher = CharMatcher.ASCII,
            char = '\u007F',
            matches = true
          ),
          TestCase(
            name = "should not match the first non-ascii character",
            charMatcher = CharMatcher.ASCII,
            char = '\u0080',
            matches = false
          )
        )
      ) {
        it.charMatcher.matches(it.char) shouldBe it.matches
      }
    }
    context("non-ascii") {
      withData(
        nameFn = { it.name },
        listOf(
          TestCase(
            name = "should match the first non-ascii character",
            charMatcher = CharMatcher.NON_ASCII,
            char = '\u0080',
            matches = true
          ),
          TestCase(
            name = "should not match the last ascii character",
            charMatcher = CharMatcher.NON_ASCII,
            char = '\u007F',
            matches = false
          )
        )
      ) {
        it.charMatcher.matches(it.char) shouldBe it.matches
      }
    }
    context("numbers") {
      withData(
        nameFn = { it.name },
        listOf(
          TestCase(
            name = "should match the first number character",
            charMatcher = CharMatcher.NUMBERS,
            char = '0',
            matches = true
          ),
          TestCase(
            name = "should match the last number character",
            charMatcher = CharMatcher.NUMBERS,
            char = '9',
            matches = true
          ),
          TestCase(
            name = "should not match the character before the first number character",
            charMatcher = CharMatcher.NUMBERS,
            char = '0' - 1,
            matches = false
          ),
          TestCase(
            name = "should not match the first non-number character",
            charMatcher = CharMatcher.NUMBERS,
            char = '9' + 1,
            matches = false
          )
        )
      ) {
        it.charMatcher.matches(it.char) shouldBe it.matches
      }
    }
    context("letters") {
      withData(
        nameFn = { it.name },
        listOf(
          TestCase(
            name = "should match the first upper-case letter character",
            charMatcher = CharMatcher.LETTERS,
            char = 'A',
            matches = true
          ),
          TestCase(
            name = "should match the last upper-case letter character",
            charMatcher = CharMatcher.LETTERS,
            char = 'Z',
            matches = true
          ),
          TestCase(
            name = "should not match the character before the first upper-case letter character",
            charMatcher = CharMatcher.LETTERS,
            char = 'A' - 1,
            matches = false
          ),
          TestCase(
            name = "should not match the character after the last first upper-case letter character",
            charMatcher = CharMatcher.LETTERS,
            char = 'Z' + 1,
            matches = false
          ),
          TestCase(
            name = "should match the first lower-case letter character",
            charMatcher = CharMatcher.LETTERS,
            char = 'a',
            matches = true
          ),
          TestCase(
            name = "should match the last lower-case letter character",
            charMatcher = CharMatcher.LETTERS,
            char = 'z',
            matches = true
          ),
          TestCase(
            name = "should not match the character before the first lower-case letter character",
            charMatcher = CharMatcher.LETTERS,
            char = 'a' - 1,
            matches = false
          ),
          TestCase(
            name = "should not match the character after the last first lower-case letter character",
            charMatcher = CharMatcher.LETTERS,
            char = 'z' + 1,
            matches = false
          )
        )
      ) {
        it.charMatcher.matches(it.char) shouldBe it.matches
      }
    }
    context("anyOf") {
      test("should work for basic cases") {
        val charMatcher = charMatcher {
          anyOf(" \t\n")
        }
        charMatcher.matches(' ') shouldBe true
        charMatcher.matches('\t') shouldBe true
        charMatcher.matches('\n') shouldBe true
        charMatcher.matches('a') shouldBe false
      }
    }
    test("precedence rules should be respected") {
      val matcher = charMatcher {
        include {
          group(CharMatcher.LETTERS)
          group(CharMatcher.NUMBERS)
          exclude {
            range('3' to '7', endExclusive = true)
          }
          include {
            char('5')
          }
        }
      }
      matcher.matches('2') shouldBe true
      matcher.matches('3') shouldBe false
      matcher.matches('4') shouldBe false
      matcher.matches('5') shouldBe true
      matcher.matches('6') shouldBe false
      matcher.matches('7') shouldBe true
      matcher.matches('8') shouldBe true
    }
    context("nesting should not be a problem") {
      val charMatcher = charMatcher {
        any()
        exclude {
          include {
            include {
              anyOf(" bcd")
              exclude {
                char('d')
              }
            }
          }
          exclude {
            char('b')
          }
        }
      }

      class TestCase(
        val char: Char,
        val matches: Boolean)
      withData(
        nameFn = { "'${it.char}'" },
        listOf(
          TestCase(
            char = ' ',
            matches = false
          ),
          TestCase(
            char = 'a',
            matches = true
          ),
          TestCase(
            char = 'b',
            matches = true
          ),
          TestCase(
            char = 'c',
            matches = false
          ),
          TestCase(
            char = 'd',
            matches = true
          ),
        )
      ) {
        charMatcher.matches(it.char) shouldBe it.matches
      }
    }
    context("collapsing") {
      test("should be able to fully collapse a CharMatcher") {
        val charMatcher = charMatcher {
          // Standard collapsing
          anyOf("abc")
          char('e')
          range('x' to 'z')
          // Nested `include` within another `include`
          include {
            range('4' to '6')
            // Nested `exclude` within an `include`
            exclude {
              anyOf("xy")
              char('5')
            }
          }
          // Nested `include` that collapses to nothing as the internal set is empty
          include {
            range('f' to 'h')
            exclude {
              range('f' to 'i')
            }
          }
          // Nested `exclude` that collapses to nothing as the internal set is empty
          exclude {
            range('b' to 'c')
            exclude {
              range('a' to 'd')
            }
          }
          // Nested `exclude` within an `include`
          exclude {
            char('a')
            // Nested `include` within an `exclude`
            include {
              anyOf("b")
              range('1' to '9')
            }
            exclude {
              char('a')
              anyOf("14")
            }
          }
        }
        charMatcher.shouldBeInstanceOf<SetCharMatcher>()
        // And, for sanity, check the matches
        charMatcher.matches('a') shouldBe true
        charMatcher.matches('c') shouldBe true
        charMatcher.matches('x') shouldBe true
        charMatcher.matches('y') shouldBe true
        charMatcher.matches('z') shouldBe true
        charMatcher.matches('4') shouldBe true
        charMatcher.matches('5') shouldBe false
        charMatcher.matches('6') shouldBe false
        charMatcher.matches('b') shouldBe false
        charMatcher.matches('f') shouldBe false
        charMatcher.matches('g') shouldBe false
        charMatcher.matches('h') shouldBe false
      }
      test("should not collapse a range matcher into a set matcher if the range is too large") {
        val charMatcher = charMatcher {
          range('\u0001' to '\u0402') // 1024 chars is the current limit, to avoid incredibly large sets
        }
        charMatcher.shouldBeInstanceOf<RangeCharMatcher>()
        // And, for sanity, check the matches
        charMatcher.matches('\u0000') shouldBe false
        charMatcher.matches('\u0001') shouldBe true
        charMatcher.matches('\u0002') shouldBe true
        charMatcher.matches('\u0401') shouldBe true
        charMatcher.matches('\u0402') shouldBe true
        charMatcher.matches('\u0403') shouldBe false
      }
      context("should not cause problems") {
        class TestCase(
          val charMatcher: CharMatcher,
          val char: Char,
          val matches: Boolean)
        context("for includes") {
          val charMatcher = charMatcher {
            anyOf("abc")
            char('e')
            range('x' to 'z')
          }
          withData(
            nameFn = { "'${it.char}'" },
            listOf(
              TestCase(
                charMatcher = charMatcher,
                char = 'a' - 1,
                matches = false
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'a',
                matches = true
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'b',
                matches = true
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'c',
                matches = true
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'd',
                matches = false
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'e',
                matches = true
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'f',
                matches = false
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'w',
                matches = false
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'x',
                matches = true
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'y',
                matches = true
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'z',
                matches = true
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'z' + 1,
                matches = false
              ),
            )
          ) {
            it.charMatcher.matches(it.char) shouldBe it.matches
          }
        }
        context("for excludes") {
          val charMatcher = charMatcher {
            any()
            exclude {
              anyOf("abc")
              char('e')
              range('x' to 'z')
            }
          }
          withData(
            nameFn = { "'${it.char}'" },
            listOf(
              TestCase(
                charMatcher = charMatcher,
                char = 'a' - 1,
                matches = true
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'a',
                matches = false
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'b',
                matches = false
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'c',
                matches = false
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'd',
                matches = true
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'e',
                matches = false
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'f',
                matches = true
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'w',
                matches = true
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'x',
                matches = false
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'y',
                matches = false
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'z',
                matches = false
              ),
              TestCase(
                charMatcher = charMatcher,
                char = 'z' + 1,
                matches = true
              ),
            )
          ) {
            it.charMatcher.matches(it.char) shouldBe it.matches
          }
        }
      }
    }
  }
})
