package io.github.recrafter.lapis.layers.generator.builders

import io.github.recrafter.lapis.extensions.kp.KPCodeBlock
import io.github.recrafter.lapis.extensions.kp.KPCodeBlockBuilder
import io.github.recrafter.lapis.extensions.kp.KPParameter
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName
import kotlin.reflect.KFunction

@JvmInline
value class IrKotlinCodeBlockBuilder(private val builder: KPCodeBlockBuilder) {

    fun add(format: String, arguments: Arguments.() -> Unit = {}) {
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

        fun arg(function: KFunction<*>) {
            arguments += function.name
        }

        fun arg(codeBlock: KPCodeBlock) {
            arguments += codeBlock
        }

        fun arg(parameter: KPParameter) {
            arguments += parameter
        }

        fun arg(typeName: IrTypeName) {
            arguments += typeName.kotlin
        }

        fun build(): List<Any> =
            arguments
    }
}
