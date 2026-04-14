package io.github.recrafter.lapis.layers.lowering.types

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.layers.lowering.models.IrConstructorDesc
import io.github.recrafter.lapis.layers.lowering.models.IrInvokableDesc
import io.github.recrafter.lapis.layers.lowering.models.IrMethodDesc
import io.github.recrafter.lapis.layers.validator.ConstructorDesc
import io.github.recrafter.lapis.layers.validator.Desc
import io.github.recrafter.lapis.layers.validator.FieldDesc
import io.github.recrafter.lapis.layers.validator.MethodDesc

class IrJvmType(private val type: JPTypeName) {

    override fun toString(): String =
        when (type) {
            is JPClassName -> type.typeDesc
            is JPParameterizedTypeName -> type.rawType().typeDesc
            is JPArrayTypeName -> "[" + type.componentType().jvmType.toString()
            JPBoolean -> "Z"
            JPByte -> "B"
            JPShort -> "S"
            JPInt -> "I"
            JPLong -> "J"
            JPChar -> "C"
            JPFloat -> "F"
            JPDouble -> "D"
            JPVoid -> VOID_NAME
            else -> lapisError("Unsupported Java type")
        }

    private val JPClassName.typeDesc: String
        get() = "L$internalName;"

    companion object {
        fun buildDesc(typeName: IrTypeName): String =
            typeName.java.jvmType.toString()

        fun buildSignature(parameterTypeNames: List<IrTypeName>, returnTypeName: IrTypeName?): String = buildString {
            append("(")
            parameterTypeNames.forEach { append(buildDesc(it)) }
            append(")")
            append(returnTypeName?.let { buildDesc(it) } ?: VOID_NAME)
        }
    }
}

val JPTypeName.jvmType: IrJvmType
    get() = IrJvmType(this)

private const val CONSTRUCTOR_NAME: String = "<init>"
private const val VOID_NAME: String = "V"

fun Desc.getMixinRef(isTarget: Boolean = false): String =
    when (this) {
        is ConstructorDesc -> buildString {
            if (!isTarget) {
                append(CONSTRUCTOR_NAME)
            }
            append(
                IrJvmType.buildSignature(
                    parameters.map { it.typeName },
                    if (isTarget) returnTypeName else null
                )
            )
        }

        is MethodDesc -> buildString {
            if (isTarget) {
                append(IrJvmType.buildDesc(receiverTypeName))
            }
            append(targetName)
            append(
                IrJvmType.buildSignature(
                    parameters.map { it.typeName },
                    returnTypeName
                )
            )
        }

        is FieldDesc -> buildString {
            if (isTarget) {
                append(IrJvmType.buildDesc(receiverTypeName))
            }
            append(targetName)
            append(":")
            append(IrJvmType.buildDesc(fieldTypeName))
        }
    }

val IrInvokableDesc.binaryName: String
    get() = when (this) {
        is IrConstructorDesc -> CONSTRUCTOR_NAME
        is IrMethodDesc -> targetName
    }
