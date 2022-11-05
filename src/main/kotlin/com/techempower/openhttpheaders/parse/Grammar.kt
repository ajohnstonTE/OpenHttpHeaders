package com.techempower.openhttpheaders.parse

import com.techempower.openhttpheaders.ProcessingException
import java.util.concurrent.atomic.AtomicInteger

// TODO CURRENT: Probably a good idea to use CharSpan here.

internal abstract class Grammar<T> {
  internal val id: Int = idSource.incrementAndGet()

  fun group(key: String): Grammar<T> =
      GroupGrammar(this, key)

  // By default, a capture will imply that it forms a scope, as it's generally
  // going to be the case that a) the grammar accessed by the scope will
  // contain groups or references and b) those references should not be
  // propagated beyond that capture.
  fun <S> capture(
      function: (SingleCaptureContext<String>) -> S
  ): ParsingGrammar<S> {
    return CaptureGrammar(this, function)
  }

  fun transform(
      function: (SingleCaptureContext<String>) -> String
  ): TransformGrammar {
    return TransformGrammar(this, function)
  }

  // This will basically tell the grammar to return a wrapped version of itself
  // that will map the wrapped instance to the context for captures.
  operator fun not(): Grammar<*> = RefGrammar(this)

  abstract fun copy(): Grammar<T>

  fun debug(function: () -> Unit): Grammar<String> =
      DebugGrammar(this, function)

