package com.techempower.openhttpheaders.parse

// Partially inspired by Guava: https://github.com/google/guava/blob/master/guava/src/com/google/common/base/CharMatcher.java
internal interface CharMatcher {
  fun matches(char: Char): Boolean

  companion object {
    val ASCII = charMatcher {
      range('\u0000'..'\u007F')
    }

    val NON_ASCII = charMatcher {
      any()
      exclude {
        group(ASCII)
      }
    }

    val NUMBERS = charMatcher {
      range('0'..'9')
    }

    val LETTERS = charMatcher {
      range('a'..'z')
      range('A'..'Z')
    }
  }
}

internal abstract class CharMatcherDsl {
  protected val childNodes = mutableListOf<NodeCharMatcher.Child>()

  fun include(init: CharMatcherDsl.() -> Unit) = addDsl(init, false)

  fun exclude(init: CharMatcherDsl.() -> Unit) = addDsl(init, true)

  fun range(range: CharRange) {
    add(RangeCharMatcher(range))
  }

  fun group(charMatcher: CharMatcher) {
    add(charMatcher)
  }

  fun char(char: Char) {
    add(SingleCharMatcher(char))
  }

  fun any() {
    add(AnyCharMatcher.INSTANCE)
  }

  fun anyOf(str: String) {
    add(SetCharMatcher(str))
  }

  private fun addDsl(
    init: CharMatcherDsl.() -> Unit,
    exclude: Boolean) {
    add(
      CharMatcherDslImpl()
        .also(init)
        .toCharMatcher(),
      exclude
    )
  }

  private fun add(charMatcher: CharMatcher, exclude: Boolean = false) {
    childNodes.add(
      NodeCharMatcher.Child(
        charMatcher = charMatcher,
        exclude = exclude
      )
    )
  }
}

private class CharMatcherDslImpl : CharMatcherDsl() {
  fun toCharMatcher(): CharMatcher = NodeCharMatcher(childNodes).collapse()
}

private interface Collapsible {
  fun collapse(): CharMatcher

  fun shouldCollapse(): Boolean
}

internal class NodeCharMatcher(private val children: List<Child>) :
  CharMatcher, Collapsible {
  override fun matches(char: Char): Boolean {
    var matches = false
    for (child in children) {
      val charMatcher = child.charMatcher
      val exclude = child.exclude
      if (!matches && !exclude) {
        // If not already matching and viewing an inclusion matcher, then
        // `matches` may flip to `true` if a match was found
        matches = charMatcher.matches(char)
      } else if (matches && exclude && charMatcher.matches(char)) {
        // If already matching and viewing an exclusion matcher, then `matches`
        // may flip to `false` if a match was found
        matches = false
      }
    }
    return matches
  }

  override fun collapse(): CharMatcher {
    // The approach to collapsing CharMatchers is this, as a general theme:
    // When walking down the list of CharMatchers, attempt to maintain a single
    // or as few SetCharMatchers as is possible.
    //
    // More specifically, when starting out, a SetCharMatcher is added as the
    // initial child, and then each child is visited. From there, the visited
    // child is either collapsed into the latest 'new' child or forms a new
    // child, potentially altering previous ones to remove superseded rules for
    // certain characters. For example, if a character was previously included
    // but in the current visited node has been excluded, it can be fully
    // excluded from the earlier set.
    //
    // After all children have been visited, the 'new' children are collected
    // into a final CharMatcher and returned according to the functionality in
    // [CharMatcherCollapseState.close].
    val collapseState = CharMatcherCollapseState()
    for (child in children) {
      val charMatcher = collapse(child.charMatcher)
      val exclude = child.exclude
      if (charMatcher !is SetCharMatcher) {
        collapseState.add(
          Child(
            charMatcher = charMatcher,
            exclude = child.exclude
          )
        )
      } else {
        collapseState.addChars(charMatcher.chars, exclude)
      }
    }

    return collapseState.close()
  }

  override fun shouldCollapse(): Boolean = true

  class Child(val charMatcher: CharMatcher, val exclude: Boolean)
}

private class CharMatcherCollapseState {

  private val children = mutableListOf<NodeCharMatcher.Child>()

  init {
    children.add(
      NodeCharMatcher.Child(
        charMatcher = SetCharMatcher(emptySet()),
        exclude = false
      )
    )
  }

  fun newChild() {
    children.add(
      NodeCharMatcher.Child(
        charMatcher = SetCharMatcher(emptySet()),
        exclude = false
      )
    )
  }

  fun add(child: NodeCharMatcher.Child) {
    if (child.charMatcher is NoneCharMatcher) {
      // This child was completely collapsed, move on
      return
    }
    children.add(child)
    newChild()
  }

