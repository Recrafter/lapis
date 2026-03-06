package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.extensions.singleQuoted

val JPTypeName.defaultValue: String
    get() = when (this) {
        JPBoolean -> false.toString()
        JPByte, JPShort, JPInt -> 0.toString()
        JPLong -> "0L"
        JPChar -> """\0""".singleQuoted()
        JPFloat -> "0f"
        JPDouble -> "0d"
        else -> null.toString()
    }

fun JPTypeName?.orVoid(): JPTypeName =
    this ?: JPVoid
