@file:Suppress("LocalVariableName", "UnnecessaryVariable")

package com.techempower.openhttpheaders.wip

import com.techempower.openhttpheaders.ContentDispositionHeader
import com.techempower.openhttpheaders.parse.ParsingGrammar
import com.techempower.openhttpheaders.wip.parse.div
import com.techempower.openhttpheaders.wip.parse.optional
import com.techempower.openhttpheaders.wip.parse.orMore
import com.techempower.openhttpheaders.wip.parse.plus
import com.techempower.openhttpheaders.wip.parse.unaryMinus

internal class Rfc6266 {
  companion object {

    private val CONTENT_DISPOSITION: ParsingGrammar<ContentDispositionHeader>

    init {
      val OWS = optional(Rfc2616.LWS)
      val EXT_TOKEN = (!Rfc2616.TOKEN + '*')
          .transform { it[Rfc2616.TOKEN].value!! }
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
          ((!EXT_TOKEN + OWS + '=' + OWS + !Rfc5987.EXT_VALUE)
              / (!Rfc2616.TOKEN + OWS + '=' + OWS + !Rfc2616.VALUE))
              .capture {
                if (it[EXT_TOKEN].value != null) {
                  val extValue = it[Rfc5987.EXT_VALUE].value!!
                  ContentDispositionHeader.Parameter(
                      key = it[EXT_TOKEN].value!!,
                      value = extValue.value,
                      charset = extValue.charset,
                      lang = extValue.lang,
                      explicitExt = true
                  )
                } else {
                  ContentDispositionHeader.Parameter(
                      key = it[Rfc2616.TOKEN].value!!,
                      value = it[Rfc2616.VALUE].value!!,
                      // The charset for non-ext values is ISO-LATIN-1, aka ISO/IEC 8859-1
                      charset = Charsets.ISO_8859_1,
                      lang = null,
                      explicitExt = false
                  )
                }
              }
      val DISPOSITION_PARM = DISP_EXT_PARM.copy()
      val DISP_EXT_TYPE = Rfc2616.TOKEN.copy()
      val DISPOSITION_TYPE = -"inline" / -"attachment" / DISP_EXT_TYPE
      // https://www.rfc-editor.org/rfc/rfc6266#section-4.1
      CONTENT_DISPOSITION =
          (!DISPOSITION_TYPE + 0.orMore(OWS + ';' + OWS + !DISPOSITION_PARM))
              .capture {
                ContentDispositionHeader(
                    dispositionType = it[DISPOSITION_TYPE].value!!,
                    parameters = it.values(DISPOSITION_PARM)
                )
              }
    }

    fun parseContentDispositionHeader(headerStr: String): ContentDispositionHeader {
      return CONTENT_DISPOSITION.parse(headerStr)
    }
  }
}
