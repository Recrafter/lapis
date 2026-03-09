package io.github.recrafter.lapis.layers.lowering

import io.github.recrafter.lapis.extensions.jp.*
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
}

private const val CONSTRUCTOR_NAME: String = "<init>"
private const val VOID_NAME: String = "V"

fun Descriptor.getMemberReference(withReceiver: Boolean = false): String =
    when (val descriptor = this) {
        is InvokableDescriptor -> buildString {
            if (withReceiver) {
                append(irReceiverType.java.asJvmDescriptor().typeDescriptor)
            }
            append(binaryName)
            append("(")
            parameters.forEach {
                append(it.irType.java.asJvmDescriptor().typeDescriptor)
            }
            append(")")
            if (descriptor is ConstructorDescriptor) {
                append(VOID_NAME)
            } else {
                append(irReturnType?.java?.asJvmDescriptor()?.typeDescriptor ?: VOID_NAME)
            }
        }

        is FieldDescriptor -> buildString {
            if (withReceiver) {
                append(irReceiverType.java.asJvmDescriptor().typeDescriptor)
            }
            append(targetName)
            append(":")
            append(irType.java.asJvmDescriptor().typeDescriptor)
        }
    }

fun JPType.asJvmDescriptor(): JvmDescriptor =
    JvmDescriptor(this)

val InvokableDescriptor.binaryName: String
    get() = when (this) {
        is ConstructorDescriptor -> CONSTRUCTOR_NAME
        is MethodDescriptor -> targetName
    }

val IrAccessorKind.binaryName: String
    get() = when (this) {
        is IrConstructorAccessor -> CONSTRUCTOR_NAME
        else -> targetName
    }
