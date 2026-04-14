package io.github.recrafter.lapis.extensions.jp

val JPTypeName.primitiveDefaultValue: String?
    get() = when (this) {
        JPBoolean -> "false"
        JPByte, JPShort, JPInt -> "0"
        JPLong -> "0L"
        JPChar -> "'\\0'"
        JPFloat -> "0f"
        JPDouble -> "0d"
        else -> null
    }

fun JPTypeName?.orVoid(): JPTypeName =
    this ?: JPVoid
