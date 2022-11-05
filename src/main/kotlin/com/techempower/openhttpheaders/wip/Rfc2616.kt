@file:Suppress(
    "LocalVariableName",
    "UnnecessaryVariable",
    "MemberVisibilityCanBePrivate"
)

package com.techempower.openhttpheaders.wip

import com.techempower.openhttpheaders.parse.CharMatcher
import com.techempower.openhttpheaders.parse.Grammar
import com.techempower.openhttpheaders.parse.charMatcher
import com.techempower.openhttpheaders.wip.parse.div
import com.techempower.openhttpheaders.wip.parse.group
import com.techempower.openhttpheaders.wip.parse.optional
import com.techempower.openhttpheaders.wip.parse.orMore
import com.techempower.openhttpheaders.wip.parse.plus
import com.techempower.openhttpheaders.wip.parse.unaryMinus

internal class Rfc2616 {
  companion object {
    val TOKEN: Grammar<String>
    val QUOTED_STRING: Grammar<String>
    val VALUE: Grammar<String>
    val LWS: Grammar<String>

    init {
      // https://www.rfc-editor.org/rfc/rfc2616#section-2.2
      val CHAR = CharMatcher.ASCII
      val CTL = charMatcher {
        range('\u0000'..'\u001F') // octets 0 through 31
        char('\u007F') // octet 127
      }
      val SP = charMatcher {
        char(' ')
      }
      val HT = charMatcher {
        char('\t')
      }
      val OCTET = charMatcher {
        any()
      }
      // While LWS is defined in the format of a grammar in the spec, its
      // usage is exclusively as a set of matchable characters, so for that
      // reason it is treated as such here.
      val LWS_CHARS = charMatcher {
        char('\r')
        char('\n')
        char(' ')
        char('\t')
      }
      LWS = (optional(-"\r\n") + 1.orMore(SP / HT))
          // According to the spec, all linear white space can be treated as a
          // single space before interpreting the field value.
          .transform { " " }
      val TEXT = charMatcher {
        group(OCTET)
        exclude {
          group(CTL)
          // By excluding this block, these can still match overall
          exclude {
            group(LWS_CHARS)
          }
        }
      }
      val SEPARATOR = '(' / ')' / '<' / '>' / '@' / ',' / ';' / ':' / '\\' /
          '\"' / '/' / '[' / ']' / '?' / '=' / '{' / '}' / SP / HT
      TOKEN = 1.orMore(charMatcher {
        group(CHAR)
        exclude {
          group(CTL)
          group(SEPARATOR)
        }
      })
      val QD_TEXT = charMatcher {
        group(TEXT)
        exclude {
          char('\"')
        }
      }
      val QUOTED_PAIR = ('\\' + CHAR.group("char"))
          .transform { it["char"]!! }
      // Note: QUOTED_PAIR MUST be captured before QD_TEXT, as it is
      // technically a valid and capture-able QD_TEXT value, but SHOULD be
      // interpreted as a quoted pair.
      QUOTED_STRING =
          ('\"' + 0.orMore(QUOTED_PAIR / QD_TEXT).group("quoted_value") + '\"')
              .transform { it["quoted_value"]!! }
      // https://www.rfc-editor.org/rfc/rfc2616#section-3.6
      VALUE = TOKEN / QUOTED_STRING
    }
  }
}
