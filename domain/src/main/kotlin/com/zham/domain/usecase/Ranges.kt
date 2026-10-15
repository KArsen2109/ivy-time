package com.zham.domain.usecase

import java.time.Duration
import java.time.Instant

/** Sorted, non-overlapping ranges covering exactly what the input covers. */
internal fun merge(ranges: List<Pair<Instant, Instant>>): List<Pair<Instant, Instant>> {
    if (ranges.isEmpty()) return emptyList()
    val sorted = ranges.sortedBy { it.first }
    val merged = mutableListOf(sorted.first())
    sorted.drop(1).forEach { (start, end) ->
        val (lastStart, lastEnd) = merged.last()
        if (start > lastEnd) {
            merged += start to end
        } else if (end > lastEnd) {
            merged[merged.lastIndex] = lastStart to end
        }
    }
    return merged
}

/** Total length of the ranges, counting overlapping stretches once. */
internal fun unionLength(ranges: List<Pair<Instant, Instant>>): Duration =
    merge(ranges).fold(Duration.ZERO) { acc, (start, end) -> acc + Duration.between(start, end) }
