package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.phases.generator.builders.Builder
import io.github.recrafter.lapis.phases.generator.builders.IrKotlinCodeBlock
import kotlin.reflect.KProperty1

fun <A : Annotation> KPAnnotationBuilder.setArgumentValue(property: KProperty1<A, String>, string: String) {
    addMember(buildKotlinCodeBlock("%L = %S") { +property.name; +string })
}

@JvmName("setStringVarargArgumentValue")
inline fun <reified A : Annotation> KPAnnotationBuilder.setArgumentValue(
    property: KProperty1<A, Array<out String>>,
    vararg strings: String,
) {
    setArrayArgumentValue<A>(property, strings, "%S", true) {
        strings.forEach { +it }
    }
}

inline fun <reified A : Annotation> KPAnnotationBuilder.setArrayArgumentValue(
    property: KProperty1<A, Array<*>>,
    array: Array<*>,
    placeholder: String,
    isVararg: Boolean = false,
    noinline argumentsBuilder: Builder<IrKotlinCodeBlock.Arguments> = {}
) {
    val format = if (isVararg) {
        array.joinToString { placeholder }
    } else {
        "%L = " + array.joinToString(prefix = "[", postfix = "]") { placeholder }
    }
    addMember(buildKotlinCodeBlock(format) {
        if (!isVararg) +property.name
        argumentsBuilder()
    })
}
