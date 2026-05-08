package io.github.recrafter.lapis.phases.generator.builders

import io.github.recrafter.lapis.extensions.kp.*

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
            builder.addStatement(buildKotlinCodeBlock(format, argumentsBuilder = argumentsBuilder))
        }
    }

    fun IrKotlinFunctionBody.return_(
        format: String? = null,
        argumentsBuilder: Builder<IrKotlinCodeBlock.Arguments> = {}
    ) {
        builder.addReturnStatement(format?.let { buildKotlinCodeBlock(it, argumentsBuilder = argumentsBuilder) })
    }

    fun IrKotlinFunctionBody.with_(
        receiver: KPCodeBlock,
        block: Builder<IrKotlinCodeBlock>
    ) {
        withControlFlow(buildKotlinCodeBlock("with(%L)") { +receiver }, block)
    }

    fun IrKotlinFunctionBody.throw_(
        format: String,
        argumentsBuilder: Builder<IrKotlinCodeBlock.Arguments> = {}
    ) {
        builder.addStatement(buildKotlinCodeBlock("throw $format", argumentsBuilder = argumentsBuilder))
    }

    private fun IrKotlinFunctionBody.withControlFlow(controlFlow: KPCodeBlock, body: Builder<IrKotlinCodeBlock>) {
        builder.beginControlFlow("%L", controlFlow)
        buildKotlinCodeBlock(body)
        builder.endControlFlow()
    }
}
