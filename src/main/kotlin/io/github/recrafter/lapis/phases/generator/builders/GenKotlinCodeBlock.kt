package io.github.recrafter.lapis.phases.generator.builders

import io.github.recrafter.lapis.extensions.common.Builder
import io.github.recrafter.lapis.extensions.jp.JPField
import io.github.recrafter.lapis.extensions.jp.JPMethod
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import kotlin.reflect.KCallable

@JvmInline
value class IrKotlinCodeBlock(private val builder: KPCodeBlockBuilder) {

    fun add(format: String, argumentsBuilder: Builder<Arguments> = {}) {
        builder.add(format, *argumentsBuilder.build())
    }

    fun build(): KPCodeBlock = builder.build()

    private fun Builder<Arguments>.build(): Array<Any> = Arguments().apply(this).build()

    @JvmInline
    value class Arguments(private val arguments: MutableList<Any> = mutableListOf()) {

        operator fun Boolean.unaryPlus() {
            arguments += this
        }

        operator fun String.unaryPlus() {
            arguments += this
        }

        operator fun KPCodeBlock.unaryPlus() {
            arguments += this
        }

        operator fun KPParameter.unaryPlus() {
            arguments += this
        }

        operator fun KPProperty.unaryPlus() {
            arguments += this
        }

        operator fun KPFunction.unaryPlus() {
            arguments += this
        }

        operator fun GenKotlinEntity.invoke() {
            when (this) {
                is GenKotlinPropertyEntity -> +property

                is GenKotlinFunctionEntity -> {
                    +function; parameters.forEach { +it }
                }
            }
        }

        operator fun GenKotlinEntity.unaryPlus() {
            when (this) {
                is GenKotlinPropertyEntity -> +property
                is GenKotlinFunctionEntity -> +function
            }
        }

        operator fun JPField.unaryPlus() {
            asName(name())
        }

        operator fun JPMethod.unaryPlus() {
            asName(name())
        }

        operator fun GenJavaEntity.invoke() {
            when (this) {
                is GenJavaFieldEntity -> +field

                is GenJavaMethodEntity -> {
                    +method; parameters.forEach { +it }
                }
            }
        }

        operator fun GenJavaEntity.unaryPlus() {
            when (this) {
                is GenJavaFieldEntity -> +field
                is GenJavaMethodEntity -> +method
            }
        }

        operator fun KCallable<*>.unaryPlus() {
            arguments += name
        }

        operator fun IrTypeName.unaryPlus() {
            arguments += kotlin
        }

        operator fun IrParameter.unaryPlus() {
            asName(name)
        }

        fun build(): Array<Any> = arguments.toTypedArray()

        private fun asName(name: String) {
            arguments += buildKotlinFunction(name)
        }
    }
}

fun Boolean.toKotlinCodeBlock(): KPCodeBlock = buildKotlinCodeBlock("%L") { +this@toKotlinCodeBlock }
fun IrParameter.toKotlinCodeBlock(): KPCodeBlock = buildKotlinCodeBlock("%N") { +this@toKotlinCodeBlock }

val nullKotlinCodeBlock: KPCodeBlock = buildKotlinCodeBlock("null")
