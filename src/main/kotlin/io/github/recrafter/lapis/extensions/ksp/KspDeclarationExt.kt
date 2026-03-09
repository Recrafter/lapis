package io.github.recrafter.lapis.extensions.ksp

import io.github.recrafter.lapis.layers.lowering.types.IrClassType
import kotlin.reflect.KClass

val KSPDeclaration.name: String
    get() = simpleName.asString()

fun KSPDeclaration.hasParent(): Boolean =
    parentDeclaration != null

fun KSPDeclaration.isInstance(typeClass: KClass<*>): Boolean =
    qualifiedName?.asString() == typeClass.qualifiedName

inline fun <reified T> KSPDeclaration.isInstance(): Boolean =
    isInstance(T::class)

fun KSPDeclaration.isInstance(type: IrClassType): Boolean =
    qualifiedName?.asString() == type.qualifiedName
