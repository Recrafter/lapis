package io.github.recrafter.lapis.layers.generator

import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.layers.lowering.IrTypeName

@JvmInline
value class IrKotlinCodeBlockBuilder(private val builder: KPCodeBlockBuilder) {

    fun add(format: String, arguments: KotlinCodeBlockArguments.() -> Unit = {}) {
        builder.add(
            format,
            *KotlinCodeBlockArguments().apply(arguments).build().toTypedArray()
        )
    }

    fun add(codeBlock: KPCodeBlock) {
        builder.add(codeBlock)
    }

    fun build(): KPCodeBlock =
        builder.build()

    @JvmInline
    value class KotlinCodeBlockArguments(private val arguments: MutableList<Any> = mutableListOf()) {

        fun arg(string: String) {
            arguments += string
        }

        fun arg(int: Int) {
            arguments += int
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

        fun arg(type: IrTypeName) {
            arguments += type.kotlin
        }

        fun build(): List<Any> =
            arguments
    }
}
