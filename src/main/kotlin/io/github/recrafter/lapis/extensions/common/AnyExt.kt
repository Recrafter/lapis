package io.github.recrafter.lapis.extensions.common

import com.squareup.kotlinpoet.asClassName
import io.github.recrafter.lapis.layers.lowering.IrClassName
import io.github.recrafter.lapis.layers.lowering.asIr
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

inline fun <reified T : Any> Any.castOrNull(): T? =
    this as? T

fun KClass<*>.asIr(): IrClassName =
    asClassName().asIr()

inline fun <reified A : Annotation> getAnnotationDefaultIntValue(property: KProperty1<A, Int>): Int =
    requireNotNull(A::class.java.getDeclaredMethod(property.name).defaultValue as? Int)
