package io.github.recrafter.lapis.extensions.ks

import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import kotlin.reflect.KClass

val KSDecl.name: String
    get() = simpleName.asString()

fun KSDecl.isInstance(kClass: KClass<*>): Boolean =
    qualifiedName?.asString() == kClass.qualifiedName

fun KSDecl.isInstance(className: IrClassName): Boolean =
    qualifiedName != null && qualifiedName?.asString() == className.qualifiedName

inline fun <reified T> KSDecl.isInstance(): Boolean =
    isInstance(T::class)
