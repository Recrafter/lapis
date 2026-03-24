package io.github.recrafter.lapis.layers.lowering

import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName
import io.github.recrafter.lapis.layers.validator.ConstructorDesc
import io.github.recrafter.lapis.layers.validator.Desc
import io.github.recrafter.lapis.layers.validator.FieldDesc
import io.github.recrafter.lapis.layers.validator.MethodDesc

class JvmDesc(private val type: JPType) {

    override fun toString(): String =
        when (type) {
            is JPClassType -> type.typeDesc
            is JPGenericType -> type.rawType().typeDesc
            is JPArrayType -> "[" + type.componentType().asJvmDesc().toString()
            JPBoolean -> "Z"
            JPByte -> "B"
            JPShort -> "S"
            JPInt -> "I"
            JPLong -> "J"
            JPChar -> "C"
            JPFloat -> "F"
            JPDouble -> "D"
            else -> VOID_NAME
        }

    private val JPClassType.typeDesc: String
        get() = "L$internalName;"

    companion object {
        fun of(typeName: IrTypeName): String =
            typeName.java.asJvmDesc().toString()

        fun buildSignature(parameterTypeNames: List<IrTypeName>, returnTypeName: IrTypeName?): String = buildString {
            append("(")
            parameterTypeNames.forEach { append(of(it)) }
            append(")")
            append(returnTypeName?.let { of(it) } ?: VOID_NAME)
        }
    }
}

fun JPType.asJvmDesc(): JvmDesc =
    JvmDesc(this)

private const val CONSTRUCTOR_NAME: String = "<init>"
private const val VOID_NAME: String = "V"

fun Desc.getMixinRef(isTarget: Boolean = false): String =
    when (this) {
        is ConstructorDesc -> buildString {
            if (!isTarget) {
                append(JvmDesc.of(receiverTypeName))
                append(CONSTRUCTOR_NAME)
            }
            append(
                JvmDesc.buildSignature(
                    parameters.map { it.typeName },
                    if (isTarget) returnTypeName else null
                )
            )
        }

        is MethodDesc -> buildString {
            if (isTarget) {
                append(JvmDesc.of(receiverTypeName))
            }
            append(targetName)
            append(
                JvmDesc.buildSignature(
                    parameters.map { it.typeName },
                    returnTypeName
                )
            )
        }

        is FieldDesc -> buildString {
            if (isTarget) {
                append(JvmDesc.of(receiverTypeName))
            }
            append(targetName)
            append(":")
            append(JvmDesc.of(fieldTypeName))
        }
    }

val IrInvokableDesc.binaryName: String
    get() = when (this) {
        is IrConstructorDesc -> CONSTRUCTOR_NAME
        is IrMethodDesc -> targetName
    }
