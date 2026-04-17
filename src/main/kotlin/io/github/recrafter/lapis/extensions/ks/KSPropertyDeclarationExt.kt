package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSPropertyDeclaration

val KSPropertyDeclaration.isExtension: Boolean
    get() = extensionReceiver != null
