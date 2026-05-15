package io.github.recrafter.lapis.phases.generator.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.phases.generator.builders.GenKotlinEntity

class GenExtensionPackAccumulator {

    private var _entities: MutableList<GenKotlinEntity> = mutableListOf()
    private var _originatingFiles: MutableList<KSFile> = mutableListOf()

    val entities: List<GenKotlinEntity> = _entities
    val originatingFiles: List<KSFile> = _originatingFiles

    fun accumulate(entities: List<GenKotlinEntity>, originatingFiles: List<KSFile>) {
        _entities += entities
        _originatingFiles += originatingFiles
    }

    fun isNotEmpty(): Boolean =
        entities.isNotEmpty()
}
