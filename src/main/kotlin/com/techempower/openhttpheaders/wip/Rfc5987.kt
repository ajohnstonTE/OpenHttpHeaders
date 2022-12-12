@file:Suppress("LocalVariableName", "UnnecessaryVariable",
    "MemberVisibilityCanBePrivate"
)

package com.techempower.openhttpheaders.wip

import com.techempower.openhttpheaders.parse.div
import com.techempower.openhttpheaders.parse.optional
import com.techempower.openhttpheaders.parse.orMore
import com.techempower.openhttpheaders.parse.plus
import com.techempower.openhttpheaders.parse.unaryMinus
import java.net.URLDecoder
import java.nio.charset.Charset

internal class Rfc5987 {
  companion object {
    // https://www.rfc-editor.org/rfc/rfc5987#section-2
    val ALPHA = Rfc5234.ALPHA
    val DIGIT = Rfc5234.DIGIT
    // https://www.rfc-editor.org/rfc/rfc5987#section-3.2.1
    val ATTR_CHAR = ALPHA / DIGIT / '!' / '#' / '$' / '&' /
        '+' / '-' / '.' / '^' / '_' / '`' / '|' / '~'
    val MIME_CHARSETC = ALPHA / DIGIT / '!' / '#' / '$' /
        '%' / '&' / '+' / '-' / '^' / '_' / '`' / '{' / '}' / '~'
    val MIME_CHARSET = 1.orMore(MIME_CHARSETC)
    val CHARSET = -"UTF-8" / -"ISO-8859-1" / MIME_CHARSET
    val PCT_ENCODED = Rfc3986.PCT_ENCODED
    val VALUE_CHARS = 0.orMore(PCT_ENCODED / ATTR_CHAR)
    val LANGUAGE = Rfc5646.LANGUAGE_TAG.copy()
    val EXT_VALUE =
        (!CHARSET + '\'' + optional(!LANGUAGE) + '\'' + !VALUE_CHARS)
            .capture {
              val value = it[VALUE_CHARS]!!
              val charset = Charset.forName(it[CHARSET]!!)
              ExtValue(
                  value = URLDecoder.decode(value, charset),
                  charset = charset,
                  lang = it[LANGUAGE]
              )
            }
  }
}

internal class ExtValue(
    val value: String,
    val charset: Charset,
    val lang: String?
)
