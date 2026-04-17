package io.github.recrafter.lapis.phases.generator.accessor

import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import io.github.recrafter.lapis.phases.lowering.types.jvmName

class FieldEntry(
    override val ownerClassName: IrClassName,
    val name: String,
    val typeName: IrTypeName,
    val removeFinal: Boolean,
) : AccessorConfigEntry {

    override val awEntry: String = buildString {
        val fieldPart = buildString {
            append("field ")
            append(ownerClassName.internalName)
            append(" ")
            append(name)
            append(" ")
            append(typeName.jvmName)
        }
        append("accessible $fieldPart")
        if (removeFinal) {
            appendLine()
            append("mutable $fieldPart")
        }
    }

    override val atEntry: String = buildString {
        append(
            if (removeFinal) "public-f"
            else "public"
        )
        append(" ")
        append(ownerClassName.binaryName)
        append(" ")
        append(name)
    }

    override val sectionIndex: Int = 1
    override val sortingName: String = name
}
