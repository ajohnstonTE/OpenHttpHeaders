// All unused functions may end up being necessary at any time. This file
// includes many variations of functions for completeness.
@file:Suppress("unused")

package com.techempower.openhttpheaders.wip.parse

import com.techempower.openhttpheaders.parse.AndThenGrammar
import com.techempower.openhttpheaders.parse.CharMatcher
import com.techempower.openhttpheaders.parse.CharMatcherGrammar
import com.techempower.openhttpheaders.parse.Grammar
import com.techempower.openhttpheaders.parse.OrCountGrammar
import com.techempower.openhttpheaders.parse.OrGrammar
import com.techempower.openhttpheaders.parse.RangeGrammar
import com.techempower.openhttpheaders.parse.SingleCaptureContext
import com.techempower.openhttpheaders.parse.XOrMoreGrammar
import com.techempower.openhttpheaders.parse.charMatcher

internal fun <T> Int.orMore(value: Grammar<T>): Grammar<T> =
    XOrMoreGrammar(value, this)

internal fun Int.orMore(value: CharMatcher): Grammar<String> =
    XOrMoreGrammar(CharMatcherGrammar(value), this)

internal fun Int.orMore(value: Char): Grammar<String> =
    XOrMoreGrammar(CharMatcherGrammar(value), this)

internal fun <T> optional(value: Grammar<T>): Grammar<T> =
    (0 orElse 1).of(value)

internal fun optional(value: CharMatcher): Grammar<String> =
    (0 orElse 1).of(value)

internal fun optional(value: Char): Grammar<String> =
    (0 orElse 1).of(value)

internal fun <T> IntRange.of(value: Grammar<T>): Grammar<T> =
    RangeGrammar(value, this)

internal fun <T> IntRange.of(value: CharMatcher): Grammar<String> =
    RangeGrammar(CharMatcherGrammar(value), this)

internal fun <T> IntRange.of(value: Char): Grammar<String> =
    RangeGrammar(CharMatcherGrammar(value), this)

internal fun CharMatcher.group(key: String): Grammar<String> =
    CharMatcherGrammar(this).group(key)

internal fun <S> CharMatcher.capture(function: (SingleCaptureContext<String>) -> S): Grammar<S> =
    CharMatcherGrammar(this).capture(function)

internal operator fun Char.div(value: Char): CharMatcher = charMatcher {
  char(this@div)
  char(value)
}

internal operator fun Char.div(value: CharMatcher): CharMatcher = charMatcher {
  char(this@div)
  group(value)
}

internal operator fun <T> Char.div(value: Grammar<T>): Grammar<String> =
    OrGrammar(CharMatcherGrammar(this), value)

internal operator fun CharMatcher.div(value: Char): CharMatcher = charMatcher {
  group(this@div)
  char(value)
}

internal operator fun CharMatcher.div(value: CharMatcher): CharMatcher =
    charMatcher {
      group(this@div)
      group(value)
    }

internal operator fun <T> CharMatcher.div(value: Grammar<T>): Grammar<String> =
    OrGrammar(CharMatcherGrammar(this), value)

internal operator fun <T> Grammar<T>.div(value: Char): Grammar<String> =
    OrGrammar(this, CharMatcherGrammar(value))

internal operator fun <T> Grammar<T>.div(value: CharMatcher): Grammar<String> =
    OrGrammar(this, CharMatcherGrammar(value))

internal operator fun <S, T> Grammar<S>.div(value: Grammar<T>): Grammar<String> =
    OrGrammar(this, value)


internal operator fun Char.plus(value: Char): Grammar<String> =
    AndThenGrammar(CharMatcherGrammar(this), CharMatcherGrammar(value))

internal operator fun Char.plus(value: CharMatcher): Grammar<String> =
    AndThenGrammar(CharMatcherGrammar(this), CharMatcherGrammar(value))

internal operator fun <T> Char.plus(value: Grammar<T>): Grammar<String> =
    AndThenGrammar(CharMatcherGrammar(this), value)

internal operator fun CharMatcher.plus(value: Char): Grammar<String> =
    AndThenGrammar(CharMatcherGrammar(this), CharMatcherGrammar(value))

internal operator fun CharMatcher.plus(value: CharMatcher): Grammar<String> =
    AndThenGrammar(CharMatcherGrammar(this), CharMatcherGrammar(value))

internal operator fun <T> CharMatcher.plus(value: Grammar<T>): Grammar<String> =
    AndThenGrammar(CharMatcherGrammar(this), value)

internal operator fun <T> Grammar<T>.plus(value: Char): Grammar<String> =
    AndThenGrammar(this, CharMatcherGrammar(value))

internal operator fun <T> Grammar<T>.plus(value: CharMatcher): Grammar<String> =
    AndThenGrammar(this, CharMatcherGrammar(value))

internal operator fun <S, T> Grammar<S>.plus(value: Grammar<T>): Grammar<String> =
    AndThenGrammar(this, value)

internal infix fun Int.orElse(value: Int): IntOr =
    IntOr(setOf(this, value))

internal fun Int.orElse(
    value2: Int,
    vararg remaining: Int
): IntOr = IntOr(setOf(this, value2) + remaining.toSet())

internal class IntOr(private val options: Set<Int>) {
  internal fun <T> of(value: Grammar<T>): Grammar<T> =
      OrCountGrammar(value, options)

  internal fun of(value: CharMatcher): Grammar<String> =
      OrCountGrammar(CharMatcherGrammar(value), options)

  internal fun of(value: Char): Grammar<String> =
      OrCountGrammar(CharMatcherGrammar(value), options)
}
