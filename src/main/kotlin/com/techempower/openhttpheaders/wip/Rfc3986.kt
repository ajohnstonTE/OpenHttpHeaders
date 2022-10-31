@file:Suppress("LocalVariableName", "UnnecessaryVariable")

package com.techempower.openhttpheaders.wip

import com.techempower.openhttpheaders.wip.parse.plus

internal class Rfc3986 {
  companion object {
    // https://www.rfc-editor.org/rfc/rfc3986#section-2.1
    val PCT_ENCODED = '%' + Rfc2234.HEXDIG + Rfc2234.HEXDIG
  }
}
