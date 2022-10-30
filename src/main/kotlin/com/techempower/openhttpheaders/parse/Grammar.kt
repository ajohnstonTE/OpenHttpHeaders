package com.techempower.openhttpheaders.parse

import com.techempower.openhttpheaders.ProcessingException

// TODO CURRENT: Probably a good idea to use CharSpan here.

internal abstract class Grammar<T> {
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

  internal abstract fun process(
      input: String,
      tokenizer: Tokenizer
  ): Boolean
}

internal class SingleCaptureContext<T>(
    val value: T,
    val parseParameters: Map<String, Any>,
    private val tokenizer: Tokenizer
) {
  operator fun get(group: String): SimpleCaptureContext {
    val tokenizerContext = tokenizer.getContext(group)
    return SimpleCaptureContext(
        value = tokenizerContext?.text
    )
  }

  operator fun <S> get(
      group: String,
      grammarHint: Grammar<S>
  ): SingleCaptureContext<S?> {
    val tokenizerContext = tokenizer.getContext(group)
    return captureSingle(tokenizerContext, grammarHint)
  }

  operator fun <S> get(grammar: Grammar<S>): SingleCaptureContext<S?> {
    val tokenizerContext = tokenizer.getContext(grammar)
    return captureSingle(tokenizerContext, grammar)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  fun <S> getAll(
      key: String,
      grammarHint: Grammar<S>
  ): List<SingleCaptureContext<S>> {
    val tokenizerContexts = tokenizer.getAllContexts(key)
    return captureAll(tokenizerContexts, grammarHint)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  fun <S> getAll(grammar: Grammar<S>): List<SingleCaptureContext<S>> {
    val tokenizerContexts = tokenizer.getAllContexts(grammar)
    return captureAll(tokenizerContexts, grammar)
  }

  private fun <T> captureSingle(
      tokenizerContext: GroupValue?,
      grammar: Grammar<T>
  ): SingleCaptureContext<T?> {
    val value: Any? = if (tokenizerContext == null) {
      null
    } else {
      if (grammar is CaptureGrammar<*, *>) {
        tokenizer.forLatestScope(grammar) {
          grammar.doCapture(tokenizer, parseParameters)
        }
      } else {
        tokenizerContext.text
      }
    }
    @Suppress("UNCHECKED_CAST")
    return SingleCaptureContext(
        value = value as T?,
        parseParameters,
        tokenizer
    )
  }

  private fun <T> captureAll(
      tokenizerContexts: List<GroupValue>,
      grammar: Grammar<T>
  ): List<SingleCaptureContext<T>> {
    return tokenizerContexts.map { tokenizerContext ->
      val value: Any = if (grammar is CaptureGrammar<*, *>) {
        val original = tokenizer.save()
        tokenizer.restore(tokenizerContext.savePoint!!)
        val returned = grammar.doCapture(tokenizer, parseParameters)!!
        tokenizer.restore(original)
        returned
      } else {
        tokenizerContext.text!!
      }
      @Suppress("UNCHECKED_CAST")
      SingleCaptureContext(
          value = value as T,
          parseParameters,
          tokenizer
      )
    }
  }

  fun <S> values(grammar: Grammar<S>): List<S> =
      getAll(grammar).map { it.value }

  fun <S> values(
      key: String,
      grammarHint: Grammar<S>
  ): List<S> =
      getAll(key, grammarHint).map { it.value }
}

internal class SimpleCaptureContext(val value: String?)

internal class CharMatcherGrammar(private val charMatcher: CharMatcher) :
    Grammar<String>() {
  constructor(value: Char) : this(charMatcher { char(value) })

  override fun copy(): Grammar<String> = CharMatcherGrammar(charMatcher)

  override fun process(input: String, tokenizer: Tokenizer): Boolean {
    if (tokenizer.hasNext() && charMatcher.matches(tokenizer.peek())) {
      tokenizer.advance()
      return true
    }
    return false
  }
}

internal class AndThenGrammar(
    private val first: Grammar<*>,
    private val second: Grammar<*>
) : Grammar<String>() {
  override fun copy(): Grammar<String> = AndThenGrammar(first, second)

  override fun process(input: String, tokenizer: Tokenizer): Boolean {
    return first.process(input, tokenizer)
        && second.process(input, tokenizer)
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
  override fun copy(): Grammar<String> = OrGrammar(a, b)

  override fun process(input: String, tokenizer: Tokenizer): Boolean {
    val savePoint = tokenizer.save()
    if (a.process(input, tokenizer)) {
      return true
    }
    tokenizer.restore(savePoint)
    return b.process(input, tokenizer)
  }
}

internal class RefGrammar<T>(private val ref: Grammar<T>) : Grammar<T>() {
  override fun copy(): Grammar<T> = RefGrammar(ref)

  @Suppress("DuplicatedCode")
  override fun process(input: String, tokenizer: Tokenizer): Boolean {
    val initialIndex = tokenizer.index
    if (!ref.process(input, tokenizer)) {
      return false
    }
    var savePoint: TokenizerSavePoint? = null
    if (ref is CaptureGrammar<*, *>) {
      tokenizer.forLatestScope(ref) {
        savePoint = tokenizer.save()
      }
    }
    tokenizer.addContext(
        grammar = ref,
        initialIndex until tokenizer.index,
        savePoint = savePoint
    )
    return true
  }
}

internal class GroupGrammar<T>(
    private val grouped: Grammar<T>,
    private val group: String
) : Grammar<T>() {
  override fun copy(): Grammar<T> = GroupGrammar(grouped, group)

  @Suppress("DuplicatedCode")
  override fun process(input: String, tokenizer: Tokenizer): Boolean {
    val initialIndex = tokenizer.index
    if (!grouped.process(input, tokenizer)) {
      return false
    }
    var savePoint: TokenizerSavePoint? = null
    if (grouped is CaptureGrammar<*, *>) {
      tokenizer.forLatestScope(grouped) {
        savePoint = tokenizer.save()
      }
    }
    tokenizer.addContext(
        group = group,
        range = initialIndex until tokenizer.index,
        grammar = grouped,
        savePoint = savePoint
    )
    return true
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
  override fun process(input: String, tokenizer: Tokenizer): Boolean {
    return tokenizer.addScope(this) {
      val initialIndex = tokenizer.index
      if (grammar.process(input, tokenizer)) {
        tokenizer.setScopeValue(initialIndex, tokenizer.index)
        return@addScope true
      }
      return@addScope false
    }
  }

  override fun parse(input: String, parseParameters: Map<String, Any>): T {
    val tokenizer = Tokenizer(input, index = 0)
    if (!process(input, tokenizer)
        || (tokenizer.index != input.length)
    ) {
      throw ProcessingException(
          "Could not fully parse \"$input\"," +
              " parsed up to position ${tokenizer.index}.",
      )
    }
    return tokenizer.forLatestScope(this) {
      doCapture(tokenizer, parseParameters)
    }
  }

  override fun copy(): Grammar<T> = CaptureGrammar(grammar, captureFunction)

  fun doCapture(tokenizer: Tokenizer, parseParameters: Map<String, Any>): T {
    return captureFunction.invoke(
        SingleCaptureContext(
            // It matched so it definitely has a value
            tokenizer.getScopeValue(parseParameters)!!,
            parseParameters,
            tokenizer
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

  override fun process(input: String, tokenizer: Tokenizer): Boolean {
    val initialIndex = tokenizer.index
    var savePoint: TokenizerSavePoint? = null
    val success = tokenizer.addScope(this) {
      val successful = grammar.process(input, tokenizer)
      if (successful) {
        tokenizer.setScopeValue(initialIndex, tokenizer.index)
        savePoint = tokenizer.save()
      }
      return@addScope successful
    }
    if (success) {
      tokenizer.transform(
          initialIndex,
          tokenizer.index,
          savePoint!!,
          transformFunction
      )
    }
    return success
  }
}

internal class XOrMoreGrammar<T>(
    private val grammar: Grammar<T>,
    private val lowerLimit: Int
) : Grammar<T>() {
  override fun copy(): Grammar<T> = XOrMoreGrammar(grammar, lowerLimit)

  override fun process(input: String, tokenizer: Tokenizer): Boolean {
    var count = 0
    do {
      val matched = grammar.process(input, tokenizer)
      if (matched) {
        count += 1
      }
    } while (matched)
    return count >= lowerLimit
  }
}

internal class RangeGrammar<T>(
    private val grammar: Grammar<T>,
    private val range: IntRange
) : Grammar<T>() {
  override fun copy(): Grammar<T> = RangeGrammar(grammar, range)

  override fun process(input: String, tokenizer: Tokenizer): Boolean {
    var count = 0
    do {
      val matched = grammar.process(input, tokenizer)
      if (matched) {
        count += 1
      }
      if (count < range.last) {
        return false
      }
    } while (matched)
    return count >= range.first
  }
}
