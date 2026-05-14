package io.github.recrafter.lapis.phases.generator.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.phases.generator.builders.KPEntity

class GenExtensionPackAccumulator {

    private var _entities: MutableList<KPEntity> = mutableListOf()
    private var _originatingFiles: MutableList<KSFile> = mutableListOf()

    val entities: List<KPEntity> = _entities
    val originatingFiles: List<KSFile> = _originatingFiles

    fun accumulate(entities: List<KPEntity>, originatingFiles: List<KSFile>) {
        _entities += entities
        _originatingFiles += originatingFiles
    }

    fun isNotEmpty(): Boolean =
        entities.isNotEmpty()
}
