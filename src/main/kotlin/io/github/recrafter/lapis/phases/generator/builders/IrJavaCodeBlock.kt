package io.github.recrafter.lapis.phases.generator.builders

import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.models.format
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import kotlin.reflect.KClass

@JvmInline
value class IrJavaCodeBlock(private val builder: JPCodeBlockBuilder) {

    fun add(format: String, argumentsBuilder: Builder<Arguments> = {}) {
        builder.add(format.fixFormat(), *argumentsBuilder.build())
    }

    fun IrJavaCodeBlock.lambda_(
        parameters: List<IrParameter> = emptyList(),
        inline: Boolean = false,
        bodyBuilder: Builder<IrJavaMethodBody>
    ) {
        val bodyCode = buildJavaMethod("temp") { setBody(bodyBuilder) }.code().toString()
        if (inline) {
            add("(${parameters.format}) -> { ") { parameters.forEach { +it } }
            builder.add(bodyCode.replace('\n', ' ').trim())
            builder.add(" }")
        } else {
            beginControlFlow("(${parameters.format}) ->") { parameters.forEach { +it } }
            builder.add(bodyCode)
            endControlFlow()
        }
    }

    fun IrJavaCodeBlock.lambda_(
        parameters: List<IrParameter> = emptyList(),
        expression: JPCodeBlock
    ) {
        add("(${parameters.format}) -> %L") { parameters.forEach { +it }; +expression }
    }

    fun build(): JPCodeBlock = builder.build()

    private fun beginControlFlow(format: String, argumentsBuilder: Builder<Arguments> = {}) {
        builder.beginControlFlow(format.fixFormat(), *argumentsBuilder.build())
    }

    private fun endControlFlow(newLine: Boolean = false) {
        if (newLine) {
            builder.endControlFlow()
        } else {
            builder.unindent()
            builder.add("}")
        }
    }

    private fun String.fixFormat(): String = replace('%', '$')

    private fun Builder<Arguments>.build(): Array<Any> = Arguments().apply(this).build()

    @JvmInline
    value class Arguments(private val arguments: MutableList<Any> = mutableListOf()) {

        operator fun String.unaryPlus() {
            arguments += this
        }

        operator fun Int.invoke() {
            arguments += this
        }

        operator fun Boolean.unaryPlus() {
            arguments += this
        }

        operator fun JPCodeBlock.unaryPlus() {
            arguments += this
        }

        operator fun JPAnnotation.unaryPlus() {
            arguments += this
        }

        operator fun JPField.unaryPlus() {
            arguments += this
        }

        operator fun JPMethod.unaryPlus() {
            arguments += this
        }

        operator fun KClass<*>.unaryPlus() {
            +this.asIrTypeName()
        }

        operator fun IrTypeName.unaryPlus() {
            arguments += this.java
        }

        operator fun IrParameter.unaryPlus() {
            arguments += buildJavaMethod(this.name)
        }

        operator fun JPEntity.invoke() {
            when (this) {
                is JPFieldEntity -> +field

                is JPMethodEntity -> {
                    +method; parameters.forEach { +it }
                }
            }
        }

        operator fun JPEntity.unaryPlus() {
            when (this) {
                is JPFieldEntity -> +field
                is JPMethodEntity -> +method
            }
        }

        fun build(): Array<Any> = arguments.toTypedArray()
    }
}

fun Boolean.toJavaCodeBlock(): JPCodeBlock = buildJavaCodeBlock("%L") { +this@toJavaCodeBlock }
fun Int.toJavaCodeBlock(): JPCodeBlock = buildJavaCodeBlock("%L") { this@toJavaCodeBlock() }
fun String.toJavaCodeBlock(asValue: Boolean = false): JPCodeBlock =
    buildJavaCodeBlock(if (asValue) "%S" else "%L") { +this@toJavaCodeBlock }

fun IrTypeName.toJavaCodeBlock(asClass: Boolean = false): JPCodeBlock =
    buildJavaCodeBlock(if (asClass) "%T.class" else "%T") { +this@toJavaCodeBlock }

fun JPField.toCodeBlock(): JPCodeBlock = buildJavaCodeBlock("%N") { +this@toCodeBlock }
fun JPEntity.toCodeBlock(asCall: Boolean = true): JPCodeBlock = buildJavaCodeBlock("%N") {
    if (asCall) this@toCodeBlock()
    else +this@toCodeBlock
}

fun JPAnnotation.toCodeBlock(): JPCodeBlock = buildJavaCodeBlock("%L") { +this@toCodeBlock }
