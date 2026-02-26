package io.github.recrafter.lapis.layers.generator

import io.github.recrafter.lapis.extensions.kp.KPFunctionBuilder
import io.github.recrafter.lapis.extensions.kp.addStatement
import io.github.recrafter.lapis.extensions.kp.buildKotlinCodeBlock

@JvmInline
value class IrKotlinFunctionBodyBuilder(private val functionBuilder: KPFunctionBuilder) {

    fun IrKotlinFunctionBodyBuilder.line(
        format: String,
        arguments: IrKotlinCodeBlockBuilder.KotlinCodeBlockArguments.() -> Unit = {}
    ) {
        functionBuilder.addStatement(buildKotlinCodeBlock(format, arguments))
    }
}
