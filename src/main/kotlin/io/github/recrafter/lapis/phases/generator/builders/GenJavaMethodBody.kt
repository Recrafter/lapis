package io.github.recrafter.lapis.phases.generator.builders

import io.github.recrafter.lapis.extensions.common.Builder
import io.github.recrafter.lapis.extensions.jp.JPCodeBlock
import io.github.recrafter.lapis.extensions.jp.JPMethodBuilder
import io.github.recrafter.lapis.extensions.jp.buildJavaCodeBlock
import io.github.recrafter.lapis.phases.lowering.types.IrClassName

@JvmInline
value class GenJavaMethodBody(private val builder: JPMethodBuilder) {

    fun GenJavaMethodBody.code_(codeBlock: JPCodeBlock) {
        builder.addStatement(codeBlock)
    }

    fun GenJavaMethodBody.code_(
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

    fun GenJavaMethodBody.return_(
        format: String? = null,
        argumentsBuilder: Builder<IrJavaCodeBlock.Arguments> = {}
    ) {
        code_(buildJavaCodeBlock("return" + format?.let { " $it" }.orEmpty(), argumentsBuilder))
    }

    fun GenJavaMethodBody.return_(codeBlock: JPCodeBlock) {
        code_(buildJavaCodeBlock("return %L") { +codeBlock })
    }

    fun GenJavaMethodBody.if_(condition: JPCodeBlock, body: Builder<IrJavaCodeBlock>) {
        withControlFlow(buildJavaCodeBlock("if (%L)") { +condition }, body)
    }

    fun GenJavaMethodBody.throw_(
        format: String,
        argumentsBuilder: Builder<IrJavaCodeBlock.Arguments> = {}
    ) {
        builder.addStatement(buildJavaCodeBlock("throw $format", argumentsBuilder))
    }

    @Suppress("LocalVariableName")
    fun GenJavaMethodBody.try_(
        block_: JPCodeBlock,
        catchingClassName: IrClassName,
        catch_: Builder<IrJavaCodeBlock>? = null,
        finally_: Builder<IrJavaCodeBlock>? = null,
    ) {
        builder.beginControlFlow(buildJavaCodeBlock("try"))
        builder.addCode(block_)
        builder.nextControlFlow(buildJavaCodeBlock("catch (%T %L)") {
            +catchingClassName; +(if (catch_ == null) "ignored" else "e")
        })
        catch_?.let { builder.addCode(buildJavaCodeBlock(it)) }
        finally_?.let {
            builder.nextControlFlow(buildJavaCodeBlock("finally"))
            builder.addCode(buildJavaCodeBlock(it))
        }
        builder.endControlFlow()
    }

    fun GenJavaMethodBody.synchronized_(lock: JPCodeBlock, body: Builder<IrJavaCodeBlock>) {
        builder.beginControlFlow(buildJavaCodeBlock("synchronized (%L)") { +lock })
        builder.addCode(buildJavaCodeBlock(body))
        builder.endControlFlow()
    }

    private fun GenJavaMethodBody.withControlFlow(controlFlow: JPCodeBlock, body: Builder<IrJavaCodeBlock>) {
        builder.beginControlFlow(controlFlow)
        builder.addCode(buildJavaCodeBlock(body))
        builder.endControlFlow()
    }
}
