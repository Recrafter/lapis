package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier

val KSPropertyDeclaration.isExtension: Boolean
    get() = extensionReceiver != null

val KSPropertyDeclaration.isExplicitlyOpen: Boolean
    get() = modifiers.contains(Modifier.OPEN)

val KSPropertyDeclaration.isExplicitlyAbstract: Boolean
    get() = modifiers.contains(Modifier.ABSTRACT)
