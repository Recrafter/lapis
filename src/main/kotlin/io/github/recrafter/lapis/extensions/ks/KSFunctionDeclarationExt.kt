package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier

val KSFunctionDeclaration.isExtension: Boolean
    get() = extensionReceiver != null

val KSFunctionDeclaration.isExplicitlyOpen: Boolean
    get() = modifiers.contains(Modifier.OPEN)
