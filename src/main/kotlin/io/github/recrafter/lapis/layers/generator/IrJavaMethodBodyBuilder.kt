package io.github.recrafter.lapis.layers.generator

import io.github.recrafter.lapis.extensions.jp.JPMethodBuilder
import io.github.recrafter.lapis.extensions.jp.buildJavaCodeBlock

@JvmInline
value class IrJavaMethodBodyBuilder(private val methodBuilder: JPMethodBuilder) {

    fun IrJavaMethodBodyBuilder.line(
        format: String,
        arguments: IrJavaCodeBlockBuilder.Arguments.() -> Unit = {}
    ) {
        methodBuilder.addStatement(buildJavaCodeBlock(format, arguments))
    }

    fun IrJavaMethodBodyBuilder.return_(
        format: String? = null,
        arguments: IrJavaCodeBlockBuilder.Arguments.() -> Unit = {}
    ) {
        line(
            buildString {
                append("return")
                format?.let { append(" $it") }
            },
            arguments
        )
    }
}