  internal abstract fun match(
      nodeIndex: Int,
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int

  internal abstract fun collect(
      parseParameters: Map<String, Any>,
      scope: ProcessScope,
      nodeIndex: Int,
      tokenizer: Tokenizer
  )

  companion object {
    private val idSource = AtomicInteger()
  }
}

internal class ProcessScope {
  val transforms = mutableListOf<Transform>()
  val groupKeysToIndexes = mutableMapOf<String, MutableList<Int>>()
  val refIdsToIndexes = mutableMapOf<Int, MutableList<Int>>()
  val indexesToValues = mutableMapOf<Int, GroupValue>()
}

internal class DebugGrammar(
    private val grammar: Grammar<*>,
    private val function: () -> Unit
) : Grammar<String>() {
  override fun copy(): Grammar<String> = DebugGrammar(grammar, function)
  override fun match(
      nodeIndex: Int,
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    function.invoke()
    return grammar.match(
        nodeIndex = nodeIndex,
        previousSiblingIndex = previousSiblingIndex,
        tokenizer = tokenizer
    )
  }

  override fun collect(
      parseParameters: Map<String, Any>,
      scope: ProcessScope,
      nodeIndex: Int,
      tokenizer: Tokenizer
  ) {
    function.invoke()
  }
}

internal class SingleCaptureContext<T>(
    val value: T,
    val parseParameters: Map<String, Any>,
    private val tokenizer: Tokenizer,
    private val scope: ProcessScope
) {
  operator fun get(group: String): String? {
    val index = scope.groupKeysToIndexes[group]?.first()
    val text = if (index == null) null else {
      val groupValue = scope.indexesToValues[index]
      if (groupValue != null) {
        groupValue.text
      } else {
        val rangeStartInclusive = tokenizer.state.getRangeStartInclusive(index)
        val rangeEndExclusive = tokenizer.state.getRangeEndExclusive(index)
        tokenizer.input.substring(rangeStartInclusive, rangeEndExclusive)
      }
    }
    return text
  }

  operator fun <S> get(
      group: String,
      grammarHint: Grammar<S>
  ): S? {
    return values(group, grammarHint).firstOrNull()
  }

  operator fun <S> get(grammar: Grammar<S>): S? {
    return values(grammar).firstOrNull()
  }

  fun <S> values(
      group: String,
      grammarHint: Grammar<S>
  ): List<S> {
    val indexes = scope.groupKeysToIndexes[group]
    return if (indexes == null) {
      listOf()
    } else {
      captureAll(indexes, grammarHint)
    }
  }

  fun <S> values(
      grammar: Grammar<S>
  ): List<S> {
    val indexes = scope.refIdsToIndexes[grammar.id]
    return if (indexes == null) {
      listOf()
    } else {
      captureAll(indexes, grammar)
    }
  }

  private fun <T> captureAll(
      indexes: List<Int>,
      grammar: Grammar<T>
  ): List<T> {
    return indexes
        .mapNotNull { scope.indexesToValues[it] }
        .mapNotNull {
          if (grammar is CaptureGrammar<*, *>) {
            @Suppress("UNCHECKED_CAST")
            it.value as T
          } else {
            @Suppress("UNCHECKED_CAST")
            it.text as T
          }
        }
  }
}

internal class CharMatcherGrammar(private val charMatcher: CharMatcher) :
    Grammar<String>() {
  constructor(value: Char) : this(charMatcher { char(value) })

  override fun copy(): Grammar<String> = CharMatcherGrammar(charMatcher)
  override fun match(
      nodeIndex: Int,
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    if (tokenizer.hasNext() && charMatcher.matches(tokenizer.peek())) {
      tokenizer.advance()
      return TokenizerState.SKIP
    }
    return TokenizerState.NONE
  }

  override fun collect(
      parseParameters: Map<String, Any>,
      scope: ProcessScope,
      nodeIndex: Int,
      tokenizer: Tokenizer
  ) {
    // no-op
  }
}

internal class StringGrammar(
    private val value: String,
    private val caseSensitive: Boolean
) : Grammar<String>() {

  override fun copy(): Grammar<String> = StringGrammar(value, caseSensitive)
  override fun match(
      nodeIndex: Int,
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    if (tokenizer.state.isIndexPopulated(nodeIndex)) {
      // Either it matches the string or it doesn't.
      return TokenizerState.NONE
    } else {
      val newNodeIndex = tokenizer.state.reserve(
          grammarId = id,
          rangeStartInclusive = tokenizer.state.getInputIndex(),
          previousSiblingIndex = previousSiblingIndex
      )
      for (char in value) {
        if (!tokenizer.hasNext() || !char.equals(
                tokenizer.peek(),
                ignoreCase = !caseSensitive
            )
        ) {
          return TokenizerState.NONE
        }
        tokenizer.advance()
      }
      tokenizer.state.update(
          index = newNodeIndex,
          grammarValue = TokenizerState.NONE,
          rangeEndExclusive = tokenizer.state.getInputIndex(),
          numberOfChildren = 0
      )
      return newNodeIndex
    }
  }

  override fun collect(
      parseParameters: Map<String, Any>,
      scope: ProcessScope,
      nodeIndex: Int,
      tokenizer: Tokenizer
  ) {
    // no-op
  }

  // Will likely never be used, but supported regardless.
  @Suppress("unused")
  fun caseSensitive(): Grammar<String> = StringGrammar(value, true)
}

internal class AndThenGrammar(
    private val first: Grammar<*>,
    private val second: Grammar<*>
) : Grammar<String>() {
  private val interceptFirst = first is CharMatcherGrammar
  private val interceptSecond = second is CharMatcherGrammar

  override fun copy(): Grammar<String> = AndThenGrammar(first, second)

  override fun match(
      nodeIndex: Int,
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    return if (tokenizer.state.isIndexPopulated(nodeIndex)) {
      matchNextVariation(
          nodeIndex = nodeIndex,
          tokenizer = tokenizer
      )
    } else {
      matchAny(
          previousSiblingIndex = previousSiblingIndex,
          tokenizer = tokenizer
      )
    }
  }

  override fun collect(
      parseParameters: Map<String, Any>,
      scope: ProcessScope,
      nodeIndex: Int,
      tokenizer: Tokenizer
  ) {
    val firstIndex = if (!interceptFirst) {
      tokenizer.state.getNthChildIndex(nodeIndex, 0)
    } else TokenizerState.SKIP
    val secondIndex = if (!interceptSecond) {
      val relativeIndex =  if (!interceptFirst) 1 else 0
      tokenizer.state.getNthChildIndex(nodeIndex, relativeIndex)
    } else TokenizerState.SKIP
    first.collect(
        parseParameters = parseParameters,
        scope = scope,
        nodeIndex = firstIndex,
        tokenizer = tokenizer
    )
    second.collect(
        parseParameters = parseParameters,
        scope = scope,
        nodeIndex = secondIndex,
        tokenizer = tokenizer
    )
  }

  private fun matchNextVariation(
      nodeIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    val originalIndex = tokenizer.state.getInputIndex()
    val firstIndex = if (!interceptFirst) {
      tokenizer.state.getNthChildIndex(nodeIndex, 0)
    } else TokenizerState.SKIP
    val secondIndex = if (!interceptSecond) {
      val relativeIndex =  if (!interceptFirst) 1 else 0
      tokenizer.state.getNthChildIndex(nodeIndex, relativeIndex)
    } else TokenizerState.SKIP
    // Start off by trying for any new variations from the second child
    val newSecondIndex = if (!interceptSecond) {
      second.match(
          nodeIndex = secondIndex,
          previousSiblingIndex = firstIndex,
          tokenizer = tokenizer
      )
    } else TokenizerState.NONE
    if (newSecondIndex != TokenizerState.NONE) {
      tokenizer.state.update(
          index = nodeIndex,
          grammarValue = TokenizerState.NONE,
          rangeEndExclusive = tokenizer.state.getInputIndex(),
          numberOfChildren = 2
      )
      return nodeIndex
    }
    // If no new variations work for the second child, begin trying new
    // variations for both children, starting by restoring back to the first
    // child so that it can try new variations.
    val inputIndexToRestore = if (!interceptFirst) tokenizer.state.getRangeEndExclusive(firstIndex)
        else originalIndex
    if (secondIndex != TokenizerState.SKIP) {
      tokenizer.state.restore(
          inputIndex = inputIndexToRestore,
          blockIndex = secondIndex
      )
    } else {
      tokenizer.state.setInputIndex(inputIndexToRestore)
    }
    return findNextMatch(
        nodeIndex = nodeIndex,
        firstIndex = firstIndex,
        secondIndex = secondIndex,
        tokenizer = tokenizer
    )
  }

  private fun matchAny(
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    val nodeIndex = tokenizer.state.reserve(
        grammarId = id,
        rangeStartInclusive = tokenizer.state.getInputIndex(),
        previousSiblingIndex = previousSiblingIndex
    )
    return findNextMatch(
        nodeIndex = nodeIndex,
        firstIndex = TokenizerState.NONE,
        secondIndex = TokenizerState.NONE,
        tokenizer = tokenizer
    )
  }

  private fun findNextMatch(
      nodeIndex: Int,
      firstIndex: Int,
      secondIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    var newFirstIndex = firstIndex
    var newSecondIndex = secondIndex
    do {
      newFirstIndex = first.match(
          nodeIndex = newFirstIndex,
          previousSiblingIndex = TokenizerState.NONE,
          tokenizer = tokenizer
      )
      if (newFirstIndex == TokenizerState.NONE) {
        return TokenizerState.NONE
      }
      val savePoint = tokenizer.save()
      newSecondIndex = second.match(
          nodeIndex = newSecondIndex,
          previousSiblingIndex = newFirstIndex,
          tokenizer = tokenizer
      )
      if (newSecondIndex == TokenizerState.NONE) {
        // Since `start` is going to have to retry, the grammar needs to roll
        // back to the state that `start` was working with.
        tokenizer.restore(savePoint)
      } else {
        // Normally this checks for != TokenizerState.NONE, but in this case it
        // can also be a CharMatcherGrammar which returns TokenizerState.SKIP
        if (newFirstIndex >= 0) {
          tokenizer.state.setNextSiblingIndex(newFirstIndex, newSecondIndex)
        }
      }
    } while (
    // If `second` did not match but `first` did, then this needs to try a
    // new variation for `first`. However, if either `second` succeeded (in
    // which case the full match has been found) or `first` failed (in
    // which case no match has been found), then it should just stop.
        newSecondIndex == TokenizerState.NONE &&
        newFirstIndex != TokenizerState.NONE
    )
    if (newSecondIndex == TokenizerState.NONE) {
      return TokenizerState.NONE
    }
    tokenizer.state.update(
        index = nodeIndex,
        grammarValue = TokenizerState.NONE,
        rangeEndExclusive = tokenizer.state.getInputIndex(),
        numberOfChildren = (if (!interceptFirst) 1 else 0)
            + (if (!interceptSecond) 1 else 0)
    )
    return nodeIndex
  }
}

// The only grammar that will care about rolling back the tokenizer if
// something does not match is the OrGrammar. For that reason, only the
// OrGrammar should make a copy of the tokenizer. And only for the first node,
// if the second node fails then it can just immediately fail upwards without
// rolling back.
internal class OrGrammar(
    private val a: Grammar<*>,
    private val b: Grammar<*>
) : Grammar<String>() {
  private val aIntercept = a is CharMatcherGrammar
  private val bIntercept = b is CharMatcherGrammar

  override fun copy(): Grammar<String> = OrGrammar(a, b)
  override fun match(
      nodeIndex: Int,
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    return if (tokenizer.state.isIndexPopulated(nodeIndex)) {
      matchNextVariation(
          nodeIndex = nodeIndex,
          tokenizer = tokenizer
      )
    } else {
      matchAny(
          previousSiblingIndex = previousSiblingIndex,
          tokenizer = tokenizer
      )
    }
  }

  override fun collect(
      parseParameters: Map<String, Any>,
      scope: ProcessScope,
      nodeIndex: Int,
      tokenizer: Tokenizer
  ) {
    val value = tokenizer.state.getGrammarValue(nodeIndex)
    val grammar = if (value == 0) a else b
    val childIndex = if (grammar !is CharMatcherGrammar) {
      tokenizer.state.getNthChildIndex(nodeIndex, 0)
    } else TokenizerState.NONE
    grammar.collect(
        parseParameters = parseParameters,
        scope = scope,
        nodeIndex = childIndex,
        tokenizer = tokenizer
    )
  }

  private fun matchNextVariation(
      nodeIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    var value = tokenizer.state.getGrammarValue(nodeIndex)
    var newChildIndex: Int = TokenizerState.NONE
    if (value == 0) {
      /// First try with `a` if `a` was the previous match grammar (value 0)
      val aIndex =
          if (aIntercept) TokenizerState.NONE else tokenizer.state.getNthChildIndex(
              nodeIndex,
              0
          )
      newChildIndex = a.match(
          nodeIndex = aIndex,
          previousSiblingIndex = TokenizerState.NONE,
          tokenizer = tokenizer
      )
      if (newChildIndex == TokenizerState.NONE) {
        value = 1
        if (aIndex != TokenizerState.SKIP) {
          tokenizer.state.restore(
              inputIndex = tokenizer.state.getRangeStartInclusive(nodeIndex),
              blockIndex = aIndex
          )
        }
      }
    }
    if (newChildIndex == TokenizerState.NONE) {
      val originalValue = tokenizer.state.getGrammarValue(nodeIndex)
      // Next try with `b` if it was previously on `a` but `a` didn't have any
      // other matching variations, or if it was previously on `b`
      newChildIndex = b.match(
          nodeIndex = if (bIntercept && originalValue == 1) TokenizerState.NONE else tokenizer.state.getNthChildIndex(
              nodeIndex,
              0
          ),
          previousSiblingIndex = TokenizerState.NONE,
          tokenizer = tokenizer
      )
    }
    if (newChildIndex == TokenizerState.NONE) {
      return TokenizerState.NONE
    }
    tokenizer.state.update(
        index = nodeIndex,
        grammarValue = value,
        rangeEndExclusive = tokenizer.state.getInputIndex(),
        numberOfChildren = 1
    )
    return nodeIndex
  }

  private fun matchAny(
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    val newNodeIndex = tokenizer.state.reserve(
        grammarId = id,
        rangeStartInclusive = tokenizer.state.getInputIndex(),
        previousSiblingIndex = previousSiblingIndex
    )
    var value = 0
    val savePoint = tokenizer.save()
    var matchIndex = a.match(
        nodeIndex = TokenizerState.NONE,
        previousSiblingIndex = TokenizerState.NONE,
        tokenizer = tokenizer
    )
    if (matchIndex == TokenizerState.NONE) {
      tokenizer.restore(savePoint)
      matchIndex = b.match(
          nodeIndex = TokenizerState.NONE,
          previousSiblingIndex = TokenizerState.NONE,
          tokenizer = tokenizer
      )
      value = 1
    }
    if (matchIndex == TokenizerState.NONE) {
      return TokenizerState.NONE
    }
    tokenizer.state.update(
        index = newNodeIndex,
        grammarValue = value,
        rangeEndExclusive = tokenizer.state.getInputIndex(),
        numberOfChildren = if (matchIndex != TokenizerState.SKIP) 1 else 0
    )
    return newNodeIndex
  }
}

internal class RefGrammar<T>(private val ref: Grammar<T>) : Grammar<T>() {
  private val intercept = ref is CharMatcherGrammar

