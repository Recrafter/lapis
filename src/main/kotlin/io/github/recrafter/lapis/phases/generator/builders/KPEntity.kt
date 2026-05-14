package io.github.recrafter.lapis.phases.generator.builders

import io.github.recrafter.lapis.extensions.kp.KPFunction
import io.github.recrafter.lapis.extensions.kp.KPProperty
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.models.format

sealed interface KPEntity {
    val callFormat: String
    val referenceFormat: String get() = "%N"
}

class KPPropertyEntity(val property: KPProperty) : KPEntity {
    override val callFormat: String = referenceFormat
}

class KPFunctionEntity(
    val function: KPFunction,
    val parameters: List<IrParameter> = emptyList()
) : KPEntity {
    override val callFormat: String = "$referenceFormat(${parameters.format})"
}
