package io.github.recrafter.lapis.extensions

fun String.capitalizeWithPrefix(prefix: String): String =
    prefix + replaceFirstChar { it.titlecaseChar() }

fun String.singleQuoted(): String =
    "'$this'"
