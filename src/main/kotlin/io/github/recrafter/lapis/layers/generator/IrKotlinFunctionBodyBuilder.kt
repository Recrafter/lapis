package io.github.recrafter.lapis.layers.generator

import io.github.recrafter.lapis.extensions.kp.KPFunctionBuilder
import io.github.recrafter.lapis.extensions.kp.addReturnStatement
import io.github.recrafter.lapis.extensions.kp.addStatement
import io.github.recrafter.lapis.extensions.kp.buildKotlinCodeBlock

@JvmInline
value class IrKotlinFunctionBodyBuilder(private val functionBuilder: KPFunctionBuilder) {

    fun IrKotlinFunctionBodyBuilder.code(
        format: String,
        arguments: IrKotlinCodeBlockBuilder.Arguments.() -> Unit = {}
    ) {
        functionBuilder.addStatement(buildKotlinCodeBlock(format, arguments))
    }

    fun IrKotlinFunctionBodyBuilder.throw_(
        format: String,
        arguments: IrKotlinCodeBlockBuilder.Arguments.() -> Unit = {}
    ) {
        functionBuilder.addStatement(buildKotlinCodeBlock("throw $format", arguments))
    }

    fun IrKotlinFunctionBodyBuilder.return_(
        format: String? = null,
        arguments: IrKotlinCodeBlockBuilder.Arguments.() -> Unit = {}
    ) {
        functionBuilder.addReturnStatement(format?.let { buildKotlinCodeBlock(it, arguments) })
    }
}
