package io.github.recrafter.lapis.layers.validator

import io.github.recrafter.lapis.extensions.ksp.KspDependencies
import io.github.recrafter.lapis.extensions.ksp.KspSymbol
import io.github.recrafter.lapis.extensions.ksp.toDependencies

abstract class KspSourceHolder {

    abstract val source: KspSymbol

    val dependencies: KspDependencies
        get() = source.toDependencies()
}
