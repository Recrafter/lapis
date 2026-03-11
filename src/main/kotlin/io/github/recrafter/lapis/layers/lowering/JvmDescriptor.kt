package io.github.recrafter.lapis.layers.lowering

import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.layers.lowering.types.IrType
import io.github.recrafter.lapis.layers.validator.*

class JvmDescriptor(private val type: JPType) {

    val typeDescriptor: String
        get() = when (type) {
            is JPClassType -> type.typeDescriptor
            is JPGenericType -> type.rawType().typeDescriptor
            is JPArrayType -> type.typeDescriptor
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

    private val JPClassType.binaryName: String
        get() = buildString {
            if (packageName().isNotEmpty()) {
                append(packageName().replace('.', '/')).append('/')
            }
            append(simpleNames().joinToString("$"))
        }

    private val JPClassType.typeDescriptor: String
        get() = "L$binaryName;"

    private val JPArrayType.typeDescriptor: String
        get() = "[" + componentType().asJvmDescriptor().typeDescriptor

    companion object {
        fun of(type: IrType): String =
            type.java.asJvmDescriptor().typeDescriptor

        fun buildMethodSignature(parameterTypes: List<IrType>, returnType: IrType?): String = buildString {
            append("(")
            parameterTypes.forEach { append(of(it)) }
            append(")")
            append(returnType?.let { of(it) } ?: VOID_NAME)
        }
    }
}

private const val CONSTRUCTOR_NAME: String = "<init>"
private const val VOID_NAME: String = "V"

fun Descriptor.getMemberReference(withReceiver: Boolean = false): String =
    when (val descriptor = this) {
        is InvokableDescriptor -> buildString {
            if (withReceiver) {
                append(JvmDescriptor.of(irReceiverType))
            }
            append(binaryName)
            append(
                JvmDescriptor.buildMethodSignature(
                    parameters.map { it.irType },
                    if (descriptor is ConstructorDescriptor) null else irReturnType
                )
            )
        }

        is FieldDescriptor -> buildString {
            if (withReceiver) {
                append(JvmDescriptor.of(irReceiverType))
            }
            append(targetName)
            append(":")
            append(JvmDescriptor.of(irType))
        }
    }

fun JPType.asJvmDescriptor(): JvmDescriptor =
    JvmDescriptor(this)

val InvokableDescriptor.binaryName: String
    get() = when (this) {
        is ConstructorDescriptor -> CONSTRUCTOR_NAME
        is MethodDescriptor -> targetName
    }

val IrInvokableDescriptor.binaryName: String
    get() = when (this) {
        is IrConstructorDescriptor -> CONSTRUCTOR_NAME
        is IrMethodDescriptor -> targetName
    }
