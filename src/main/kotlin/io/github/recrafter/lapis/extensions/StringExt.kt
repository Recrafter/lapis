package io.github.recrafter.lapis.extensions

fun String.capitalize(): String =
    replaceFirstChar {
        if (it.isLowerCase()) it.titlecase()
        else it.toString()
    }

fun String.singleQuoted(): String =
    "'$this'"

fun String.backticked(): String =
    "`$this`"
