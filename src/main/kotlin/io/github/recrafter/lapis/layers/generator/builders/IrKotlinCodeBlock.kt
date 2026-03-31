package io.github.recrafter.lapis.layers.generator.builders

import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.layers.lowering.models.IrParameter
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName
import kotlin.reflect.KCallable

@JvmInline
value class IrKotlinCodeBlock(private val builder: KPCodeBlockBuilder) {

    fun add(format: String, arguments: Builder<Arguments> = {}) {
        builder.add(
            format,
            *Arguments().apply(arguments).build().toTypedArray()
        )
    }

    fun add(codeBlock: KPCodeBlock) {
        builder.add(codeBlock)
    }

    fun build(): KPCodeBlock =
        builder.build()

    @JvmInline
    value class Arguments(private val arguments: MutableList<Any> = mutableListOf()) {

        fun arg(string: String) {
            arguments += string
        }

        fun arg(callable: KCallable<*>) {
            arguments += callable.name
        }

        fun arg(codeBlock: KPCodeBlock) {
            arguments += codeBlock
        }

        fun arg(parameter: KPParameter) {
            arguments += parameter
        }

        fun arg(property: KPProperty) {
            arguments += property
        }

        fun arg(function: KPFunction) {
            arguments += function
        }

        fun arg(typeName: IrTypeName) {
            arguments += typeName.kotlin
        }

        fun arg(parameter: IrParameter) {
            arguments += buildKotlinFunction(parameter.name)
        }

        fun build(): List<Any> =
            arguments
    }
}
