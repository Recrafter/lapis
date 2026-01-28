package io.github.recrafter.lapis.extensions.ksp

fun KspPropertyDeclaration.isExtension(): Boolean =
    extensionReceiver != null
