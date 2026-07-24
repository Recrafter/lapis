package io.github.recrafter.lapis.common

import javax.lang.model.element.Modifier

object JavaModifiers {
    val visibilities: List<Modifier> = listOf(
        Modifier.PUBLIC, Modifier.PROTECTED, Modifier.PRIVATE
    )
    val fieldAllowed: List<Modifier> = visibilities + listOf(
        Modifier.STATIC, Modifier.FINAL, Modifier.TRANSIENT,
        Modifier.VOLATILE,
    )
    val methodAllowed: List<Modifier> = visibilities + listOf(
        Modifier.ABSTRACT, Modifier.STATIC, Modifier.FINAL,
        Modifier.SYNCHRONIZED, Modifier.NATIVE, Modifier.STRICTFP,
    )
    val methodConflicts: List<Modifier> = listOf(
        Modifier.ABSTRACT, Modifier.FINAL,
    )
    val abstractIllegals: List<Modifier> = listOf(
        Modifier.PRIVATE,
        Modifier.STATIC, Modifier.FINAL,
        Modifier.SYNCHRONIZED, Modifier.NATIVE, Modifier.STRICTFP,
    )
}
