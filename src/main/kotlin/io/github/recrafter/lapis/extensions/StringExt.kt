package io.github.recrafter.lapis.extensions

import io.github.recrafter.lapis.LapisMeta

fun String.capitalize(): String =
    replaceFirstChar {
        if (it.isLowerCase()) it.titlecase()
        else it.toString()
    }

fun String.quoted(): String =
    "'$this'"

fun String.withInternalPrefix(prefix: String): String =
    "_${prefix}_$this"

fun String.withInternalPrefix(prefix: InternalPrefix = InternalPrefix.BUILTIN): String =
    withInternalPrefix(prefix.value)

enum class InternalPrefix(val value: String) {
    BUILTIN(LapisMeta.NAME.lowercase()),
    PARAM("param"),
    LOCAL("local"),
    ARGUMENT("argument"),
}
