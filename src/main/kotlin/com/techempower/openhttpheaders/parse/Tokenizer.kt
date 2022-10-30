package com.techempower.openhttpheaders.parse

import com.techempower.openhttpheaders.ProcessingException
import java.util.WeakHashMap

// Partially inspired by Guava: https://github.com/google/guava/blob/master/guava/src/com/google/common/net/MediaType.java#L1089
internal class Tokenizer(
    private val input: String,
    index: Int
) {
  var index: Int = index
    private set

  private var currentScope: TokenizerScope =
      TokenizerScope(
          grammar = null,
          valueRange = null,
          transforms = mutableListOf(),
          children = WeakHashMap(),
          groupContexts = mutableMapOf(),
          refContexts = WeakHashMap()
      )

  fun addScope(
      grammar: CaptureGrammar<*, *>,
      function: () -> Boolean
  ): Boolean = addGrammarScope(grammar, function)

  fun addScope(grammar: TransformGrammar, function: () -> Boolean): Boolean =
      addGrammarScope(grammar, function)

  private fun addGrammarScope(
      grammar: Grammar<*>,
      function: () -> Boolean
  ): Boolean {
    val oldScope = currentScope
    var newScope =
        TokenizerScope(
            grammar = grammar,
            valueRange = null,
            transforms = mutableListOf(),
            children = WeakHashMap(),
            groupContexts = mutableMapOf(),
            refContexts = WeakHashMap()
        )
    currentScope = newScope
    val successful = function.invoke()
    // Note that the instance of the new scope may have changed due to
    // save/restore, so the reference must be re-obtained.
    newScope = currentScope
    currentScope = oldScope
    if (successful) {
      val scopes =
          currentScope.children.computeIfAbsent(grammar) { mutableListOf() }
      scopes.add(newScope)
    }
    return successful
  }

  fun <T> forLatestScope(grammar: Grammar<*>, function: () -> T): T {
    val scopes = currentScope.children[grammar]
        ?: throw ProcessingException("Scopes for grammar not found")
    if (scopes.isEmpty()) {
      throw ProcessingException("Scope for grammar not found")
    }
    return forEachProvidedScope(
        scopes.subList(scopes.size - 1, scopes.size),
        function
    ).last()
  }

  private fun <T> forEachProvidedScope(
      scopes: List<TokenizerScope>,
      function: () -> T
  ): List<T> {
    val oldScope = currentScope
    val result = scopes.map {
      currentScope = it
      function.invoke()
    }
    currentScope = oldScope
    return result
  }

  fun hasNext(): Boolean = input.length > index

  fun peek(): Char = input[index]

  fun advance() {
    index += 1
  }

  fun addContext(
      group: String,
      grammar: Grammar<*>,
      range: IntRange, savePoint: TokenizerSavePoint?
  ) {
    val contextMapping = currentScope.groupContexts.computeIfAbsent(group) {
      GroupRefContextMapping(grammar)
    }
    contextMapping.contexts.add(
        GroupValue(
            originalRange = range,
            savePoint = savePoint,
        )
    )
  }

  fun addContext(
      grammar: Grammar<*>,
      range: IntRange,
      savePoint: TokenizerSavePoint?
  ) {
    val contextMapping = currentScope.refContexts.computeIfAbsent(grammar) {
      GroupRefContextMapping(grammar)
    }
    contextMapping.contexts.add(
        GroupValue(
            originalRange = range,
            savePoint = savePoint,
        )
    )
  }

  fun getContext(group: String): GroupValue? =
      getAllContexts(group).firstOrNull()

  fun getAllContexts(group: String): List<GroupValue> {
    val value = currentScope.groupContexts[group]
    if (value == null || value.contexts.isEmpty()) {
      return listOf()
    }
    return value.contexts
  }

  fun getContext(grammar: Grammar<*>): GroupValue? =
      getAllContexts(grammar).firstOrNull()

  fun getAllContexts(grammar: Grammar<*>): List<GroupValue> {
    val value = currentScope.refContexts[grammar]
    if (value == null || value.contexts.isEmpty()) {
      return listOf()
    }
    return value.contexts
  }

  fun save(): TokenizerSavePoint = TokenizerSavePointImpl(
      index = index,
      scope = TokenizerScope(
          grammar = currentScope.grammar,
          valueRange = currentScope.valueRange,
          transforms = currentScope.transforms.toMutableList(),
          // Copies only need to be one-map deep. Scopes are never re-entered after
          // they have been created.
          children = currentScope.children
              .entries
              .associateTo(WeakHashMap()) { it.key to it.value.toMutableList() },
          groupContexts = currentScope.groupContexts
              .entries
              .associateTo(mutableMapOf()) { it.key to it.value.copy() },
          refContexts = currentScope.refContexts
              .entries
              .associateTo(WeakHashMap()) { it.key to it.value.copy() }
      )
  )

  fun restore(savePoint: TokenizerSavePoint) {
    val impl = savePoint as TokenizerSavePointImpl
    currentScope = impl.scope
    index = impl.index
  }

  fun setScopeValue(startInclusive: Int, endExclusive: Int) {
    currentScope.valueRange = startInclusive until endExclusive
  }

  fun transform(
      startInclusive: Int,
      endExclusive: Int,
      savePoint: TokenizerSavePoint,
      transformFunction: (SingleCaptureContext<String>) -> String
  ) {
    currentScope.transforms.add(
        Transform(
            startInclusive = startInclusive,
            endExclusive = endExclusive,
            savePoint = savePoint,
            transformFunction = transformFunction

        )
    )
  }

  fun getScopeValue(parseParameters: Map<String, Any>): String? {
    if (currentScope.valueRange == null) {
      return null
    }
    // TODO CURRENT: Optimize later
    val stringBuilder = StringBuilder()
    val originalIndex = currentScope.valueRange!!.first
    var index = originalIndex
    val transformResults = mutableListOf<TransformResult>()
    currentScope.transforms.forEach {
      if (index != it.startInclusive) {
        stringBuilder.append(input.substring(index, it.startInclusive))
      }
      val original = save()
      restore(it.savePoint)
      val toTransform = getScopeValue(parseParameters)!!
      // TODO CURRENT: The save/restore for cases like this is wasteful.
      //  Better to just add a function like view(savePoint) {} which doesn't
      //  have the overhead of copying the old one, since it's not about safe
      //  modifications to the original.

      val transformed = it.transformFunction.invoke(
          SingleCaptureContext(
              toTransform,
              parseParameters,
              this
          )
      )
      transformResults.add(
          TransformResult(
              it.startInclusive until it.endExclusive,
              transformed.length
          )
      )
      stringBuilder.append(transformed)
      restore(original)
      index = it.endExclusive
    }
    if (index != currentScope.valueRange!!.last + 1) {
      stringBuilder.append(
          input.substring(
              index,
              currentScope.valueRange!!.last + 1
          )
      )
    }
    val value = stringBuilder.toString()
    // Go through all groups and refs and update their start/end indexes to
    // reflect the transforms.
    (currentScope.groupContexts.values + currentScope.refContexts.values)
        .forEach {
          it.contexts.forEach { groupValue ->
            var startInclusive: Int =
                groupValue.originalRange.first - originalIndex
            var endExclusive: Int =
                groupValue.originalRange.last + 1 - originalIndex

            transformResults.forEach { transformResult ->
              if (transformResult.transformRange.last < groupValue.originalRange.first) {
                startInclusive += transformResult.offset
                endExclusive += transformResult.offset
              } else if (transformResult.transformRange.last <= groupValue.originalRange.last) {
                endExclusive += transformResult.offset
              }
            }

            groupValue.effectiveRange = startInclusive until endExclusive
            groupValue.text = value.substring(groupValue.effectiveRange!!)
          }
        }
    return value
  }
}

