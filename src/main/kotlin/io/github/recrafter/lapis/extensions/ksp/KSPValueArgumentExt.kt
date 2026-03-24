package io.github.recrafter.lapis.extensions.ksp

import io.github.recrafter.lapis.extensions.common.castOrNull

fun KSPValueArgument.getClassDeclValue(): KSPClassDecl? =
    value?.castOrNull<KSPType>()?.toClassDeclOrNull()
