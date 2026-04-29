package io.github.recrafter.lapis.phases.generator.accessor

import io.github.recrafter.lapis.phases.common.JvmClassName

class ClassEntry(
    override val ownerJvmClassName: JvmClassName,
    val removeFinal: Boolean,
) : AccessorConfigEntry {

    override val awEntry: String = buildString {
        append(
            if (removeFinal) "extendable"
            else "accessible"
        )
        append(" class ")
        append(ownerJvmClassName.internalName)
    }

    override val atEntry: String = buildString {
        append(
            if (removeFinal) "public-f"
            else "public"
        )
        append(" ")
        append(ownerJvmClassName.binaryName)
    }

    override val sectionIndex: Int = 0
}