// This should only be implemented by TokenizerSavePointImpl. It serves as a
// way to ensure the user can only restore from this class, but otherwise
// cannot read any of its properties.
internal interface TokenizerSavePoint

private class TokenizerScope(
    val grammar: Grammar<*>?,
    var valueRange: IntRange?,
    val transforms: MutableList<Transform>,
    val children: WeakHashMap<Grammar<*>, MutableList<TokenizerScope>>,
    val groupContexts: MutableMap<String, GroupRefContextMapping>,
    val refContexts: WeakHashMap<Grammar<*>, GroupRefContextMapping>
)

private class TokenizerSavePointImpl(
    val index: Int,
    val scope: TokenizerScope
) : TokenizerSavePoint

private class GroupRefContextMapping(
    val grammar: Grammar<*>,
    val contexts: MutableList<GroupValue> = mutableListOf()
) {
  fun copy() = GroupRefContextMapping(
      grammar = grammar,
      contexts = contexts.toMutableList()
  )
}

private class Transform(
    val startInclusive: Int,
    val endExclusive: Int,
    val savePoint: TokenizerSavePoint,
    val transformFunction: (SingleCaptureContext<String>) -> String
)

private class TransformResult(
    val transformRange: IntRange,
    newLength: Int
) {
  val offset = newLength - ((transformRange.last + 1) - transformRange.first)
}

internal class GroupValue(
    val originalRange: IntRange,
    var effectiveRange: IntRange? = null,
    var text: String? = null,
    var value: Any? = null,
    var savePoint: TokenizerSavePoint?
)
