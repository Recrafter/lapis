package io.github.recrafter.lapis.common

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.ks.findAnnotation
import io.github.recrafter.lapis.extensions.ks.getArgumentValue
import io.github.recrafter.lapis.extensions.ks.hasAnnotation

@JvmInline
value class KSFunctionType private constructor(private val type: KSType) {

    val isExtension: Boolean
        get() = type.hasAnnotation<ExtensionFunctionType>()

    fun getReceiverTypeOrNull(): KSType? =
        if (isExtension) type.arguments.firstOrNull()?.type?.resolve()
        else null

    fun getParameters(): List<KSFunctionTypeParameter> =
        type.arguments.drop(if (isExtension) 1 else 0).dropLast(1).map {
            val name = it.findAnnotation<ParameterName>()?.getArgumentValue(ParameterName::name)
            KSFunctionTypeParameter(it, name)
        }

    fun getReturnType(): KSType =
        type.arguments.lastOrNull()?.type?.resolve()
            ?: lapisError("Functional type must have a return value")

    companion object {
        fun of(type: KSType): KSFunctionType? =
            if (type.isFunctionType) KSFunctionType(type)
            else null
    }
}

class KSFunctionTypeParameter(
    val argument: KSTypeArgument,
    val name: String?,
)

fun KSType.toFunctionTypeOrNull(): KSFunctionType? =
    KSFunctionType.of(this)
