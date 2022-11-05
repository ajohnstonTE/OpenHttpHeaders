package com.techempower.openhttpheaders.parse

// Partially inspired by Guava: https://github.com/google/guava/blob/master/guava/src/com/google/common/net/MediaType.java#L1089
internal class Tokenizer(
    val input: String
) {
  val state: TokenizerState = TokenizerStateImpl()

  fun hasNext(): Boolean = input.length > state.getInputIndex()

  fun peek(): Char = input[state.getInputIndex()]

  fun advance() {
    state.setInputIndex(state.getInputIndex() + 1)
  }

  fun save(): TokenizerSavePoint = state.save()

  fun restore(savePoint: TokenizerSavePoint) {
    state.restore(savePoint)
  }

  fun getScopeValue(
      nodeIndex: Int,
      scope: ProcessScope
  ): String {
    // TODO CURRENT: Optimize later
    val stringBuilder = StringBuilder()
    val originalStartInclusive = state.getRangeStartInclusive(nodeIndex)
    val originalEndExclusive = state.getRangeEndExclusive(nodeIndex)
    var index = originalStartInclusive
    val transformResults = mutableListOf<TransformResult>()
    scope.transforms.forEach {
      if (index != it.startInclusive) {
        stringBuilder.append(input.substring(index, it.startInclusive))
      }

      // TODO CURRENT: There's no need for TransformResult anymore, just use
      //  Transform, with new properties
      val transformed = it.value
      transformResults.add(
          TransformResult(
              it.startInclusive until it.endExclusive,
              transformed.length
          )
      )
      stringBuilder.append(transformed)
      index = it.endExclusive
    }
    if (index != originalEndExclusive) {
      stringBuilder.append(input.substring(index, originalEndExclusive))
    }
    val value = stringBuilder.toString()
    // Go through all groups and refs and update their start/end indexes to
    // reflect the transforms.
    (scope.indexesToValues.values)
        .forEach {
          groupValue ->
            var startInclusive: Int =
                groupValue.originalRange.first - originalStartInclusive
            var endExclusive: Int =
                groupValue.originalRange.last + 1 - originalStartInclusive

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
    return value
  }
}

internal class Transform(
    val startInclusive: Int,
    val endExclusive: Int,
    val value: String
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
)
