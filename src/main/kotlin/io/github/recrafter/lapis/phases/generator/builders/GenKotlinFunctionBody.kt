package io.github.recrafter.lapis.phases.generator.builders

import io.github.recrafter.lapis.extensions.common.Builder
import io.github.recrafter.lapis.extensions.kp.KPCodeBlock
import io.github.recrafter.lapis.extensions.kp.KPFunctionBuilder
import io.github.recrafter.lapis.extensions.kp.buildKotlinCodeBlock

@JvmInline
value class GenKotlinFunctionBody(private val builder: KPFunctionBuilder) {

    fun GenKotlinFunctionBody.code_(codeBlock: KPCodeBlock) {
        builder.addStatement("%L", codeBlock)
    }

    fun GenKotlinFunctionBody.return_(
        format: String? = null,
        argumentsBuilder: Builder<IrKotlinCodeBlock.Arguments> = {}
    ) {
        code_(buildKotlinCodeBlock("return" + format?.let { " $it" }.orEmpty(), argumentsBuilder))
    }

    fun GenKotlinFunctionBody.code_(
        format: String,
        isReturn: Boolean = false,
        argumentsBuilder: Builder<IrKotlinCodeBlock.Arguments> = {}
    ) {
        if (isReturn) {
            return_(format, argumentsBuilder)
        } else {
            code_(buildKotlinCodeBlock(format, argumentsBuilder))
        }
    }

    fun GenKotlinFunctionBody.throw_(
        format: String,
        argumentsBuilder: Builder<IrKotlinCodeBlock.Arguments> = {}
    ) {
        code_(buildKotlinCodeBlock("throw $format", argumentsBuilder))
    }
}
