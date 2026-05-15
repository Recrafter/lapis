package io.github.recrafter.lapis.phases.common

import io.github.recrafter.lapis.extensions.jp.JPModifier

object JavaModifiers {
    val visibilities: List<JPModifier> = listOf(
        JPModifier.PUBLIC, JPModifier.PROTECTED, JPModifier.PRIVATE
    )
    val fieldAllowed: List<JPModifier> = visibilities + listOf(
        JPModifier.STATIC, JPModifier.FINAL, JPModifier.TRANSIENT,
        JPModifier.VOLATILE,
    )
    val methodAllowed: List<JPModifier> = visibilities + listOf(
        JPModifier.ABSTRACT, JPModifier.STATIC, JPModifier.FINAL,
        JPModifier.SYNCHRONIZED, JPModifier.NATIVE, JPModifier.STRICTFP,
    )
    val methodConflicts: List<JPModifier> = listOf(
        JPModifier.ABSTRACT, JPModifier.FINAL,
    )
    val abstractIllegals: List<JPModifier> = listOf(
        JPModifier.PRIVATE,
        JPModifier.STATIC, JPModifier.FINAL,
        JPModifier.SYNCHRONIZED, JPModifier.NATIVE, JPModifier.STRICTFP,
    )
}
