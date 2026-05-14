package io.github.recrafter.lapis.phases.generator.builders

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

        operator fun KCallable<*>.unaryPlus() {
            arguments += this.name
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

        operator fun KPEntity.invoke() {
            when (this) {
                is KPPropertyEntity -> +property

                is KPFunctionEntity -> {
                    +function; parameters.forEach { +it }
                }
            }
        }

        operator fun KPEntity.unaryPlus() {
            when (this) {
                is KPPropertyEntity -> +property
                is KPFunctionEntity -> +function
            }
        }

        operator fun IrTypeName.unaryPlus() {
            arguments += this.kotlin
        }

        operator fun IrParameter.unaryPlus() {
            arguments += buildKotlinFunction(this.name)
        }

        operator fun JPField.unaryPlus() {
            arguments += this
        }

        operator fun JPMethod.unaryPlus() {
            arguments += buildKotlinFunction(this.name())
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

fun Boolean.toKotlinCodeBlock(): KPCodeBlock = buildKotlinCodeBlock("%L") { +this@toKotlinCodeBlock }
fun IrParameter.toKotlinCodeBlock(): KPCodeBlock = buildKotlinCodeBlock("%N") { +this@toKotlinCodeBlock }
