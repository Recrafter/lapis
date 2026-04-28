package io.github.recrafter.lapis.phases.lowering.types

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.phases.lowering.models.IrConstructorDescriptor
import io.github.recrafter.lapis.phases.lowering.models.IrInvokableDescriptor
import io.github.recrafter.lapis.phases.lowering.models.IrMethodDescriptor
import io.github.recrafter.lapis.phases.validator.ConstructorDescriptor
import io.github.recrafter.lapis.phases.validator.Descriptor
import io.github.recrafter.lapis.phases.validator.FieldDescriptor
import io.github.recrafter.lapis.phases.validator.MethodDescriptor

class IrJvmType(private val type: JPTypeName) {

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

    fun getName(): String =
        when (type) {
            is JPClassName -> type.objectName
            is JPParameterizedTypeName -> type.rawType().objectName
            is JPArrayTypeName -> "[" + type.componentType().jvmName
            else -> getPrimitiveName() ?: lapisError("Unsupported Java type")
        }

    private val JPClassName.objectName: String
        get() = "L$internalName;"

    companion object {
        fun buildSignature(parameterTypeNames: List<IrTypeName>, returnTypeName: IrTypeName?): String =
            buildString {
                append(
                    parameterTypeNames.joinToString(
                        prefix = "(",
                        separator = "",
                        postfix = ")",
                    ) { it.jvmName }
                )
                append(returnTypeName?.jvmName ?: VOID_NAME)
            }
    }
}

val IrTypeName.jvmName: String
    get() = java.jvmName

fun Descriptor.getMixinRef(isTarget: Boolean = false): String =
    when (this) {
        is ConstructorDescriptor -> buildString {
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

        is MethodDescriptor -> buildString {
            if (isTarget) {
                append(receiverTypeName.jvmName)
            }
            append(bytecodeName)
            append(
                IrJvmType.buildSignature(
                    parameters.map { it.typeName },
                    returnTypeName
                )
            )
        }

        is FieldDescriptor -> buildString {
            if (isTarget) {
                append(receiverTypeName.jvmName)
            }
            append(bytecodeName)
            append(":")
            append(fieldTypeName.jvmName)
        }
    }

val IrInvokableDescriptor.binaryName: String
    get() = when (this) {
        is IrConstructorDescriptor -> CONSTRUCTOR_NAME
        is IrMethodDescriptor -> bytecodeName
    }

private const val CONSTRUCTOR_NAME: String = "<init>"
private const val VOID_NAME: String = "V"

private val JPTypeName.jvmName: String
    get() = IrJvmType(this).getName()
