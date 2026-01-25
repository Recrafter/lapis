package io.github.recrafter.lapis.utils

import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.KPUnit
import io.github.recrafter.lapis.extensions.kp.asKJTypeName
import io.github.recrafter.lapis.kj.KJTypeName

object Descriptors {

    const val CONSTRUCTOR_METHOD_NAME: String = "<init>"

    fun forMethod(
        name: String,
        receiverType: KJTypeName?,
        argumentTypes: List<KJTypeName>,
        returnType: KJTypeName,
    ): String =
        buildString {
            receiverType?.let { append(it.descriptor) }
            append(name)
            append("(")
            append(argumentTypes.joinToString("") { it.descriptor })
            append(")")
            append(returnType.descriptor)
        }

    fun forConstructor(argumentTypes: List<KJTypeName>): String =
        forMethod(CONSTRUCTOR_METHOD_NAME, null, argumentTypes, KPUnit.asKJTypeName())
}

private val KJTypeName.descriptor: String
    get() = when (javaVersion) {
        JPVoid -> "V"
        JPBoolean -> "Z"
        JPByte -> "B"
        JPShort -> "S"
        JPInt -> "I"
        JPLong -> "L"
        JPChar -> "C"
        JPFloat -> "F"
        JPDouble -> "D"
        else -> {
            ((javaVersion as? JPParameterizedTypeName)?.rawType() ?: className?.javaVersion)
                ?.let { "L" + it.canonicalName().replace(".", "/") + ";" }
                ?: error("Unsupported type: $javaVersion")
        }
    }
