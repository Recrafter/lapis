package io.github.recrafter.lapis.phases.generator.builders

import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import kotlin.reflect.KClass

@JvmInline
value class IrJavaCodeBlock(private val builder: JPCodeBlockBuilder) {

    fun add(format: String, arguments: Builder<Arguments> = {}) {
        builder.add(
            format.replace('%', '$'),
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

        fun arg(boolean: Boolean) {
            arguments += boolean
        }

        fun arg(codeBlock: JPCodeBlock) {
            arguments += codeBlock
        }

        fun arg(annotation: JPAnnotation) {
            arguments += annotation
        }

        fun arg(field: JPField) {
            arguments += field
        }

        fun arg(method: JPMethod) {
            arguments += method
        }

        fun arg(kClass: KClass<*>) {
            arg(kClass.asIrTypeName())
        }

        fun arg(typeName: IrTypeName) {
            arguments += typeName.java
        }

        fun arg(parameter: IrParameter) {
            arguments += buildJavaMethod(parameter.name)
        }

        fun build(): List<Any> =
            arguments
    }
}

fun Boolean.toJavaCodeBlock(): JPCodeBlock = buildJavaCodeBlock("%L") { arg(this@toJavaCodeBlock) }
fun Int.toJavaCodeBlock(): JPCodeBlock = buildJavaCodeBlock("%L") { arg(this@toJavaCodeBlock) }
fun String.toJavaCodeBlock(asValue: Boolean = false): JPCodeBlock =
    buildJavaCodeBlock(if (asValue) "%S" else "%L") { arg(this@toJavaCodeBlock) }

fun IrTypeName.toJavaCodeBlock(asValue: Boolean = false): JPCodeBlock =
    buildJavaCodeBlock(if (asValue) "%T.class" else "%T") { arg(this@toJavaCodeBlock) }

fun JPField.toCodeBlock(): JPCodeBlock = buildJavaCodeBlock("%N") { arg(this@toCodeBlock) }
fun JPAnnotation.toCodeBlock(): JPCodeBlock = buildJavaCodeBlock("%L") { arg(this@toCodeBlock) }
