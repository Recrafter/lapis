package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.extensions.common.castOrNull

val KSType.isValid: Boolean
    get() = !isError

val KSType.genericTypes: List<KSType>
    get() = arguments.mapNotNull { it.type?.resolve() }

fun KSType.getGenericTypeOrNull(): KSType? =
    genericTypes.firstOrNull()

fun KSType.toClassDeclaration(): KSClassDeclaration? =
    declaration.castOrNull<KSClassDeclaration>()
