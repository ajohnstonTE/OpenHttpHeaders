@file:Suppress("MemberVisibilityCanBePrivate", "ObjectPropertyName")

package com.techempower.openhttpheaders.wip

import com.techempower.openhttpheaders.parse.Grammar
import com.techempower.openhttpheaders.parse.charMatcher
import com.techempower.openhttpheaders.parse.div
import com.techempower.openhttpheaders.parse.group
import com.techempower.openhttpheaders.parse.orMore
import com.techempower.openhttpheaders.parse.plus

internal class Rfc7230 {
  companion object {
    // https://www.rfc-editor.org/rfc/rfc7230#section-1.2
    val DIGIT = Rfc5234.DIGIT
    val ALPHA = Rfc5234.ALPHA
    val V_CHAR = Rfc5234.V_CHAR

    // https://www.rfc-editor.org/rfc/rfc7230#section-3.2.3
    val OWS = 0.orMore(' ' / '\t')

    // https://www.rfc-editor.org/rfc/rfc7230#section-3.2.6
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
            // Instead of returning the whole match as its value, only return
            // the escaped character. For example, instead of returning \",
            // it would just return "
            .transform { it["escaped_char"]!! }
    val QUOTED_STRING =
        ('\"' + 0.orMore(QUOTED_PAIR / QD_TEXT).group("quoted_value") + '\"')
            // Only include the content inside the quotes for the value
            .transform { it["quoted_value"]!! }
    val T_CHAR = '!' / '#' / '$' / '%' / '&' / '\'' / '*' / '+' / '-' /
        '.' / '^' / '_' / '`' / '|' / '~' / DIGIT / ALPHA
    val TOKEN = 1.orMore(T_CHAR)

    /// https://www.rfc-editor.org/rfc/rfc7230#section-7
    /*
    TODO CURRENT: There are legacy list rules that must be allowed but
     ignored. See here:
     https://www.rfc-editor.org/rfc/rfc7230#section-7
    */
    /*
    TODO CURRENT: For the legacy list rule, it needs to be able to capture
     empty/optional values, *however* those empty values must not count
     towards the total matches for the x.orMore. In other words, if it was
     1.orMore and it matched all empty elements, then it isn't a match.
     This means that it needs an IgnoreEmptyGrammar or something, which
     would return an IGNORE flag if the count/contents should be ignored,
     but *also* provide an index for the child since it can still take up
     space (meaning it can't just assume a length of 1 like with
     CharMatcherGrammars). For this to be possible, all grammars will need
     to return longs instead of ints for the match function, with the index
     and flag written into the long. However, in order for a parent to know
     when backing off of a child if the child was an ignore child, it will
     need to be able to access the flag of the child at will. This could be
     stored as the value of the child, such that negative values are assumed
     to be flags, but it might be better to just add another field to the
     list to avoid that assumption, even if it's a memory hit. If it ends up
     using the object approach in the end, then it really won't be a heavy
     cost anyways.
    */
    /*
    TODO: In the future, the flags should probably be bit-masked so that it
     can actually have multiple flags stored in a single value.
    */
    // Note: IntelliJ thinks this is unused, but it's not. Must be a bug.
    @Suppress("unused")
    val `#`: (Grammar<*>) -> Grammar<String> = { grammar ->
      grammar + 0.orMore(OWS + ',' + OWS + grammar)
    }

    // https://www.rfc-editor.org/rfc/rfc7230#section-7
    @Suppress("unused")
    val `1#`: (Grammar<*>) -> Grammar<String> = { TODO() }
  }
}