  override fun copy(): Grammar<T> = RefGrammar(ref)

  @Suppress("DuplicatedCode")
  override fun match(
      nodeIndex: Int,
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    if (intercept && tokenizer.state.isIndexPopulated(nodeIndex)) {
      return TokenizerState.NONE
    }
    val startIndex = tokenizer.state.getInputIndex()
    val refIndex = ref.match(
        nodeIndex = nodeIndex,
        previousSiblingIndex = previousSiblingIndex,
        tokenizer = tokenizer
    )
    if (refIndex == TokenizerState.SKIP) {
      val newRefIndex = tokenizer.state.reserve(
          grammarId = ref.id,
          rangeStartInclusive = startIndex,
          previousSiblingIndex = previousSiblingIndex
      )
      tokenizer.state.update(
          newRefIndex,
          grammarValue = TokenizerState.NONE,
          rangeEndExclusive = tokenizer.state.getInputIndex(),
          numberOfChildren = 0
      )
      return newRefIndex
    } else {
      return refIndex
    }
  }

  override fun collect(
      parseParameters: Map<String, Any>,
      scope: ProcessScope,
      nodeIndex: Int,
      tokenizer: Tokenizer
  ) {
    scope.refIdsToIndexes.computeIfAbsent(ref.id) { mutableListOf() }
    scope.refIdsToIndexes[ref.id]!! += nodeIndex
    ref.collect(
        scope = scope,
        nodeIndex = nodeIndex,
        tokenizer = tokenizer,
        parseParameters = parseParameters
    )
    if (ref !is CaptureGrammar<*, *>) {
      val startInclusive = tokenizer.state.getRangeStartInclusive(nodeIndex)
      val endExclusive = tokenizer.state.getRangeEndExclusive(nodeIndex)
      scope.indexesToValues[nodeIndex] = GroupValue(
          originalRange = startInclusive until endExclusive
      )
    }
  }
}

internal class GroupGrammar<T>(
    private val grouped: Grammar<T>,
    private val group: String
) : Grammar<T>() {
  private val intercept = grouped is CharMatcherGrammar

  override fun copy(): Grammar<T> = GroupGrammar(grouped, group)

  @Suppress("DuplicatedCode")
  override fun match(
      nodeIndex: Int,
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    if (intercept && tokenizer.state.isIndexPopulated(nodeIndex)) {
      return TokenizerState.NONE
    }
    val startIndex = tokenizer.state.getInputIndex()
    val refIndex = grouped.match(
        nodeIndex = nodeIndex,
        previousSiblingIndex = previousSiblingIndex,
        tokenizer = tokenizer
    )
    if (refIndex == TokenizerState.SKIP) {
      val newRefIndex = tokenizer.state.reserve(
          grammarId = grouped.id,
          rangeStartInclusive = startIndex,
          previousSiblingIndex = previousSiblingIndex
      )
      tokenizer.state.update(
          newRefIndex,
          grammarValue = TokenizerState.NONE,
          rangeEndExclusive = tokenizer.state.getInputIndex(),
          numberOfChildren = 0
      )
      return newRefIndex
    } else {
      return refIndex
    }
  }

  override fun collect(
      parseParameters: Map<String, Any>,
      scope: ProcessScope,
      nodeIndex: Int,
      tokenizer: Tokenizer
  ) {
    scope.groupKeysToIndexes.computeIfAbsent(group) { mutableListOf() }
    scope.groupKeysToIndexes[group]!! += nodeIndex
    grouped.collect(
        scope = scope,
        nodeIndex = nodeIndex,
        tokenizer = tokenizer,
        parseParameters = parseParameters
    )
    if (grouped !is CaptureGrammar<*, *>) {
      val startInclusive = tokenizer.state.getRangeStartInclusive(nodeIndex)
      val endExclusive = tokenizer.state.getRangeEndExclusive(nodeIndex)
      scope.indexesToValues[nodeIndex] = GroupValue(
          originalRange = startInclusive until endExclusive
      )
    }
  }
}

internal abstract class ParsingGrammar<T> : Grammar<T>() {
  abstract fun parse(
      input: String,
      parseParameters: Map<String, Any> = mapOf()
  ): T
}

internal class CaptureGrammar<S, T>(
    private val grammar: Grammar<S>,
    private val captureFunction: (SingleCaptureContext<String>) -> T
) : ParsingGrammar<T>() {

  override fun parse(input: String, parseParameters: Map<String, Any>): T {
    val tokenizer = Tokenizer(input)
    val matchIndex = match(
        tokenizer = tokenizer,
        nodeIndex = TokenizerState.NONE,
        previousSiblingIndex = TokenizerState.NONE
    )
    if (matchIndex == TokenizerState.NONE
        || (tokenizer.state.getInputIndex() != input.length)
    ) {
      throw ProcessingException(
          "Could not fully parse \"$input\"," +
              " parsed up to position ${tokenizer.state.getInputIndex()}.",
      )
    }
    val scope = ProcessScope()
    collect(
        scope = scope,
        nodeIndex = matchIndex,
        tokenizer = tokenizer,
        parseParameters = parseParameters
    )
    @Suppress("UNCHECKED_CAST")
    return scope.indexesToValues[matchIndex]!!.value as T
  }

  override fun copy(): Grammar<T> = CaptureGrammar(grammar, captureFunction)

  @Suppress("DuplicatedCode")
  override fun match(
      nodeIndex: Int,
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    val childIndex: Int
    val newOrOldNodeIndex: Int
    if (tokenizer.state.isIndexPopulated(nodeIndex)) {
      childIndex = grammar.match(
          nodeIndex = tokenizer.state.getNthChildIndex(nodeIndex, 0),
          previousSiblingIndex = TokenizerState.NONE,
          tokenizer = tokenizer
      )
      newOrOldNodeIndex = nodeIndex
    } else {
      newOrOldNodeIndex = tokenizer.state.reserve(
          grammarId = id,
          rangeStartInclusive = tokenizer.state.getInputIndex(),
          previousSiblingIndex = previousSiblingIndex
      )
      childIndex = grammar.match(
          nodeIndex = TokenizerState.NONE,
          previousSiblingIndex = TokenizerState.NONE,
          tokenizer = tokenizer
      )
      if (childIndex != TokenizerState.NONE) {
        tokenizer.state.update(
            index = newOrOldNodeIndex,
            grammarValue = TokenizerState.NONE,
            rangeEndExclusive = tokenizer.state.getInputIndex(),
            numberOfChildren = 1
        )
      }
    }
    return if (childIndex != TokenizerState.NONE) {
      newOrOldNodeIndex
    } else {
      TokenizerState.NONE
    }
  }

  @Suppress("DuplicatedCode")
  override fun collect(
      parseParameters: Map<String, Any>,
      scope: ProcessScope,
      nodeIndex: Int,
      tokenizer: Tokenizer
  ) {
    val newScope = ProcessScope()
    val childIndex = tokenizer.state.getNthChildIndex(nodeIndex, 0)
    grammar.collect(
        parseParameters = parseParameters,
        scope = newScope,
        nodeIndex = childIndex,
        tokenizer = tokenizer
    )
    val scopeValue = tokenizer.getScopeValue(
        nodeIndex = nodeIndex,
        scope = newScope
    )
    val rangeStartInclusive = tokenizer.state.getRangeStartInclusive(nodeIndex)
    val rangeEndExclusive = tokenizer.state.getRangeEndExclusive(nodeIndex)
    scope.indexesToValues[nodeIndex] = GroupValue(
        originalRange = rangeStartInclusive until rangeEndExclusive,
        text = scopeValue,
        value = captureFunction.invoke(
            SingleCaptureContext(
                // It matched so it definitely has a value
                value = scopeValue,
                parseParameters = parseParameters,
                tokenizer = tokenizer,
                scope = newScope
            )
        )
    )
  }
}

internal class TransformGrammar(
    private val grammar: Grammar<*>,
    private val transformFunction: (SingleCaptureContext<String>) -> String
) : Grammar<String>() {
  override fun copy(): Grammar<String> =
      TransformGrammar(grammar, transformFunction)

  @Suppress("DuplicatedCode")
  override fun match(
      nodeIndex: Int,
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    val childIndex: Int
    val newOrOldNodeIndex: Int
    if (tokenizer.state.isIndexPopulated(nodeIndex)) {
      childIndex = grammar.match(
          nodeIndex = tokenizer.state.getNthChildIndex(nodeIndex, 0),
          previousSiblingIndex = TokenizerState.NONE,
          tokenizer = tokenizer
      )
      newOrOldNodeIndex = nodeIndex
    } else {
      newOrOldNodeIndex = tokenizer.state.reserve(
          grammarId = id,
          rangeStartInclusive = tokenizer.state.getInputIndex(),
          previousSiblingIndex = previousSiblingIndex
      )
      childIndex = grammar.match(
          nodeIndex = TokenizerState.NONE,
          previousSiblingIndex = TokenizerState.NONE,
          tokenizer = tokenizer
      )
      tokenizer.state.update(
          index = newOrOldNodeIndex,
          grammarValue = TokenizerState.NONE,
          rangeEndExclusive = tokenizer.state.getInputIndex(),
          numberOfChildren = 1
      )
    }
    return if (childIndex != TokenizerState.NONE) {
      newOrOldNodeIndex
    } else {
      TokenizerState.NONE
    }
  }

  @Suppress("DuplicatedCode")
  override fun collect(
      parseParameters: Map<String, Any>,
      scope: ProcessScope,
      nodeIndex: Int,
      tokenizer: Tokenizer
  ) {
    val newScope = ProcessScope()
    val childIndex = tokenizer.state.getNthChildIndex(nodeIndex, 0)
    grammar.collect(
        parseParameters = parseParameters,
        scope = newScope,
        nodeIndex = childIndex,
        tokenizer = tokenizer
    )
    val scopeValue = tokenizer.getScopeValue(
        nodeIndex = nodeIndex,
        scope = newScope
    )
    val rangeStartInclusive = tokenizer.state.getRangeStartInclusive(nodeIndex)
    val rangeEndExclusive = tokenizer.state.getRangeEndExclusive(nodeIndex)
    val transformedText = transformFunction.invoke(
        SingleCaptureContext(
            // It matched so it definitely has a value
            value = scopeValue,
            parseParameters = parseParameters,
            tokenizer = tokenizer,
            scope = newScope
        )
    )
    scope.indexesToValues[nodeIndex] = GroupValue(
        originalRange = rangeStartInclusive until rangeEndExclusive,
        text = scopeValue,
        value = transformedText
    )
    scope.transforms += Transform(
        startInclusive = rangeStartInclusive,
        endExclusive = rangeEndExclusive,
        value = transformedText
    )
  }
}

internal class XOrMoreGrammar<T>(
    private val grammar: Grammar<T>,
    private val lowerLimit: Int
) : Grammar<T>() {
  private val intercept = grammar is CharMatcherGrammar

  override fun copy(): Grammar<T> = XOrMoreGrammar(grammar, lowerLimit)

  override fun match(
      nodeIndex: Int,
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    return if (tokenizer.state.isIndexPopulated(nodeIndex)) {
      matchNextVariation(
          nodeIndex = nodeIndex,
          tokenizer = tokenizer
      )
    } else {
      matchAll(
          previousSiblingIndex = previousSiblingIndex,
          tokenizer = tokenizer
      )
    }
  }

  override fun collect(
      parseParameters: Map<String, Any>,
      scope: ProcessScope,
      nodeIndex: Int,
      tokenizer: Tokenizer
  ) {
    val value = tokenizer.state.getGrammarValue(nodeIndex)
    if (!intercept) {
      for (i in 0 until value) {
        val childIndex = tokenizer.state.getNthChildIndex(nodeIndex, i)
        grammar.collect(
            parseParameters = parseParameters,
            scope = scope,
            nodeIndex = childIndex,
            tokenizer = tokenizer
        )
      }
    }
  }

  private fun matchNextVariation(
      nodeIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    var value = tokenizer.state.getGrammarValue(nodeIndex)
    var numberOfChildren = tokenizer.state.getNumberOfChildren(nodeIndex)
    var rangeEndExclusive = tokenizer.state.getRangeEndExclusive(nodeIndex)
    if (value == 0) {
      // It's possible to match 0 times, so this must be accounted for
      return TokenizerState.NONE
    }
    // This will only happen if the child nodes should not be skipped
    if (numberOfChildren == value) {
      val lastChildIndex =
          tokenizer.state.getNthChildIndex(nodeIndex, value - 1)
      val secondToLastChildIndex =
          if (value >= 2) tokenizer.state.getNthChildIndex(
              nodeIndex,
              value - 2
          ) else TokenizerState.NONE
      val newMatchIndex = grammar.match(
          nodeIndex = lastChildIndex,
          previousSiblingIndex = secondToLastChildIndex,
          tokenizer = tokenizer
      )
      if (newMatchIndex == TokenizerState.NONE) {
        // Already on the last match. No need to restore, the parent can handle
        // that.
        if (value == 1) {
          return TokenizerState.NONE
        }
        val newInputIndex =
            tokenizer.state.getRangeStartInclusive(lastChildIndex)
        tokenizer.state.restore(newInputIndex, lastChildIndex)
        numberOfChildren -= 1
        rangeEndExclusive = newInputIndex
      } else {
        if (newMatchIndex == TokenizerState.SKIP) {
          rangeEndExclusive =
              tokenizer.state.getRangeEndExclusive(lastChildIndex)
        }
      }
    } else {
      // Assumed to be a CharMatcherGrammar, so it would only have matched a
      // single character
      rangeEndExclusive -= 1
    }
    value -= 1
    tokenizer.state.update(
        index = nodeIndex,
        grammarValue = value,
        rangeEndExclusive = rangeEndExclusive,
        numberOfChildren = numberOfChildren
    )
    tokenizer.state.setInputIndex(rangeEndExclusive)
    return nodeIndex
  }

  private fun matchAll(
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    val newNodeIndex = tokenizer.state.reserve(
        grammarId = id,
        rangeStartInclusive = tokenizer.state.getInputIndex(),
        previousSiblingIndex = previousSiblingIndex
    )
    var savePoint = tokenizer.save()
    var previousChildIndex = TokenizerState.NONE
    var value = 0
    var numberOfChildren = 0
    do {
      val childIndex = grammar.match(
          tokenizer = tokenizer,
          nodeIndex = TokenizerState.NONE,
          previousSiblingIndex = previousChildIndex,
      )
      if (childIndex != TokenizerState.NONE) {
        value += 1
        // CharMatcherGrammars, for the sake of memory conservation, do not
        // count as children.
        if (childIndex != TokenizerState.SKIP) {
          if (previousChildIndex != TokenizerState.NONE) {
            tokenizer.state.setNextSiblingIndex(previousChildIndex, childIndex)
          }
          numberOfChildren += 1
        }
        savePoint = tokenizer.save()
        previousChildIndex = childIndex
      } else {
        tokenizer.restore(savePoint)
      }
    } while (childIndex != TokenizerState.NONE)
    return if (value >= lowerLimit) {
      tokenizer.state.update(
          index = newNodeIndex,
          grammarValue = value,
          rangeEndExclusive = tokenizer.state.getInputIndex(),
          numberOfChildren = numberOfChildren
      )
      // If enough matches were found, let the parent know where the child
      // can be found.
      newNodeIndex
    } else {
      // If not enough matches, just return the NONE flag. The parent will
      // clean up and restore.
      TokenizerState.NONE
    }
  }
}

internal class RangeGrammar<T>(
    private val grammar: Grammar<T>,
    private val range: IntRange
) : Grammar<T>() {
  private val intercept = grammar is CharMatcherGrammar

  override fun copy(): Grammar<T> = RangeGrammar(grammar, range)
  override fun match(
      nodeIndex: Int,
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    return if (tokenizer.state.isIndexPopulated(nodeIndex)) {
      matchNextVariation(
          nodeIndex = nodeIndex,
          tokenizer = tokenizer
      )
    } else {
      matchAll(
          previousSiblingIndex = previousSiblingIndex,
          tokenizer = tokenizer
      )
    }
  }

  override fun collect(
      parseParameters: Map<String, Any>,
      scope: ProcessScope,
      nodeIndex: Int,
      tokenizer: Tokenizer
  ) {
    if (!intercept) {
      val value = tokenizer.state.getGrammarValue(nodeIndex)
      for (i in 0 until value) {
        val childIndex = tokenizer.state.getNthChildIndex(nodeIndex, i)
        grammar.collect(
            parseParameters = parseParameters,
            scope = scope,
            nodeIndex = childIndex,
            tokenizer = tokenizer
        )
      }
    }
  }

  private fun matchNextVariation(
      nodeIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    var value = tokenizer.state.getGrammarValue(nodeIndex)
    var numberOfChildren = tokenizer.state.getNumberOfChildren(nodeIndex)
    var rangeEndExclusive = tokenizer.state.getRangeEndExclusive(nodeIndex)
    if (value == 0) {
      // It's possible to match 0 times, so this must be accounted for
      return TokenizerState.NONE
    }
    // This will only happen if the child nodes should not be skipped
    if (numberOfChildren == value) {
      val lastChildIndex =
          tokenizer.state.getNthChildIndex(nodeIndex, value - 1)
      val secondToLastChildIndex =
          if (value >= 2) tokenizer.state.getNthChildIndex(
              nodeIndex,
              value - 2
          ) else TokenizerState.NONE
      val newMatchIndex = grammar.match(
          nodeIndex = lastChildIndex,
          previousSiblingIndex = secondToLastChildIndex,
          tokenizer = tokenizer
      )
      if (newMatchIndex == TokenizerState.NONE) {
        // Already on the last allowable match. No need to restore, the parent
        // can handle that.
        if (value == range.first) {
          return TokenizerState.NONE
        }
        val newInputIndex =
            tokenizer.state.getRangeStartInclusive(lastChildIndex)
        tokenizer.state.restore(newInputIndex, lastChildIndex)
        numberOfChildren -= 1
        rangeEndExclusive = newInputIndex
      } else {
        if (newMatchIndex == TokenizerState.SKIP) {
          rangeEndExclusive =
              tokenizer.state.getRangeEndExclusive(lastChildIndex)
        }
      }
    } else {
      // Assumed to be a CharMatcherGrammar, so it would only have matched a
      // single character
      rangeEndExclusive -= 1
    }
    value -= 1
    tokenizer.state.update(
        index = nodeIndex,
        grammarValue = value,
        rangeEndExclusive = rangeEndExclusive,
        numberOfChildren = numberOfChildren
    )
    tokenizer.state.setInputIndex(rangeEndExclusive)
    return nodeIndex
  }

  // TODO CURRENT: A lot of these functions are copy-pasted. Eventually these
  //  should be refactored into common methods as is possible.
  @Suppress("DuplicatedCode")
  private fun matchAll(
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    val newNodeIndex = tokenizer.state.reserve(
        grammarId = id,
        rangeStartInclusive = tokenizer.state.getInputIndex(),
        previousSiblingIndex = previousSiblingIndex
    )
    var savePoint = tokenizer.save()
    var previousChildIndex = TokenizerState.NONE
    var value = 0
    var numberOfChildren = 0
    do {
      val childIndex = grammar.match(
          tokenizer = tokenizer,
          nodeIndex = TokenizerState.NONE,
          previousSiblingIndex = previousChildIndex,
      )
      if (childIndex != TokenizerState.NONE) {
        value += 1
        // CharMatcherGrammars, for the sake of memory conservation, do not
        // count as children.
        if (childIndex != TokenizerState.SKIP) {
          if (previousChildIndex != TokenizerState.NONE) {
            tokenizer.state.setNextSiblingIndex(previousChildIndex, childIndex)
          }
          numberOfChildren += 1
        }
        savePoint = tokenizer.save()
        previousChildIndex = childIndex
      } else {
        tokenizer.restore(savePoint)
      }
    } while (childIndex != TokenizerState.NONE && value < range.last)
    return if (value >= range.first) {
      tokenizer.state.update(
          index = newNodeIndex,
          grammarValue = value,
          rangeEndExclusive = tokenizer.state.getInputIndex(),
          numberOfChildren = numberOfChildren
      )
      // If enough matches were found, let the parent know where the child
      // can be found.
      newNodeIndex
    } else {
      // If not enough matches, just return the NONE flag. The parent will
      // clean up and restore.
      TokenizerState.NONE
    }
  }
}

internal class OrCountGrammar<T>(
    private val grammar: Grammar<T>,
    private val options: Set<Int>
) : Grammar<T>() {
  private val intercept = grammar is CharMatcherGrammar

  private val max = options.max()
  private val min = options.min()

  override fun copy(): Grammar<T> = OrCountGrammar(grammar, options)

  override fun collect(
      parseParameters: Map<String, Any>,
      scope: ProcessScope,
      nodeIndex: Int,
      tokenizer: Tokenizer
  ) {
    if (!intercept) {
      val value = tokenizer.state.getGrammarValue(nodeIndex)
      for (i in 0 until value) {
        val childIndex = tokenizer.state.getNthChildIndex(nodeIndex, i)
        grammar.collect(
            parseParameters = parseParameters,
            scope = scope,
            nodeIndex = childIndex,
            tokenizer = tokenizer
        )
      }
    }
  }

  override fun match(
      nodeIndex: Int,
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    return if (tokenizer.state.isIndexPopulated(nodeIndex)) {
      matchNextVariation(
          nodeIndex = nodeIndex,
          tokenizer = tokenizer
      )
    } else {
      matchAll(
          previousSiblingIndex = previousSiblingIndex,
          tokenizer = tokenizer
      )
    }
  }

  private fun matchNextVariation(
      nodeIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    var value = tokenizer.state.getGrammarValue(nodeIndex)
    var numberOfChildren = tokenizer.state.getNumberOfChildren(nodeIndex)
    var rangeEndExclusive = tokenizer.state.getRangeEndExclusive(nodeIndex)
    if (value == 0) {
      // It's possible to match 0 times, so this must be accounted for
      return TokenizerState.NONE
    }
    do {
      // This will only happen if the child nodes should not be skipped
      if (numberOfChildren == value) {
        val lastChildIndex =
            tokenizer.state.getNthChildIndex(nodeIndex, value - 1)
        val secondToLastChildIndex =
            if (value >= 2) tokenizer.state.getNthChildIndex(
                nodeIndex,
                value - 2
            ) else TokenizerState.NONE
        val newMatchIndex = grammar.match(
            nodeIndex = lastChildIndex,
            previousSiblingIndex = secondToLastChildIndex,
            tokenizer = tokenizer
        )
        if (newMatchIndex == TokenizerState.NONE) {
          // Already on the last allowable match. No need to restore, the parent
          // can handle that.
          if (value == min) {
            return TokenizerState.NONE
          }
          val newInputIndex =
              tokenizer.state.getRangeStartInclusive(lastChildIndex)
          tokenizer.state.restore(newInputIndex, lastChildIndex)
          numberOfChildren -= 1
          rangeEndExclusive = newInputIndex
        } else {
          if (newMatchIndex == TokenizerState.SKIP) {
            rangeEndExclusive =
                tokenizer.state.getRangeEndExclusive(lastChildIndex)
          }
        }
      } else {
        // Assumed to be a CharMatcherGrammar, so it would only have matched a
        // single character
        rangeEndExclusive -= 1
      }
      value -= 1
    } while (!options.contains(value))
    tokenizer.state.update(
        index = nodeIndex,
        grammarValue = value,
        rangeEndExclusive = rangeEndExclusive,
        numberOfChildren = numberOfChildren
    )
    tokenizer.state.setInputIndex(rangeEndExclusive)
    return nodeIndex
  }

  @Suppress("DuplicatedCode")
  private fun matchAll(
      previousSiblingIndex: Int,
      tokenizer: Tokenizer
  ): Int {
    val newNodeIndex = tokenizer.state.reserve(
        grammarId = id,
        rangeStartInclusive = tokenizer.state.getInputIndex(),
        previousSiblingIndex = previousSiblingIndex
    )
    var savePoint = tokenizer.save()
    var previousChildIndex = TokenizerState.NONE
    var value = 0
    var internalValue = 0
    var numberOfChildren = 0
    var internalNumberOfChildren = 0
    do {
      val childIndex = grammar.match(
          tokenizer = tokenizer,
          nodeIndex = TokenizerState.NONE,
          previousSiblingIndex = previousChildIndex,
      )
      if (childIndex != TokenizerState.NONE) {
        internalValue += 1
        // CharMatcherGrammars, for the sake of memory conservation, do not
        // count as children.
        if (childIndex != TokenizerState.SKIP) {
          if (previousChildIndex != TokenizerState.NONE) {
            tokenizer.state.setNextSiblingIndex(previousChildIndex, childIndex)
          }
          internalNumberOfChildren += 1
        }
        previousChildIndex = childIndex
        if (options.contains(internalValue)) {
          savePoint = tokenizer.save()
          value = internalValue
          numberOfChildren = internalNumberOfChildren
        }
      } else {
        tokenizer.restore(savePoint)
      }
    } while (childIndex != TokenizerState.NONE && value < max)
    return if (value >= min) {
      tokenizer.state.update(
          index = newNodeIndex,
          grammarValue = value,
          rangeEndExclusive = tokenizer.state.getInputIndex(),
          numberOfChildren = numberOfChildren
      )
      // If enough matches were found, let the parent know where the child
      // can be found.
      newNodeIndex
    } else {
      // If not enough matches, just return the NONE flag. The parent will
      // clean up and restore.
      TokenizerState.NONE
    }
  }
}
