package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSFunctionDeclaration

val KSFunctionDeclaration.isExtension: Boolean
    get() = extensionReceiver != null
