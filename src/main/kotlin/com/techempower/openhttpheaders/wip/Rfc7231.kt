@file:Suppress("LocalVariableName", "UnnecessaryVariable")

package com.techempower.openhttpheaders.wip

import com.techempower.openhttpheaders.AcceptHeader
import com.techempower.openhttpheaders.MediaType
import com.techempower.openhttpheaders.parse.ParsingGrammar
import com.techempower.openhttpheaders.parse.charMatcher
import com.techempower.openhttpheaders.wip.parse.div
import com.techempower.openhttpheaders.wip.parse.group
import com.techempower.openhttpheaders.wip.parse.orMore
import com.techempower.openhttpheaders.wip.parse.plus

internal class Rfc7231 {

  companion object {

    private val MEDIA_TYPE: ParsingGrammar<MediaType>
    private val ACCEPT: ParsingGrammar<AcceptHeader>

    init {
      // Any visible USASCII character
      val V_CHAR = charMatcher { range('!'..'~') }
      // The CharMatcher below matches any non-ascii character, see https://stackoverflow.com/a/25988226
      // Note that the original definition in the RFC is \x80-\xFF, however I
      // think it should be \uFFFF as the end of the range here, not \u00FF, so
      // that it truly captures all non-ascii characters. Corrections welcomed.
      // Also of note, apparently grammar rules prefixed by "obs-" are
      // obsolete, but I don't know if that means they shouldn't be included.
      val OBS_TEXT = charMatcher { range('\u0080'..'\uFFFF') }
      val QD_TEXT = charMatcher {
        char('\t')
        char(' ')
        char('\u0021')
        range('\u0023'..'\u005B')
        range('\u005D'..'\u007E')
        group(OBS_TEXT)
      }
      val QUOTED_PAIR =
          ('\\' + ('\t' / ' ' / V_CHAR / OBS_TEXT).group("escaped_char"))
              // Instead of returning the whole match as its value, only return the escaped character.
              // For example, instead of returning \", it would just return "
              .transform { it["escaped_char"].value!! }
      val T_CHAR = '!' / '#' / '$' / '%' / '&' / '\'' / '*' / '+' / '-' /
          '.' / '^' / '_' / '`' / '|' / '~' / Rfc7230.DIGIT / Rfc7230.ALPHA
      val QUOTED_STRING =
          ('\"' + 0.orMore(QUOTED_PAIR / QD_TEXT).group("quoted_value") + '\"')
              // Only include the content inside the quotes for the value
              .transform { it["quoted_value"].value!! }
      val TOKEN = 1.orMore(T_CHAR)
      val x = 1 or 2
      val PARAMETER = (!TOKEN + '=' + (QUOTED_STRING / TOKEN).group("value"))
          .capture { it[TOKEN].value!!.lowercase() to it["value"].value!! }
      // TODO CURRENT: copy should probably return an actual copy so that
      //  capture grammars don't get messed up for refs when copied.
      //  Note: This is done, but it needs tests. While testing, temporarily
      //  change it back to verify it would have been an issue.
      val TYPE = TOKEN.copy()
      val SUBTYPE = TOKEN.copy()
      MEDIA_TYPE =
          (!TYPE + '/' + !SUBTYPE + 0.orMore(Rfc7230.OWS + ';' + Rfc7230.OWS + !PARAMETER))
              .capture {
                val parameters = it.values(PARAMETER).toMap()
                val qValueKey = (it.parseParameters[Q_VALUE_KEY]!! as String)
                    .lowercase()
                val quality = parameters[qValueKey]?.toDouble()
                MediaType(
                    type = it[TYPE].value!!,
                    subtype = it[SUBTYPE].value!!,
                    parameters = parameters,
                    quality = quality
                )
              }
      ACCEPT =
          (!MEDIA_TYPE + 0.orMore(Rfc7230.OWS + ',' + Rfc7230.OWS + !MEDIA_TYPE))
              .capture { AcceptHeader(it.values(MEDIA_TYPE)) }
    }

    fun parseMediaType(input: String, qValueKey: String = "q") =
        MEDIA_TYPE.parse(input, mapOf(Q_VALUE_KEY to qValueKey))

    fun parseAcceptHeader(input: String, qValueKey: String = "q") =
        ACCEPT.parse(input, mapOf(Q_VALUE_KEY to qValueKey))
  }
}
