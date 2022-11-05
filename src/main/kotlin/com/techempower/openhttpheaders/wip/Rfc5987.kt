@file:Suppress("LocalVariableName", "UnnecessaryVariable")

package com.techempower.openhttpheaders.wip

import com.techempower.openhttpheaders.parse.Grammar
import com.techempower.openhttpheaders.wip.parse.div
import com.techempower.openhttpheaders.wip.parse.optional
import com.techempower.openhttpheaders.wip.parse.orMore
import com.techempower.openhttpheaders.wip.parse.plus
import com.techempower.openhttpheaders.wip.parse.unaryMinus
import java.net.URLDecoder
import java.nio.charset.Charset

internal class Rfc5987 {
  companion object {

    val EXT_VALUE: Grammar<ExtValue>

    init {
      // https://www.rfc-editor.org/rfc/rfc5987#section-3.2.1
      val ATTR_CHAR = Rfc5234.ALPHA / Rfc5234.DIGIT / '!' / '#' / '$' / '&' /
          '+' / '-' / '.' / '^' / '_' / '`' / '|' / '~'
      val MIME_CHARSETC = Rfc5234.ALPHA / Rfc5234.DIGIT / '!' / '#' / '$' /
          '%' / '&' / '+' / '-' / '^' / '_' / '`' / '{' / '}' / '~'
      val MIME_CHARSET = 1.orMore(MIME_CHARSETC)
      val CHARSET = -"UTF-8" / -"ISO-8859-1" / MIME_CHARSET
      val LANGUAGE = Rfc5646.LANGUAGE_TAG.copy()
      val VALUE_CHARS = 0.orMore(Rfc3986.PCT_ENCODED / ATTR_CHAR)
      EXT_VALUE = (!CHARSET + '\'' + optional(!LANGUAGE) + '\'' + !VALUE_CHARS)
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
}

internal class ExtValue(
    val value: String,
    val charset: Charset,
    val lang: String?
)
