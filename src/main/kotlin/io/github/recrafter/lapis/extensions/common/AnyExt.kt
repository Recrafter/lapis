package io.github.recrafter.lapis.extensions.common

inline fun <reified T : Any> Any.castOrNull(): T? =
    this as? T
