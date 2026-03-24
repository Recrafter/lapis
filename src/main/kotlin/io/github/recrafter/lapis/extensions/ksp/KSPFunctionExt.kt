package io.github.recrafter.lapis.extensions.ksp

val KSPFunctionDecl.isExtension: Boolean
    get() = extensionReceiver != null

fun KSPFunctionDecl.getReturnTypeOrNull(): KSPType? =
    returnType?.resolve()?.takeNotUnit()
