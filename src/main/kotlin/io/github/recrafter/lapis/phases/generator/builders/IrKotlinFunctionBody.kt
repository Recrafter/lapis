package io.github.recrafter.lapis.phases.generator.builders

import io.github.recrafter.lapis.extensions.kp.KPFunctionBuilder
import io.github.recrafter.lapis.extensions.kp.addReturnStatement
import io.github.recrafter.lapis.extensions.kp.addStatement
import io.github.recrafter.lapis.extensions.kp.buildKotlinCodeBlock

@JvmInline
value class IrKotlinFunctionBody(private val builder: KPFunctionBuilder) {

    fun IrKotlinFunctionBody.code_(
        format: String,
        isReturn: Boolean = false,
        argumentsBuilder: Builder<IrKotlinCodeBlock.Arguments> = {}
    ) {
        if (isReturn) {
            return_(format, argumentsBuilder)
        } else {
            builder.addStatement(buildKotlinCodeBlock(format, argumentsBuilder))
        }
    }

    fun IrKotlinFunctionBody.return_(
        format: String? = null,
        argumentsBuilder: Builder<IrKotlinCodeBlock.Arguments> = {}
    ) {
        builder.addReturnStatement(format?.let { buildKotlinCodeBlock(it, argumentsBuilder) })
    }

    fun IrKotlinFunctionBody.throw_(
        format: String,
        argumentsBuilder: Builder<IrKotlinCodeBlock.Arguments> = {}
    ) {
        builder.addStatement(buildKotlinCodeBlock("throw $format", argumentsBuilder))
    }
}
