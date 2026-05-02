package io.github.recrafter.lapis.extensions.common

import kotlin.reflect.KClass

inline fun <reified T : Any> Any.castOrNull(): T? =
    this as? T

inline fun <reified T : Any> KClass<T>.requireQualifiedName(): String =
    qualifiedName ?: lapisError("Cannot resolve qualified name for ${T::class.simpleName}")
