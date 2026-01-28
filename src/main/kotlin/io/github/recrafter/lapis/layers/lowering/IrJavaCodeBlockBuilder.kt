package io.github.recrafter.lapis.layers.lowering

import io.github.recrafter.lapis.extensions.jp.JPAnnotation
import io.github.recrafter.lapis.extensions.jp.JPCodeBlock
import io.github.recrafter.lapis.extensions.jp.JPCodeBlockBuilder

@JvmInline
value class IrJavaCodeBlockBuilder(private val builder: JPCodeBlockBuilder) {

    fun add(format: String, arguments: Arguments.() -> Unit = {}) {
        builder.add(
            format.replace("%", "$"),
            *Arguments().apply(arguments).build().toTypedArray()
        )
    }

    fun add(codeBlock: JPCodeBlock) {
        builder.add(codeBlock)
    }

    fun build(): JPCodeBlock =
        builder.build()

    @JvmInline
    value class Arguments(private val arguments: MutableList<Any> = mutableListOf()) {

        fun arg(string: String) {
            arguments += string
        }

        fun arg(int: Int) {
            arguments += int
        }

        fun arg(codeBlock: JPCodeBlock) {
            arguments += codeBlock
        }

        fun arg(annotation: JPAnnotation) {
            arguments += annotation
        }

        fun arg(type: IrTypeName) {
            arguments += type.java
        }

        fun build(): List<Any> =
            arguments
    }
}
