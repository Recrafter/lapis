package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.phases.generator.builders.Builder
import io.github.recrafter.lapis.phases.generator.builders.IrKotlinCodeBlock

fun KPParameterBuilder.setDefaultValue(format: String, argumentsBuilder: Builder<IrKotlinCodeBlock.Arguments>) {
    defaultValue(buildKotlinCodeBlock(format, argumentsBuilder = argumentsBuilder))
}
