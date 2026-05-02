package io.github.recrafter.lapis.phases.common

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import io.github.recrafter.lapis.phases.validator.*

class JvmDescriptor(private val type: JPTypeName) {

    fun getPrimitiveName(allowVoid: Boolean = true): String? =
        when (type) {
            JPBoolean -> "Z"
            JPByte -> "B"
            JPShort -> "S"
            JPInt -> "I"
            JPLong -> "J"
            JPChar -> "C"
            JPFloat -> "F"
            JPDouble -> "D"
            JPVoid -> if (allowVoid) VOID_NAME else null
            else -> null
        }

    override fun toString(): String =
        when (type) {
            is JPClassName -> type.objectName
            is JPParameterizedTypeName -> type.rawType().objectName
            is JPArrayTypeName -> "[" + type.componentType().jvmDescriptor
            else -> getPrimitiveName() ?: lapisError("Unsupported Java type")
        }

    private val JPClassName.objectName: String
        get() = JvmClassName.of(binaryName).descriptor

    companion object {
        fun buildSignature(parameterTypeNames: List<IrTypeName>, returnTypeName: IrTypeName?): String =
            buildString {
                append(
                    parameterTypeNames.joinToString(
                        prefix = "(",
                        separator = "",
                        postfix = ")",
                    ) { it.jvmDescriptor.toString() }
                )
                append(returnTypeName?.jvmDescriptor ?: VOID_NAME)
            }
    }
}

val IrTypeName.jvmDescriptor: JvmDescriptor
    get() = java.jvmDescriptor

fun Descriptor.getMixinReference(isTarget: Boolean = false): String =
    when (this) {
        is ConstructorDescriptor -> buildString {
            if (!isTarget) {
                append(CONSTRUCTOR_NAME)
            }
            append(
                JvmDescriptor.buildSignature(
                    parameters.map { it.typeName },
                    if (isTarget) returnTypeName else null
                )
            )
        }

        is MethodDescriptor -> buildString {
            if (isTarget) {
                append(inaccessibleReceiverJvmClassName?.descriptor ?: receiverTypeName.jvmDescriptor)
            }
            append(mappingName)
            append(
                JvmDescriptor.buildSignature(
                    parameters.map { it.typeName },
                    returnTypeName
                )
            )
        }

        is FieldDescriptor -> buildString {
            if (isTarget) {
                append(inaccessibleReceiverJvmClassName?.descriptor ?: receiverTypeName.jvmDescriptor)
            }
            append(mappingName)
            append(":")
            append(fieldTypeName.jvmDescriptor)
        }
    }

val InvokableDescriptor.binaryName: String
    get() = when (this) {
        is ConstructorDescriptor -> CONSTRUCTOR_NAME
        is MethodDescriptor -> mappingName
    }

private const val CONSTRUCTOR_NAME: String = "<init>"
private const val VOID_NAME: String = "V"

private val JPTypeName.jvmDescriptor: JvmDescriptor
    get() = JvmDescriptor(this)
