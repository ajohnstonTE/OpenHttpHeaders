package com.techempower.openhttpheaders.wip

import com.techempower.openhttpheaders.parse.charMatcher
import com.techempower.openhttpheaders.wip.parse.div
import com.techempower.openhttpheaders.wip.parse.orMore

internal class Rfc7230 {
  companion object {
    val OWS = 0.orMore(' ' / '\t')
    val DIGIT = charMatcher { range('0'..'9') }
    val ALPHA = charMatcher {
      range('a'..'z')
      range('A'..'Z')
    }
  }
}
