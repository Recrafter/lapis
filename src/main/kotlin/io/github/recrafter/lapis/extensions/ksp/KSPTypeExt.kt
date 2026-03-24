package io.github.recrafter.lapis.extensions.ksp

import io.github.recrafter.lapis.extensions.common.castOrNull
import kotlin.reflect.KClass

val KSPType.genericTypes: List<KSPType>
    get() = arguments.mapNotNull { it.type?.resolve() }

fun KSPType.getGenericTypeOrNull(): KSPType? =
    genericTypes.firstOrNull()

fun KSPType.takeNotUnit(): KSPType? =
    takeUnless { it.declaration.isInstance<Unit>() }

fun KSPType.isSame(other: KSPType?): Boolean {
    val thisName = declaration.qualifiedName?.asString() ?: return false
    val otherName = other?.declaration?.qualifiedName?.asString() ?: return false
    return thisName == otherName
}

fun KSPType.isSame(other: KClass<*>): Boolean {
    val thisName = declaration.qualifiedName?.asString() ?: return false
    val otherName = other.qualifiedName ?: return false
    return thisName == otherName
}

fun KSPType.toClassDeclOrNull(): KSPClassDecl? =
    declaration.castOrNull<KSPClassDecl>()
