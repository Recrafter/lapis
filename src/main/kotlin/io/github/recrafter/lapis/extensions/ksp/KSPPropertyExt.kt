package io.github.recrafter.lapis.extensions.ksp

val KSPPropertyDecl.isExtension: Boolean
    get() = extensionReceiver != null
