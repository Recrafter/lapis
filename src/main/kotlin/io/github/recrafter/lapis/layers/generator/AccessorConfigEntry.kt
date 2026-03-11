package io.github.recrafter.lapis.layers.generator

import io.github.recrafter.lapis.layers.lowering.JvmDescriptor
import io.github.recrafter.lapis.layers.lowering.types.IrClassType
import io.github.recrafter.lapis.layers.lowering.types.IrType

sealed interface AccessorConfigEntry {
    val awEntry: String
    val atEntry: String
}

class ClassEntry(
    val classType: IrClassType,
    val needRemoveFinal: Boolean,
) : AccessorConfigEntry {

    override val awEntry: String
        get() = buildString {
            append(
                if (needRemoveFinal) "extendable"
                else "accessible"
            )
            append(" class ")
            append(classType.awClassName)
        }

    override val atEntry: String
        get() = buildString {
            append(
                if (needRemoveFinal) "public-f"
                else "public"
            )
            append(" ")
            append(classType.atClassName)
        }
}

class MethodEntry(
    val ownerClassType: IrClassType,
    val name: String,
    val parameterTypes: List<IrType>,
    val returnType: IrType?,
    val needRemoveFinal: Boolean,
) : AccessorConfigEntry {

    override val awEntry: String
        get() = buildString {
            append(
                if (needRemoveFinal) "extendable"
                else "accessible"
            )
            append(" method ")
            append(ownerClassType.awClassName)
            append(" ")
            append(name)
            append(" ")
            append(JvmDescriptor.buildMethodSignature(parameterTypes, returnType))
        }

    override val atEntry: String
        get() = buildString {
            append(
                if (needRemoveFinal) "public-f"
                else "public"
            )
            append(" ")
            append(ownerClassType.atClassName)
            append(" ")
            append(name)
            append(JvmDescriptor.buildMethodSignature(parameterTypes, returnType))
        }
}

class FieldEntry(
    val ownerClassType: IrClassType,
    val name: String,
    val type: IrType,
    val needRemoveFinal: Boolean,
) : AccessorConfigEntry {

    override val awEntry: String
        get() = buildString {
            append(
                if (needRemoveFinal) "mutable"
                else "accessible"
            )
            append(" field ")
            append(ownerClassType.awClassName)
            append(" ")
            append(name)
            append(" ")
            append(JvmDescriptor.of(type))
        }

    override val atEntry: String
        get() = buildString {
            append(
                if (needRemoveFinal) "public-f"
                else "public"
            )
            append(" ")
            append(ownerClassType.atClassName)
            append(" ")
            append(name)
        }
}

private val IrClassType.awClassName: String
    get() = buildString {
        if (packageName.isNotEmpty()) {
            append(packageName.replace('.', '/'))
            append('/')
        }
        append(kotlin.simpleNames.joinToString("$"))
    }

private val IrClassType.atClassName: String
    get() = buildString {
        if (packageName.isNotEmpty()) {
            append(packageName)
            append('.')
        }
        append(kotlin.simpleNames.joinToString("$"))
    }
