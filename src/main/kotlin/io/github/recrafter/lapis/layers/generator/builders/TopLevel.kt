package io.github.recrafter.lapis.layers.generator.builders

typealias Builder<T> = T.() -> Unit
typealias Remapper<T> = (T) -> T
