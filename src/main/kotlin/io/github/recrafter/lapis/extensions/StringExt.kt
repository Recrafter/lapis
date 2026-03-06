package io.github.recrafter.lapis.extensions

fun String.capitalizeWithPrefix(prefix: String): String =
    prefix + replaceFirstChar { it.titlecaseChar() }

fun String.singleQuoted(): String =
    "'$this'"

fun String.backticked(): String =
    "`$this`"

fun String.withJavaInternalPrefix(prefix: String = "lapis"): String =
    "$prefix$$this"

fun String.withKotlinInternalPrefix(prefix: String = "lapis"): String =
    "_${prefix}_$this"
