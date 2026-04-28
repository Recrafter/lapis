package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSDeclaration

val KSDeclaration.name: String
    get() = simpleName.asString()

fun KSDeclaration.parentDeclarations(includeSelf: Boolean = false): Sequence<KSDeclaration> =
    generateSequence(
        if (includeSelf) this else parentDeclaration
    ) { it.parentDeclaration }
