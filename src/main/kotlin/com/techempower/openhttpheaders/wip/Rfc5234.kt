package com.techempower.openhttpheaders.wip

import com.techempower.openhttpheaders.parse.charMatcher

internal class Rfc5234 {
  companion object {
    // https://www.rfc-editor.org/rfc/rfc5234#appendix-B.1
    val ALPHA = charMatcher {
      range('a'..'z')
      range('A'..'Z')
    }
    val DIGIT = charMatcher {
      range('0'..'9')
    }
  }
}
