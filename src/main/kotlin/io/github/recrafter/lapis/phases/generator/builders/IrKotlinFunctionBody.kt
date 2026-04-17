package io.github.recrafter.lapis.phases.generator.builders

import io.github.recrafter.lapis.extensions.kp.*

@JvmInline
value class IrKotlinFunctionBody(private val builder: KPFunctionBuilder) {

    fun IrKotlinFunctionBody.code_(
        format: String,
        isReturn: Boolean = false,
        arguments: Builder<IrKotlinCodeBlock.Arguments> = {}
    ) {
        if (isReturn) {
            return_(format, arguments)
        } else {
            builder.addStatement(buildKotlinCodeBlock(format, arguments))
        }
    }

    fun IrKotlinFunctionBody.return_(
        format: String? = null,
        arguments: Builder<IrKotlinCodeBlock.Arguments> = {}
    ) {
        builder.addReturnStatement(format?.let { buildKotlinCodeBlock(it, arguments) })
    }

    fun IrKotlinFunctionBody.with_(
        receiver: KPCodeBlock,
        block: Builder<IrKotlinCodeBlock>
    ) {
        withControlFlow(buildKotlinCodeBlock("with(%L)") { arg(receiver) }, block)
    }

    fun IrKotlinFunctionBody.throw_(
        format: String,
        arguments: Builder<IrKotlinCodeBlock.Arguments> = {}
    ) {
        builder.addStatement(buildKotlinCodeBlock("throw $format", arguments))
    }

    private fun IrKotlinFunctionBody.withControlFlow(controlFlow: KPCodeBlock, body: Builder<IrKotlinCodeBlock>) {
        builder.beginControlFlow("%L", controlFlow)
        buildKotlinCodeBlock(body)
        builder.endControlFlow()
    }
}
