package io.github.recrafter.lapis.extensions

import io.github.recrafter.lapis.phases.generator.models.GenInternalPrefix

fun String.capitalize(): String =
    replaceFirstChar {
        if (it.isLowerCase()) it.titlecase()
        else it.toString()
    }

fun String.quoted(): String =
    "'$this'"

fun String.withInternalPrefix(prefix: String): String =
    "_${prefix}_$this"

fun String.withInternalPrefix(prefix: GenInternalPrefix = GenInternalPrefix.BUILTIN): String =
    withInternalPrefix(prefix.value)
