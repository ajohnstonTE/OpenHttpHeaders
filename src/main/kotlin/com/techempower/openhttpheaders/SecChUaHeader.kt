package com.techempower.openhttpheaders


// https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-CH-UA
// https://wicg.github.io/ua-client-hints/#sec-ch-ua
// https://www.rfc-editor.org/rfc/rfc8941
class SecChUaHeader {

  companion object {
    @JvmStatic
    fun builder(): SecChUaHeaderBuilder = TODO()
  }
}

fun secChUaHeader(init: SecChUaHeaderDsl.() -> Unit = {}): SecChUaHeader =
  TODO()

class SecChUaHeaderBuilder {
  // TODO CURRENT

  fun build(): SecChUaHeader = TODO()
}

interface SecChUaHeaderDsl {
  // TODO CURRENT
}

private class SecChUaHeaderDslImpl: SecChUaHeaderDsl {
  fun toHeader(): SecChUaHeader = TODO()
}
