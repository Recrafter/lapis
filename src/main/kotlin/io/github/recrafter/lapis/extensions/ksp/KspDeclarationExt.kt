package io.github.recrafter.lapis.extensions.ksp

import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import kotlin.reflect.KClass

val KspDeclaration.name: String
    get() = simpleName.asString()

fun KspDeclaration.hasParent(): Boolean =
    parentDeclaration != null

fun KspDeclaration.isInstance(typeClass: KClass<*>): Boolean =
    qualifiedName?.asString() == typeClass.qualifiedName

inline fun <reified T> KspDeclaration.isInstance(): Boolean =
    isInstance(T::class)

fun KspDeclaration.isInstance(type: IrClassName): Boolean =
    qualifiedName?.asString() == type.qualifiedName
