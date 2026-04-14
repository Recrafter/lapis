package io.github.recrafter.lapis.extensions.ks

import io.github.recrafter.lapis.extensions.common.castOrNull

fun KSValueArgument.getKClassType(): KSType? =
    value?.castOrNull<KSType>()

fun KSValueArgument.getTypeClassDecl(): KSClassDecl? =
    getKClassType()?.getClassDecl()?.takeNotNothing()
