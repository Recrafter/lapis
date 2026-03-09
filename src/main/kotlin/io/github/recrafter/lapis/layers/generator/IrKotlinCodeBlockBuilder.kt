package io.github.recrafter.lapis.layers.generator

import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.layers.lowering.types.IrType
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

        fun arg(annotation: KPAnnotation) {
            arguments += annotation
        }

        fun arg(parameter: KPParameter) {
            arguments += parameter
        }

        fun arg(property: KPProperty) {
            arguments += property
        }

        fun arg(type: IrType) {
            arguments += type.kotlin
        }

        fun build(): List<Any> =
            arguments
    }
}
