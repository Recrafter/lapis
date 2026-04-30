package io.github.recrafter.lapis.phases.generator.builders

import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
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

        fun arg(boolean: Boolean) {
            arguments += boolean
        }

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

fun Boolean.toKotlinCodeBlock(): KPCodeBlock = buildKotlinCodeBlock("%L") { arg(this@toKotlinCodeBlock) }
fun String.toKotlinCodeBlock(asValue: Boolean = false): KPCodeBlock =
    buildKotlinCodeBlock(if (asValue) "%S" else "%L") { arg(this@toKotlinCodeBlock) }

fun KPParameter.toCodeBlock(): KPCodeBlock = buildKotlinCodeBlock("%N") { arg(this@toCodeBlock) }
