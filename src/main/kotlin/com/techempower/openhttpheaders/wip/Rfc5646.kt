@file:Suppress(
    "LocalVariableName", "UnnecessaryVariable",
    "MemberVisibilityCanBePrivate"
)

package com.techempower.openhttpheaders.wip

import com.techempower.openhttpheaders.parse.charMatcher
import com.techempower.openhttpheaders.parse.div
import com.techempower.openhttpheaders.parse.of
import com.techempower.openhttpheaders.parse.optional
import com.techempower.openhttpheaders.parse.orMore
import com.techempower.openhttpheaders.parse.plus
import com.techempower.openhttpheaders.parse.unaryMinus

internal class Rfc5646 {
  companion object {

    // TODO CURRENT: Consider the following excerpt from the spec:
    //  "All subtags have a maximum length of eight characters."
    //  Does this mean that the xOrMore's should actually be capped at 8?
    // Note that some of original spec's syntax is implied by
    // https://www.rfc-editor.org/rfc/rfc5234#section-3.7
    // Otherwise, everything is defined by
    // https://www.rfc-editor.org/rfc/rfc5646#section-2.1
    val REGION = 2.of(Rfc5234.ALPHA) / 3.of(Rfc5234.DIGIT)
    val ALPHANUM = Rfc5234.ALPHA / Rfc5234.DIGIT
    val SINGLETON = charMatcher {
      group(Rfc5234.DIGIT)
      range('A'..'W')
      range('Y'..'Z')
      range('a'..'w')
      range('y'..'z')
    }
    val EXTENSION = SINGLETON + 1.orMore('-' + (2..8).of(ALPHANUM))
    val VARIANT = (5..8).of(ALPHANUM) / (Rfc5234.DIGIT + 3.of(ALPHANUM))
    val SCRIPT = 4.of(Rfc5234.ALPHA)
    val EXTLANG = 3.of(Rfc5234.ALPHA) + (0..2).of('-' + 3.of(Rfc5234.ALPHA))
    val LANGUAGE = (2..3).of(Rfc5234.ALPHA) + optional('-' + EXTLANG)
    val PRIVATEUSE = 'x' + 1.orMore('-' + (1..8).of(ALPHANUM))
    val LANGTAG = LANGUAGE + optional('-' + SCRIPT) +
        optional('-' + REGION) + 0.orMore('-' + VARIANT) +
        0.orMore('-' + EXTENSION) + optional('-' + PRIVATEUSE)
    val IRREGULAR = -"en-GB-oed" /
        -"i-ami" /
        -"i-bnn" /
        -"i-default" /
        -"i-enochian" /
        -"i-hak" /
        -"i-klingon" /
        -"i-lux" /
        -"i-mingo" /
        -"i-navajo" /
        -"i-pwn" /
        -"i-tao" /
        -"i-tay" /
        -"i-tsu" /
        -"sgn-BE-FR" /
        -"sgn-BE-NL" /
        -"sgn-CH-DE"
    val REGULAR = -"art-lojban" /
        -"cel-gaulish" /
        -"no-bok" /
        -"no-nyn" /
        -"zh-guoyu" /
        -"zh-hakka" /
        -"zh-min" /
        -"zh-min-nan" /
        -"zh-xiang"
    val GRANDFATHERED = IRREGULAR / REGULAR
    val LANGUAGE_TAG = LANGTAG / PRIVATEUSE / GRANDFATHERED
  }
}
