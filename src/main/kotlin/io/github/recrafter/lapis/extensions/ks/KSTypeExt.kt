package io.github.recrafter.lapis.extensions.ks

import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ksp.KSPResolver
import kotlin.reflect.KClass

val KSType.isValid: Boolean
    get() = !isError

val KSType.genericTypes: List<KSType>
    get() = arguments.mapNotNull { it.type?.resolve() }

fun KSType.getGenericTypeOrNull(): KSType? =
    genericTypes.firstOrNull()

fun KSType.takeNotUnit(): KSType? =
    takeUnless { it.declaration.isInstance<Unit>() }

fun KSType.isSame(other: KClass<*>): Boolean {
    val thisName = declaration.qualifiedName?.asString() ?: return false
    val otherName = other.qualifiedName ?: return false
    return thisName == otherName
}

fun KSType.getClassDecl(): KSClassDecl? =
    declaration.castOrNull<KSClassDecl>()

fun KSType.findArrayComponentType(resolver: KSPResolver): KSType? =
    when (declaration.qualifiedName?.asString()) {
        KPArray.qualifiedName -> arguments.firstOrNull()?.type?.resolve()
        KPBooleanArray.qualifiedName -> resolver.builtIns.booleanType
        KPByteArray.qualifiedName -> resolver.builtIns.byteType
        KPShortArray.qualifiedName -> resolver.builtIns.shortType
        KPIntArray.qualifiedName -> resolver.builtIns.intType
        KPLongArray.qualifiedName -> resolver.builtIns.longType
        KPCharArray.qualifiedName -> resolver.builtIns.charType
        KPFloatArray.qualifiedName -> resolver.builtIns.floatType
        KPDoubleArray.qualifiedName -> resolver.builtIns.doubleType
        else -> null
    }
