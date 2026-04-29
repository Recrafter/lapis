package io.github.recrafter.lapis.phases.generator.accessor

import io.github.recrafter.lapis.phases.common.JvmClassName

sealed interface AccessorConfigEntry : Comparable<AccessorConfigEntry> {

    val awEntry: String
    val atEntry: String

    val ownerJvmClassName: JvmClassName
    val sectionIndex: Int
    val sortingName: String get() = ""
    val parametersCount: Int get() = 0

    override fun compareTo(other: AccessorConfigEntry): Int =
        compareBy<AccessorConfigEntry> { it.ownerJvmClassName.qualifiedName }
            .thenBy { it.sectionIndex }
            .thenBy { it.sortingName }
            .thenBy { it.parametersCount }
            .thenBy { it.awEntry }
            .compare(this, other)
}
