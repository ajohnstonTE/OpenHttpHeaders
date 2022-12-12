@file:Suppress(
    "LocalVariableName",
    "UnnecessaryVariable",
    "MemberVisibilityCanBePrivate"
)

package com.techempower.openhttpheaders.wip

import com.techempower.openhttpheaders.parse.charMatcher
import com.techempower.openhttpheaders.parse.div

internal class Rfc2234 {
  companion object {
    // https://www.rfc-editor.org/rfc/rfc2234#section-6.1
    val DIGIT = charMatcher {
      range('0'..'9')
    }
    val HEXDIG = DIGIT / 'A' / 'B' / 'C' / 'D' / 'E' / 'F' /
        'a' / 'b' / 'c' / 'd' / 'e' / 'f'
  }
}
