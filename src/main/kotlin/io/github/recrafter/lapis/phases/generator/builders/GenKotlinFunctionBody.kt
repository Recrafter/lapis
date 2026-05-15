package io.github.recrafter.lapis.phases.generator.builders

import io.github.recrafter.lapis.extensions.common.Builder
import io.github.recrafter.lapis.extensions.kp.KPFunctionBuilder
import io.github.recrafter.lapis.extensions.kp.addReturnStatement
import io.github.recrafter.lapis.extensions.kp.addStatement
import io.github.recrafter.lapis.extensions.kp.buildKotlinCodeBlock

@JvmInline
value class GenKotlinFunctionBody(private val builder: KPFunctionBuilder) {

    fun GenKotlinFunctionBody.code_(
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

    fun GenKotlinFunctionBody.return_(
        format: String? = null,
        argumentsBuilder: Builder<IrKotlinCodeBlock.Arguments> = {}
    ) {
        builder.addReturnStatement(format?.let { buildKotlinCodeBlock(it, argumentsBuilder) })
    }

    fun GenKotlinFunctionBody.throw_(
        format: String,
        argumentsBuilder: Builder<IrKotlinCodeBlock.Arguments> = {}
    ) {
        builder.addStatement(buildKotlinCodeBlock("throw $format", argumentsBuilder))
    }
}
