package io.github.recrafter.lapis.extensions.ksp

fun KSPProperty.isExtension(): Boolean =
    extensionReceiver != null
