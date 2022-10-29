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
          value = null,
          children = WeakHashMap(),
          groupContexts = mutableMapOf(),
          refContexts = WeakHashMap()
      )

  fun addScope(
      grammar: CaptureGrammar<*, *>,
      function: () -> Boolean
  ): Boolean {
    val oldScope = currentScope
    var newScope =
        TokenizerScope(
            grammar = grammar,
            value = null,
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

  fun <T> addScope(grammar: TransformGrammar, function: () -> T): T {
    TODO()
  }

  // TODO CURRENT: I think forLatestScope is the only one needed.
  fun <T> forSingleScope(grammar: Grammar<*>, function: () -> T): T {
    val scopes = currentScope.children[grammar]
        ?: throw ProcessingException("Scopes for grammar not found")
    if (scopes.isEmpty()) {
      throw ProcessingException("Scope for grammar not found")
    }
    return forEachProvidedScope(scopes.subList(0, 1), function)[0]
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
      context: MutableCaptureContext
  ) {
    val contextMapping = currentScope.groupContexts.computeIfAbsent(group) {
      GroupRefContextMapping(grammar)
    }
    contextMapping.contexts.add(context)
  }

  fun addContext(grammar: Grammar<*>, context: MutableCaptureContext) {
    currentScope.refContexts.computeIfAbsent(grammar) { mutableListOf() }
        .add(context)
  }

  fun getContext(group: String): MutableCaptureContext? =
      getAllContexts(group).firstOrNull()

  fun getAllContexts(group: String): List<MutableCaptureContext> {
    val value = (currentScope.groupContexts[group]
        ?: throw ProcessingException("Context for group $group not found"))
    if (value.contexts.isEmpty()) {
      // This should never happen, the only way a grammar has a reference in
      // the map is if map entry for that grammar was populated.
      throw ProcessingException("Context for group $group is empty")
    }
    return value.contexts
  }

  fun getContext(grammar: Grammar<*>): MutableCaptureContext? =
      getAllContexts(grammar).firstOrNull()

  fun getAllContexts(grammar: Grammar<*>): List<MutableCaptureContext> {
    val value = currentScope.refContexts[grammar]
    if (value.isNullOrEmpty()) {
      return listOf()
    }
    return value
  }

  fun save(): TokenizerSavePoint = TokenizerSavePointImpl(
      index = index,
      scope = TokenizerScope(
          grammar = currentScope.grammar,
          value = currentScope.value,
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
              .associateTo(WeakHashMap()) { it.key to it.value.toMutableList() }
      )
  )

  fun restore(savePoint: TokenizerSavePoint) {
    val impl = savePoint as TokenizerSavePointImpl
    currentScope = impl.scope
    index = impl.index
  }

  fun setScopeValue(str: String) {
    currentScope.value = str
  }

  fun getScopeValue(): String? = currentScope.value
}

// This should only be implemented by TokenizerSavePointImpl. It serves as a
// way to ensure the user can only restore from this class, but otherwise
// cannot read any of its properties.
internal interface TokenizerSavePoint

private class TokenizerScope(
    val grammar: Grammar<*>?,
    var value: String?,
    val children: WeakHashMap<CaptureGrammar<*, *>, MutableList<TokenizerScope>>,
    val groupContexts: MutableMap<String, GroupRefContextMapping>,
    val refContexts: WeakHashMap<Grammar<*>, MutableList<MutableCaptureContext>>
)

private class TokenizerSavePointImpl(
    val index: Int,
    val scope: TokenizerScope
) : TokenizerSavePoint

private class GroupRefContextMapping(
    val grammar: Grammar<*>,
    val contexts: MutableList<MutableCaptureContext> = mutableListOf()
) {
  fun copy() = GroupRefContextMapping(
      grammar = grammar,
      contexts = contexts.toMutableList()
  )
}
