@file:Suppress("UNUSED_VARIABLE", "LocalVariableName")

package com.techempower.openhttpheaders.parse

import com.techempower.openhttpheaders.wip.parse.div
import com.techempower.openhttpheaders.wip.parse.group
import com.techempower.openhttpheaders.wip.parse.of
import com.techempower.openhttpheaders.wip.parse.orElse
import com.techempower.openhttpheaders.wip.parse.orMore
import com.techempower.openhttpheaders.wip.parse.plus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GrammarFormatTest : FunSpec({
  test("should compile") {

    data class Header(val key: String, val value: String)

    class StringWrapper(val str: String)

    // Grammar:
    //
    // header: token OWS "=" OWS (token | quoted_value)
    // token: {1,inf}tchar
    // tchar: "a" | "b" | "c"
    // quoted_value: <"> {0,inf}( quote_char | escape_pair ) <">
    // quote_char: "a" | "b" | "c"
    // escape_pair: "\" ( quote_char | SP | <"> )
    // OWS: {0,inf} ( SP | HTAB )
    //      ; OWS is 'optional whitespace'
    //
    // Note that escape pairs should be able to be unescaped
    val QUOTE_CHAR = charMatcher { anyOf("abc") }
    val ESCAPE_PAIR = ('\\' + (QUOTE_CHAR / ' ' / '\"').group("escaped_char"))
        // Instead of returning the whole match as its value, only return the escaped character.
        // For example, instead of returning \", it would just return "
        .transform { it["escaped_char"]!! }
    val QUOTED_VALUE =
        ('\"' + (0.orMore(QUOTE_CHAR / ESCAPE_PAIR)).group("quote_content") + '\"')
            // Only include the content inside the quotes for the value
            .transform { it["quote_content"]!! }
    val T_CHAR = charMatcher { anyOf("abc") }
    val TOKEN = 1.orMore(T_CHAR)
    val KEY = TOKEN
        .capture { StringWrapper(it.value) }
    val OWS = 0.orMore(' ' / '\t')
    val HEADER =
        (KEY.group("key") + OWS + '=' + OWS + (TOKEN / QUOTED_VALUE).group("value"))
            .capture {
              Header(
                  key = it["key", KEY]!!.str,
                  value = it["value"]!!
              )
            }

    // Should produce `Header(key="abb", value="bac")
    val result1: Header = HEADER.parse("abb=bac")
    // Should produce `Header(key="ca", value="b a")
    val result2: Header = HEADER.parse("ca=\"b\\ a\"")

    result1 shouldBe Header(key = "abb", value = "bac")
    result2 shouldBe Header(key = "ca", value = "b a")

    // Not related to example above, just testing other formats/functions
    val test1: Grammar<String> = (1..4).of(TOKEN)
    val test2: Grammar<String> = 0.orMore(TOKEN)
    val test3: Grammar<List<Int>> = 1.orMore(!TOKEN)
        .capture {
          it.values(TOKEN).map { value -> value.toInt() }
        }
    val test4: Grammar<String> = (1.orElse(2)).of(TOKEN)
    val test5: Grammar<String> = (1.orElse(2, 3)).of(TOKEN)
  }
})




