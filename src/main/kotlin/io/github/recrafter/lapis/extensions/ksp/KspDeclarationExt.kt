package io.github.recrafter.lapis.extensions.ksp

import kotlin.reflect.KClass

val KspDeclaration.name: String
    get() = simpleName.asString()

fun KspDeclaration.hasParent(): Boolean =
    parentDeclaration != null

fun KspDeclaration.isInstance(typeClass: KClass<*>): Boolean =
    qualifiedName?.asString() == typeClass.qualifiedName

inline fun <reified T> KspDeclaration.isInstance(): Boolean =
    isInstance(T::class)
