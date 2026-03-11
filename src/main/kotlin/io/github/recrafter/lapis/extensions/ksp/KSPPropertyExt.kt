package io.github.recrafter.lapis.extensions.ksp

val KSPProperty.isExtension: Boolean
    get() = extensionReceiver != null
