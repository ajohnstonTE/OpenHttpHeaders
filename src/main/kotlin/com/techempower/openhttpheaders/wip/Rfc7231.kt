@file:Suppress(
    "LocalVariableName", "UnnecessaryVariable",
    "MemberVisibilityCanBePrivate"
)

package com.techempower.openhttpheaders.wip

import com.techempower.openhttpheaders.AcceptHeader
import com.techempower.openhttpheaders.MediaType
import com.techempower.openhttpheaders.parse.div
import com.techempower.openhttpheaders.parse.orMore
import com.techempower.openhttpheaders.parse.plus

internal class Rfc7231 {

  companion object {
    // https://www.rfc-editor.org/rfc/rfc7231#section-1.2
    // Note: IntelliJ thinks this is unused, but it's not. Must be a bug.
    @Suppress("unused", "ObjectPropertyName")
    val `#` = Rfc7230.`#`

    // https://www.rfc-editor.org/rfc/rfc7231#appendix-C
    val TOKEN = Rfc7230.TOKEN

    // https://www.rfc-editor.org/rfc/rfc7231#appendix-D
    val OWS = Rfc7230.OWS
    val QUOTED_STRING = Rfc7230.QUOTED_STRING

    // https://www.rfc-editor.org/rfc/rfc7231#section-3.1.1.1
    val PARAMETER = (!TOKEN + '=' + (QUOTED_STRING / TOKEN).group("value"))
        .capture { it[TOKEN]!!.lowercase() to it["value"]!! }
    val TYPE = TOKEN.copy()
    val SUBTYPE = TOKEN.copy()
    val MEDIA_TYPE =
        (!TYPE + '/' + !SUBTYPE + 0.orMore(OWS + ';' + OWS + !PARAMETER))
            .capture {
              val parameters = it.values(PARAMETER).toMap()
              val qValueKey = (it.parseParameters[Q_VALUE_KEY]!! as String)
                  .lowercase()
              val quality = parameters[qValueKey]?.toDouble()
              MediaType(
                  type = it[TYPE]!!,
                  subtype = it[SUBTYPE]!!,
                  parameters = parameters,
                  quality = quality
              )
            }
    // https://www.rfc-editor.org/rfc/rfc7231#section-5.3.2
    val ACCEPT =
        `#`(!MEDIA_TYPE)
            .capture { AcceptHeader(it.values(MEDIA_TYPE)) }

    fun parseMediaType(input: String, qValueKey: String = "q") =
        MEDIA_TYPE.parse(input, mapOf(Q_VALUE_KEY to qValueKey))

    fun parseAcceptHeader(input: String, qValueKey: String = "q") =
        ACCEPT.parse(input, mapOf(Q_VALUE_KEY to qValueKey))
  }
}
