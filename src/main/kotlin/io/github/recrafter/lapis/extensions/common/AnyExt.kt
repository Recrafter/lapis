package io.github.recrafter.lapis.extensions.common

import kotlin.reflect.KProperty1

inline fun <reified T : Any> Any.castOrNull(): T? =
    this as? T

inline fun <reified A : Annotation> getAnnotationDefaultIntValue(property: KProperty1<A, Int>): Int =
    requireNotNull(A::class.java.getDeclaredMethod(property.name).defaultValue as? Int)
