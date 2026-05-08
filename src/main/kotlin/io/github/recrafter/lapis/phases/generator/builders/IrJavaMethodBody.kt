package io.github.recrafter.lapis.phases.generator.builders

import io.github.recrafter.lapis.extensions.jp.JPCodeBlock
import io.github.recrafter.lapis.extensions.jp.JPMethodBuilder
import io.github.recrafter.lapis.extensions.jp.buildJavaCodeBlock
import io.github.recrafter.lapis.phases.lowering.types.IrClassName

@JvmInline
value class IrJavaMethodBody(private val builder: JPMethodBuilder) {

    fun IrJavaMethodBody.code_(codeBlock: JPCodeBlock) {
        builder.addStatement(codeBlock)
    }

    fun IrJavaMethodBody.code_(
        format: String,
        isReturn: Boolean = false,
        argumentsBuilder: Builder<IrJavaCodeBlock.Arguments> = {}
    ) {
        if (isReturn) {
            return_(format, argumentsBuilder)
        } else {
            code_(buildJavaCodeBlock(format, argumentsBuilder))
        }
    }

    fun IrJavaMethodBody.return_(
        format: String? = null,
        argumentsBuilder: Builder<IrJavaCodeBlock.Arguments> = {}
    ) {
        code_(buildJavaCodeBlock("return" + format?.let { " $it" }.orEmpty(), argumentsBuilder))
    }

    fun IrJavaMethodBody.return_(codeBlock: JPCodeBlock) {
        code_(buildJavaCodeBlock("return %L") { +codeBlock })
    }

    fun IrJavaMethodBody.if_(condition: JPCodeBlock, body: Builder<IrJavaCodeBlock>) {
        withControlFlow(buildJavaCodeBlock("if (%L)") { +condition }, body)
    }

    fun IrJavaMethodBody.throw_(
        format: String,
        argumentsBuilder: Builder<IrJavaCodeBlock.Arguments> = {}
    ) {
        builder.addStatement(buildJavaCodeBlock("throw $format", argumentsBuilder))
    }

    @Suppress("LocalVariableName")
    fun IrJavaMethodBody.try_(
        block: Builder<IrJavaCodeBlock>,
        catchingClassName: IrClassName,
        catch_: Builder<IrJavaCodeBlock>? = null,
        finally_: Builder<IrJavaCodeBlock>? = null,
    ) {
        builder.beginControlFlow(buildJavaCodeBlock("try"))
        buildJavaCodeBlock(block)
        builder.nextControlFlow(buildJavaCodeBlock("catch (%T %L)") {
            +catchingClassName; +(if (catch_ == null) "ignored" else "e")
        })
        catch_?.let(::buildJavaCodeBlock)
        finally_?.let {
            builder.nextControlFlow(buildJavaCodeBlock("finally"))
            buildJavaCodeBlock(it)
        }
        builder.endControlFlow()
    }

    fun IrJavaMethodBody.synchronized_(lock: JPCodeBlock, body: Builder<IrJavaCodeBlock>) {
        builder.beginControlFlow(buildJavaCodeBlock("synchronized (%L)") { +lock })
        buildJavaCodeBlock(body)
        builder.endControlFlow()
    }

    private fun IrJavaMethodBody.withControlFlow(controlFlow: JPCodeBlock, body: Builder<IrJavaCodeBlock>) {
        builder.beginControlFlow(controlFlow)
        buildJavaCodeBlock(body)
        builder.endControlFlow()
    }
}
