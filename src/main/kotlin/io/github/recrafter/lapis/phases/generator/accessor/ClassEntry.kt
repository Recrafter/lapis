package io.github.recrafter.lapis.phases.generator.accessor

import io.github.recrafter.lapis.extensions.jp.binaryName
import io.github.recrafter.lapis.extensions.jp.internalName
import io.github.recrafter.lapis.phases.lowering.types.IrClassName

class ClassEntry(
    override val ownerClassName: IrClassName,
    val removeFinal: Boolean,
) : AccessorConfigEntry {

    override val awEntry: String = buildString {
        append(
            if (removeFinal) "extendable"
            else "accessible"
        )
        append(" class ")
        append(ownerClassName.java.internalName)
    }

    override val atEntry: String = buildString {
        append(
            if (removeFinal) "public-f"
            else "public"
        )
        append(" ")
        append(ownerClassName.java.binaryName)
    }

    override val sectionIndex: Int = 0
}
