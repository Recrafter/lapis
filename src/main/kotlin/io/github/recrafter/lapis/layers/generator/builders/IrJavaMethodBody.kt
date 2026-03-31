package io.github.recrafter.lapis.layers.generator.builders

import io.github.recrafter.lapis.extensions.jp.JPCodeBlock
import io.github.recrafter.lapis.extensions.jp.JPMethodBuilder
import io.github.recrafter.lapis.extensions.jp.buildJavaCodeBlock
import io.github.recrafter.lapis.layers.lowering.types.IrClassName

@JvmInline
value class IrJavaMethodBody(private val builder: JPMethodBuilder) {

    fun IrJavaMethodBody.code_(
        format: String,
        isReturn: Boolean = false,
        arguments: Builder<IrJavaCodeBlock.Arguments> = {}
    ) {
        if (isReturn) {
            return_(format, arguments)
        } else {
            builder.addStatement(buildJavaCodeBlock(format, arguments))
        }
    }

    fun IrJavaMethodBody.return_(
        format: String? = null,
        arguments: Builder<IrJavaCodeBlock.Arguments> = {}
    ) {
        builder.addStatement(
            buildJavaCodeBlock(
                buildString {
                    append("return")
                    format?.let { append(" $it") }
                },
                arguments
            )
        )
    }

    fun IrJavaMethodBody.if_(condition: JPCodeBlock, body: Builder<IrJavaCodeBlock>) {
        withControlFlow(buildJavaCodeBlock("if (%L)") { arg(condition) }, body)
    }

    @Suppress("LocalVariableName")
    fun IrJavaMethodBody.try_(
        try_: Builder<IrJavaCodeBlock>,
        catchingClassName: IrClassName,
        catch_: Builder<IrJavaCodeBlock>? = null,
        finally_: Builder<IrJavaCodeBlock>? = null,
    ) {
        builder.beginControlFlow(buildJavaCodeBlock("try"))
        buildJavaCodeBlock(try_)
        builder.nextControlFlow(buildJavaCodeBlock("catch (%T %L)") {
            arg(catchingClassName)
            arg(if (catch_ == null) "ignored" else "e")
        })
        catch_?.let { buildJavaCodeBlock(it) }
        finally_?.let {
            builder.nextControlFlow(buildJavaCodeBlock("finally"))
            buildJavaCodeBlock(it)
        }
        builder.endControlFlow()
    }

    private fun IrJavaMethodBody.withControlFlow(controlFlow: JPCodeBlock, body: Builder<IrJavaCodeBlock>) {
        builder.beginControlFlow(controlFlow)
        buildJavaCodeBlock(body)
        builder.endControlFlow()
    }
}
