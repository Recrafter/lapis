package io.github.recrafter.lapis.phases.generator.builders

import io.github.recrafter.lapis.extensions.jp.JPField
import io.github.recrafter.lapis.extensions.jp.JPMethod
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.models.format

sealed interface IrJavaMember {
    val callFormat: String
    val referenceFormat: String get() = "%N"
}

class IrFieldMember(val field: JPField) : IrJavaMember {
    override val callFormat: String = "%N"
}

class IrMethodMember(val method: JPMethod, val parameters: List<IrParameter> = emptyList()) : IrJavaMember {
    override val callFormat: String = "%N(${parameters.format})"
}
