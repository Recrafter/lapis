package io.github.recrafter.lapis.extensions.ksp

import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import kotlin.reflect.KClass

val KSPDecl.name: String
    get() = simpleName.asString()

fun KSPDecl.isInstance(kClass: KClass<*>): Boolean =
    qualifiedName?.asString() == kClass.qualifiedName

fun KSPDecl.isInstance(className: IrClassName): Boolean =
    qualifiedName != null && qualifiedName?.asString() == className.qualifiedName

inline fun <reified T> KSPDecl.isInstance(): Boolean =
    isInstance(T::class)
