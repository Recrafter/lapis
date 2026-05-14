package io.github.recrafter.lapis.phases.generator.builders

import io.github.recrafter.lapis.extensions.jp.JPField
import io.github.recrafter.lapis.extensions.jp.JPMethod
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.models.format

sealed interface JPEntity {
    val callFormat: String
    val referenceFormat: String get() = "%N"
}

class JPFieldEntity(val field: JPField) : JPEntity {
    override val callFormat: String = referenceFormat
}

class JPMethodEntity(
    val method: JPMethod,
    val parameters: List<IrParameter> = emptyList()
) : JPEntity {
    override val callFormat: String = "$referenceFormat(${parameters.format})"
}
