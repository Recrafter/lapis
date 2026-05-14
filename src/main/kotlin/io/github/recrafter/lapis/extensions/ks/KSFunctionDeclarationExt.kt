package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier

val KSFunctionDeclaration.hasExtensionReceiver: Boolean
    get() = extensionReceiver != null

val KSFunctionDeclaration.isExplicitlyOpen: Boolean
    get() = Modifier.OPEN in modifiers

val KSFunctionDeclaration.constructorProperties: List<KSValueParameter>
    get() = parameters.filter { it.isVal || it.isVar }