  fun addChars(chars: Set<Char>, exclude: Boolean) {
    children.forEachIndexed { index, child ->
      val charMatcher = child.charMatcher
      // For all previous children that contain sets of characters,
      // their inclusion/exclusion rules are overwritten by the latest
      // rules. It does not require that the earlier rules were opposite
      // the exclusion rules of the new chars. For example, if 'a' is
      // included earlier in a ruleset and at the end, then it really
      // only needs to be included at the end and can be discarded
      // anywhere it appears earlier than that.
      if (charMatcher is SetCharMatcher
        && chars.any { charMatcher.chars.contains(it) }
      ) {
        val newChars = charMatcher.chars.toMutableSet()
        newChars.removeAll(chars)
        children[index] = NodeCharMatcher.Child(
          charMatcher = SetCharMatcher(newChars.toSet()),
          exclude = child.exclude
        )
      }
    }
    val latestChild = latest()
    // If switching from an excluding context to an including context or vice-versa,
    // flush the current set of characters to a child and start a new context.
    // Otherwise, add the set of characters to the old characters.
    if (latestChild.exclude != exclude) {
      // If about to exclude characters from the overall set but the only
      // CharMatchers before the current one are SetCharMatchers (meaning
      // they're predictable), then this set of characters can be ignored.
      // They're excluding characters from an already empty set of matchable
      // characters, or at least exclusions that were already accounted for by
      // the above loop that removed those characters from the match sets.
      if (exclude && children.all { it.charMatcher is SetCharMatcher }) {
        return
      } else {
        add(
          NodeCharMatcher.Child(
            charMatcher = SetCharMatcher(chars),
            exclude = exclude
          )
        )
      }
    } else {
      children[children.lastIndex] = NodeCharMatcher.Child(
        charMatcher = SetCharMatcher(latestChild.charMatcher.chars + chars),
        exclude = exclude
      )
    }
  }

  fun close(): CharMatcher {
    val children =
      children.filter { it.charMatcher !is SetCharMatcher || it.charMatcher.chars.isNotEmpty() }
    return if (children.isEmpty()) {
      // This node was completely collapsed, so it matches nothing
      NoneCharMatcher.INSTANCE
    } else if (children.size == 1) {
      // This node was collapsed to a single CharMatcher, so proceed by looking
      // at that instead of the node as a whole
      if (children[0].exclude) {
        // In the case that the single child was an "exclude", the whole node
        // matches nothing.
        NoneCharMatcher.INSTANCE
      } else {
        // In the case that the child was an "include", the node is effectively
        // just the single child.
        children[0].charMatcher
      }
    } else {
      NodeCharMatcher(children)
    }
  }

  private fun latest(): LatestChild {
    val lastChild = children.last()
    return LatestChild(
      // This can be assumed, due to how this is managed. However, it also
      // assumes this method is not called during the middle of a flush.
      charMatcher = lastChild.charMatcher as SetCharMatcher,
      exclude = lastChild.exclude
    )
  }

  private class LatestChild(
    val charMatcher: SetCharMatcher,
    val exclude: Boolean)
}

internal class RangeCharMatcher(
  private val range: CharRange) : CharMatcher,
  Collapsible {

  override fun matches(char: Char): Boolean {
    return char in range
  }

  override fun collapse() = SetCharMatcher(range.toSet())

  override fun shouldCollapse(): Boolean = (range.last - range.first) <= 1024
}

private class SingleCharMatcher(val char: Char) : CharMatcher, Collapsible {
  override fun matches(char: Char): Boolean {
    return char == this.char
  }

  override fun collapse() = SetCharMatcher(setOf(char))

  override fun shouldCollapse(): Boolean = true
}

private class AnyCharMatcher : CharMatcher {
  override fun matches(char: Char) = true

  companion object {
    val INSTANCE = AnyCharMatcher()
  }
}

private class NoneCharMatcher : CharMatcher {
  override fun matches(char: Char) = false

  companion object {
    val INSTANCE = NoneCharMatcher()
  }
}

internal class SetCharMatcher(val chars: Set<Char>) : CharMatcher {

  constructor(str: String) : this(str.toCharArray().toSet())

  override fun matches(char: Char) = chars.contains(char)
}

internal fun charMatcher(init: CharMatcherDsl.() -> Unit) =
  CharMatcherDslImpl()
    .also(init)
    .toCharMatcher()

private fun collapse(charMatcher: CharMatcher): CharMatcher =
  if (charMatcher is Collapsible && charMatcher.shouldCollapse()) {
    charMatcher.collapse()
  } else {
    charMatcher
  }
