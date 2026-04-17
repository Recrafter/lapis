package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.phases.generator.builders.Builder
import io.github.recrafter.lapis.phases.generator.builders.IrKotlinCodeBlock
import kotlin.reflect.KProperty1

fun <A : Annotation> KPAnnotationBuilder.setStringMember(property: KProperty1<A, String>, string: String) {
    addMember(buildKotlinCodeBlock("%L = %S") {
        arg(property.name)
        arg(string)
    })
}

inline fun <reified A : Annotation> KPAnnotationBuilder.setStringVarargMember(
    property: KProperty1<A, Array<out String>>,
    vararg strings: String,
) {
    setArrayMember<A>(property, strings, "%S", isVararg = true) {
        strings.forEach { arg(it) }
    }
}

inline fun <reified A : Annotation> KPAnnotationBuilder.setArrayMember(
    property: KProperty1<A, Array<*>>,
    array: Array<*>,
    placeholder: String,
    isVararg: Boolean = false,
    noinline arguments: Builder<IrKotlinCodeBlock.Arguments> = {}
) {
    addMember(buildKotlinCodeBlock(buildString {
        if (isVararg) {
            append(array.joinToString { placeholder })
        } else {
            append("%L = ")
            append(array.joinToString(prefix = "[", postfix = "]") { placeholder })
        }
    }) {
        if (!isVararg) {
            arg(property.name)
        }
        arguments()
    })
}
