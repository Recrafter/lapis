package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier

val KSPropertyDeclaration.isExtension: Boolean
    get() = extensionReceiver != null

val KSPropertyDeclaration.isExplicitlyOpen: Boolean
    get() = Modifier.OPEN in modifiers

val KSPropertyDeclaration.isExplicitlyAbstract: Boolean
    get() = Modifier.ABSTRACT in modifiers
