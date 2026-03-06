package io.github.recrafter.lapis.layers.lowering

import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.layers.validator.ConstructorDescriptor
import io.github.recrafter.lapis.layers.validator.Descriptor
import io.github.recrafter.lapis.layers.validator.FieldDescriptor
import io.github.recrafter.lapis.layers.validator.MethodDescriptor

class JvmDescriptor(private val type: JPTypeName) {

    val typeDescriptor: String
        get() = when (type) {
            is JPClassName -> type.typeDescriptor
            is JPParameterizedTypeName -> type.rawType().typeDescriptor
            is JPArrayTypeName -> type.typeDescriptor
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

    private val JPClassName.binaryName: String
        get() = buildString {
            if (packageName().isNotEmpty()) {
                append(packageName().replace('.', '/')).append('/')
            }
            append(simpleNames().joinToString("$"))
        }

    private val JPClassName.typeDescriptor: String
        get() = "L$binaryName;"

    private val JPArrayTypeName.typeDescriptor: String
        get() = "[" + componentType().asJvmDescriptor().typeDescriptor
}

private const val CONSTRUCTOR_NAME: String = "<init>"
private const val VOID_NAME: String = "V"

fun Descriptor.getMemberReference(withReceiver: Boolean = false): String =
    when (val descriptor = this) {
        is MethodDescriptor -> buildString {
            if (withReceiver) {
                append(receiverType.java.asJvmDescriptor().typeDescriptor)
            }
            append(binaryName)
            append("(")
            parameters.forEach {
                append(it.type.java.asJvmDescriptor().typeDescriptor)
            }
            append(")")
            if (descriptor is ConstructorDescriptor) {
                append(VOID_NAME)
            } else {
                append(returnType?.java?.asJvmDescriptor()?.typeDescriptor ?: VOID_NAME)
            }
        }

        is FieldDescriptor -> buildString {
            if (withReceiver) {
                append(receiverType.java.asJvmDescriptor().typeDescriptor)
            }
            append(targetName)
            append(":")
            append(type.asIr().java.asJvmDescriptor().typeDescriptor)
        }
    }

fun JPTypeName.asJvmDescriptor(): JvmDescriptor =
    JvmDescriptor(this)

val MethodDescriptor.binaryName: String
    get() = when (this) {
        is ConstructorDescriptor -> CONSTRUCTOR_NAME
        else -> targetName
    }

val IrAccessorKind.binaryName: String
    get() = when (this) {
        is IrConstructorAccessor -> CONSTRUCTOR_NAME
        else -> targetName
    }
