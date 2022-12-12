@file:Suppress("LocalVariableName", "UnnecessaryVariable",
    "MemberVisibilityCanBePrivate"
)

package com.techempower.openhttpheaders.wip

import com.techempower.openhttpheaders.ContentDispositionHeader
import com.techempower.openhttpheaders.parse.div
import com.techempower.openhttpheaders.parse.optional
import com.techempower.openhttpheaders.parse.orMore
import com.techempower.openhttpheaders.parse.plus
import com.techempower.openhttpheaders.parse.unaryMinus

internal class Rfc6266 {
  companion object {
    // https://www.rfc-editor.org/rfc/rfc6266#section-4.1
    val OWS = optional(Rfc2616.LWS)
    val TOKEN = Rfc2616.TOKEN
    val VALUE = Rfc2616.VALUE
    val EXT_TOKEN = (!TOKEN + '*')
        .transform { it[TOKEN]!! }
    val EXT_VALUE = Rfc5987.EXT_VALUE
    // For simplicity, filename-parm is left out as it's completely covered
    // by DISP_EXT_PARM
    // Note: EXT_TOKEN/EXT_VALUE MUST be captured first otherwise it will
    // always be captured as a token/value, as the token/value grammar
    // completely contains the grammar/characters present in an ext value.
    // Also, per the grammar of token, "key*" is a valid token, meaning an
    // incorrectly formatted ext pair can and will instead be interpreted as
    // a token-value pair. While it's likely that the intention was to always
    // interpret "key*" keys as an ext-token, the spec does not prevent it
    // from being a standard key-value token, and for that reason this
    // implementation does not make any assumptions.
    val DISP_EXT_PARM =
        ((!EXT_TOKEN + OWS + '=' + OWS + !EXT_VALUE)
            / (!TOKEN + OWS + '=' + OWS + !VALUE))
            .capture {
              if (it[EXT_TOKEN] != null) {
                val extValue = it[EXT_VALUE]!!
                ContentDispositionHeader.Parameter(
                    key = it[EXT_TOKEN]!!,
                    value = extValue.value,
                    charset = extValue.charset,
                    lang = extValue.lang,
                    explicitExt = true
                )
              } else {
                ContentDispositionHeader.Parameter(
                    key = it[TOKEN]!!,
                    value = it[VALUE]!!,
                    // The charset for non-ext values is ISO-LATIN-1, aka ISO/IEC 8859-1
                    charset = Charsets.ISO_8859_1,
                    lang = null,
                    explicitExt = false
                )
              }
            }
    val DISPOSITION_PARM = DISP_EXT_PARM.copy()
    val DISP_EXT_TYPE = TOKEN.copy()
    val DISPOSITION_TYPE = -"inline" / -"attachment" / DISP_EXT_TYPE
    val CONTENT_DISPOSITION =
        (!DISPOSITION_TYPE + 0.orMore(OWS + ';' + OWS + !DISPOSITION_PARM))
            .capture {
              ContentDispositionHeader(
                  dispositionType = it[DISPOSITION_TYPE]!!,
                  parameters = it.values(DISPOSITION_PARM)
              )
            }

    fun parseContentDispositionHeader(headerStr: String): ContentDispositionHeader {
      return CONTENT_DISPOSITION.parse(headerStr)
    }
  }
}
