package io.github.recrafter.lapis.extensions.ksp

import io.github.recrafter.lapis.extensions.ks.KSPropertyDecl

val KSPropertyDecl.isExtension: Boolean
    get() = extensionReceiver != null
