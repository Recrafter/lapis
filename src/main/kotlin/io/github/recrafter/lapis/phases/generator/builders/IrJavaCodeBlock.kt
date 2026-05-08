package io.github.recrafter.lapis.phases.generator.builders

import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import kotlin.reflect.KClass

@JvmInline
value class IrJavaCodeBlock(private val builder: JPCodeBlockBuilder) {

    fun add(format: String, argumentsBuilder: Builder<Arguments> = {}) {
        builder.add(
            format.replace('%', '$'),
            *Arguments().apply(argumentsBuilder).build().toTypedArray()
        )
    }

    fun build(): JPCodeBlock = builder.build()

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

        operator fun IrJavaMember.unaryPlus() {
            when (this) {
                is IrFieldMember -> +field

                is IrMethodMember -> {
                    +method; parameters.forEach { +it }
                }
            }
        }

        fun build(): List<Any> = arguments
    }
}

fun Boolean.toJavaCodeBlock(): JPCodeBlock = buildJavaCodeBlock("%L") { +this@toJavaCodeBlock }
fun Int.toJavaCodeBlock(): JPCodeBlock = buildJavaCodeBlock("%L") { this@toJavaCodeBlock() }
fun String.toJavaCodeBlock(asValue: Boolean = false): JPCodeBlock =
    buildJavaCodeBlock(if (asValue) "%S" else "%L") { +this@toJavaCodeBlock }

fun IrTypeName.toJavaCodeBlock(asClass: Boolean = false): JPCodeBlock =
    buildJavaCodeBlock(if (asClass) "%T.class" else "%T") { +this@toJavaCodeBlock }

fun JPField.toCodeBlock(): JPCodeBlock = buildJavaCodeBlock("%N") { +this@toCodeBlock }
fun JPAnnotation.toCodeBlock(): JPCodeBlock = buildJavaCodeBlock("%L") { +this@toCodeBlock }
