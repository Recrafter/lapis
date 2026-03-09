package io.github.recrafter.lapis.extensions.jp

val JPType.defaultValue: String
    get() = when (this) {
        JPBoolean -> "false"
        JPByte, JPShort, JPInt -> "0"
        JPLong -> "0L"
        JPChar -> "'\\0'"
        JPFloat -> "0f"
        JPDouble -> "0d"
        else -> "null"
    }

fun JPType?.orVoid(): JPType =
    this ?: JPVoid
