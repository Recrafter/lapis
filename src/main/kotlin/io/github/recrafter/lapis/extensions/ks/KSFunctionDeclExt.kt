package io.github.recrafter.lapis.extensions.ks

val KSFunctionDecl.isExtension: Boolean
    get() = extensionReceiver != null

fun KSFunctionDecl.getReturnTypeOrNull(): KSType? =
    returnType?.resolve()?.takeNotUnit()
