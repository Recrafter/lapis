package io.github.recrafter.lapis.layers.generator

import io.github.recrafter.lapis.layers.lowering.JvmDescriptor
import io.github.recrafter.lapis.layers.lowering.types.IrClassType
import io.github.recrafter.lapis.layers.lowering.types.IrType

sealed interface AccessorConfigEntry : Comparable<AccessorConfigEntry> {
    val awEntry: String
    val atEntry: String

    val ownerClass: IrClassType
    val sectionIndex: Int
    val sortingName: String get() = ""
    val parametersCount: Int get() = 0

    override fun compareTo(other: AccessorConfigEntry): Int =
        compareBy<AccessorConfigEntry> { it.ownerClass.qualifiedName }
            .thenBy { it.sectionIndex }
            .thenBy { it.sortingName }
            .thenBy { it.parametersCount }
            .thenBy { it.awEntry }
            .compare(this, other)
}

class ClassEntry(
    override val ownerClass: IrClassType,
    val needRemoveFinal: Boolean,
) : AccessorConfigEntry {

    override val awEntry: String
        get() = buildString {
            append(
                if (needRemoveFinal) "extendable"
                else "accessible"
            )
            append(" class ")
            append(ownerClass.awClassName)
        }

    override val atEntry: String
        get() = buildString {
            append(
                if (needRemoveFinal) "public-f"
                else "public"
            )
            append(" ")
            append(ownerClass.atClassName)
        }

    override val sectionIndex: Int = 0
}

class FieldEntry(
    override val ownerClass: IrClassType,
    val name: String,
    val type: IrType,
    val needRemoveFinal: Boolean,
) : AccessorConfigEntry {

    override val awEntry: String
        get() = buildString {
            val field = buildString {
                append("field ")
                append(ownerClass.awClassName)
                append(" ")
                append(name)
                append(" ")
                append(JvmDescriptor.of(type))
            }
            append("accessible $field")
            if (needRemoveFinal) {
                appendLine()
                append("mutable $field")
            }
        }

    override val atEntry: String
        get() = buildString {
            append(
                if (needRemoveFinal) "public-f"
                else "public"
            )
            append(" ")
            append(ownerClass.atClassName)
            append(" ")
            append(name)
        }

    override val sectionIndex: Int = 1
    override val sortingName: String = name
}

class MethodEntry(
    override val ownerClass: IrClassType,
    val name: String,
    val parameterTypes: List<IrType>,
    val returnType: IrType?,
    val needRemoveFinal: Boolean,
    val isConstructor: Boolean,
) : AccessorConfigEntry {

    override val awEntry: String
        get() = buildString {
            append(
                if (needRemoveFinal) "extendable"
                else "accessible"
            )
            append(" method ")
            append(ownerClass.awClassName)
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
            append(ownerClass.atClassName)
            append(" ")
            append(name)
            append(JvmDescriptor.buildMethodSignature(parameterTypes, returnType))
        }

    override val sectionIndex: Int = if (isConstructor) 2 else 3
    override val sortingName: String = if (isConstructor) "" else name
    override val parametersCount: Int = parameterTypes.size
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
