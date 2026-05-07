package io.github.recrafter.lapis.phases.generator.builders

import io.github.recrafter.lapis.extensions.jp.JPField
import io.github.recrafter.lapis.extensions.jp.JPMethod
import io.github.recrafter.lapis.phases.lowering.models.IrParameter

sealed interface IrJavaMember {
    val format: String
}

class IrFieldMember(val field: JPField) : IrJavaMember {
    override val format: String = "%N"
}

class IrMethodMember(val method: JPMethod, val parameters: List<IrParameter> = emptyList()) : IrJavaMember {
    override val format: String = "%N(${parameters.joinToString { "%N" }})"
}
