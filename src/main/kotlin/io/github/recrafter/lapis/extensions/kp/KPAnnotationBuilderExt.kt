package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.phases.generator.builders.Builder
import io.github.recrafter.lapis.phases.generator.builders.IrKotlinCodeBlock
import kotlin.reflect.KProperty1

fun <A : Annotation> KPAnnotationBuilder.setArgumentValue(property: KProperty1<A, String>, string: String) {
    addMember(buildKotlinCodeBlock("%L = %S") {
        arg(property.name)
        arg(string)
    })
}

@JvmName("setStringArrayArgumentValue")
inline fun <reified A : Annotation> KPAnnotationBuilder.setArgumentValue(
    property: KProperty1<A, Array<String>>,
    vararg strings: String,
) {
    setArrayArgumentValue<A>(property, strings, "%S", false) {
        strings.forEach(::arg)
    }
}

@JvmName("setStringVarargArgumentValue")
inline fun <reified A : Annotation> KPAnnotationBuilder.setArgumentValue(
    property: KProperty1<A, Array<out String>>,
    vararg strings: String,
) {
    setArrayArgumentValue<A>(property, strings, "%S", true) {
        strings.forEach(::arg)
    }
}

inline fun <reified A : Annotation> KPAnnotationBuilder.setArrayArgumentValue(
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
