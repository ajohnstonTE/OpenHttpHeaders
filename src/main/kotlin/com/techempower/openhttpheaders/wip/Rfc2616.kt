@file:Suppress(
    "LocalVariableName",
    "UnnecessaryVariable",
    "MemberVisibilityCanBePrivate"
)

package com.techempower.openhttpheaders.wip

import com.techempower.openhttpheaders.parse.CharMatcher
import com.techempower.openhttpheaders.parse.charMatcher
import com.techempower.openhttpheaders.parse.div
import com.techempower.openhttpheaders.parse.group
import com.techempower.openhttpheaders.parse.optional
import com.techempower.openhttpheaders.parse.orMore
import com.techempower.openhttpheaders.parse.plus
import com.techempower.openhttpheaders.parse.unaryMinus

internal class Rfc2616 {
  companion object {
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
    val LWS = (optional(-"\r\n") + 1.orMore(SP / HT))
        // According to the spec, all linear white space can be treated as a
        // single space before interpreting the field value.
        .transform { " " }

    // While LWS is defined in the format of a grammar in the spec, its
    // usage for the TEXT grammar is as a set of matchable characters, so for
    // that reason it is defined as such here.
    private val LWS_CHARS = charMatcher {
      char('\r')
      char('\n')
      char(' ')
      char('\t')
    }
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
    val SEPARATORS = '(' / ')' / '<' / '>' / '@' / ',' / ';' / ':' / '\\' /
        '\"' / '/' / '[' / ']' / '?' / '=' / '{' / '}' / SP / HT
    val TOKEN = 1.orMore(charMatcher {
      group(CHAR)
      exclude {
        group(CTL)
        group(SEPARATORS)
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
    val QUOTED_STRING =
        ('\"' + 0.orMore(QUOTED_PAIR / QD_TEXT).group("quoted_value") + '\"')
            .transform { it["quoted_value"]!! }

    // https://www.rfc-editor.org/rfc/rfc2616#section-3.6
    val VALUE = TOKEN / QUOTED_STRING
  }
}
