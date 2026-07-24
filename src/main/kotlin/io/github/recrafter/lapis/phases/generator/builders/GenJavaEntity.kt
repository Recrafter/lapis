package io.github.recrafter.lapis.phases.generator.builders

import io.github.diskria.poetesse.java.JPField
import io.github.diskria.poetesse.java.JPMethod
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.models.format

sealed interface GenJavaEntity {
    val callFormat: String
    val referenceFormat: String get() = "%N"
}

class GenJavaFieldEntity(val field: JPField) : GenJavaEntity {
    override val callFormat: String = referenceFormat
}

class GenJavaMethodEntity(
    val method: JPMethod,
    val parameters: List<IrParameter> = emptyList()
) : GenJavaEntity {
    override val callFormat: String = "$referenceFormat(${parameters.format})"
}
