package io.github.recrafter.nametag.accessors.annotations

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class KAccessor(
    val value: KClass<*>,
)
