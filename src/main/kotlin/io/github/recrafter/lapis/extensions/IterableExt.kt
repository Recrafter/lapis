package io.github.recrafter.lapis.extensions

fun <T> Iterable<T>.indexOfFirstOrNull(predicate: (T) -> Boolean): Int? =
    indexOfFirst(predicate).takeIf { it >= 0 }

fun <T> Iterable<T>.indexOfLastOrNull(predicate: (T) -> Boolean): Int? =
    indexOfLast(predicate).takeIf { it >= 0 }

fun String.lastIndexOfOrNull(char: Char, startIndex: Int = lastIndex, ignoreCase: Boolean = false): Int? =
    lastIndexOf(char, startIndex, ignoreCase).takeIf { it >= 0 }
