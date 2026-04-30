package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.phases.generator.builders.Builder
import io.github.recrafter.lapis.phases.generator.builders.IrJavaCodeBlock
import io.github.recrafter.lapis.phases.generator.builders.toCodeBlock
import io.github.recrafter.lapis.phases.generator.builders.toJavaCodeBlock
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

fun <A : Annotation> JPAnnotationBuilder.setArgumentValue(property: KProperty1<A, Boolean>, boolean: Boolean) {
    addMember(property.name, boolean.toJavaCodeBlock())
}

fun <A : Annotation> JPAnnotationBuilder.setArgumentValue(property: KProperty1<A, Int>, int: Int) {
    addMember(property.name, int.toJavaCodeBlock())
}

fun <A : Annotation> JPAnnotationBuilder.setArgumentValue(property: KProperty1<A, String>, string: String) {
    addMember(property.name, string.toJavaCodeBlock(asValue = true))
}

fun <A : Annotation> JPAnnotationBuilder.setArgumentValue(property: KProperty1<A, KClass<*>>, className: IrTypeName) {
    addMember(property.name, className.toJavaCodeBlock(asValue = true))
}

inline fun <reified A : Annotation, reified Embedded : Annotation> JPAnnotationBuilder.setArgumentValue(
    property: KProperty1<A, Embedded>,
    crossinline builder: Builder<JPAnnotationBuilder> = {}
) {
    addMember(property.name, buildJavaAnnotation<Embedded>(builder).toCodeBlock())
}

@JvmName("setStringArrayArgumentValue")
inline fun <reified A : Annotation> JPAnnotationBuilder.setArgumentValue(
    property: KProperty1<A, Array<out String>>,
    vararg strings: String
) {
    setArrayArgumentValue(property, strings, "%S") {
        strings.forEach(::arg)
    }
}

@JvmName("setClassArrayArgumentValue")
inline fun <reified A : Annotation> JPAnnotationBuilder.setArgumentValue(
    property: KProperty1<A, Array<out KClass<*>>>,
    vararg classNames: IrClassName
) {
    setArrayArgumentValue(property, classNames, "%T.class") {
        classNames.forEach(::arg)
    }
}

@JvmName("setAnnotationArrayArgumentValue")
inline fun <reified A : Annotation, reified Embedded : Annotation> JPAnnotationBuilder.setArgumentValue(
    property: KProperty1<A, Array<out Embedded>>,
    crossinline builder: Builder<JPAnnotationBuilder> = {}
) {
    val annotations = arrayOf(buildJavaAnnotation<Embedded>(builder))
    setArrayArgumentValue(property, annotations, "%L") {
        annotations.forEach(::arg)
    }
}

inline fun <reified A : Annotation> JPAnnotationBuilder.setArrayArgumentValue(
    property: KProperty1<A, *>,
    array: Array<*>,
    placeholder: String,
    noinline arguments: Builder<IrJavaCodeBlock.Arguments> = {}
) {
    addMember(
        property.name,
        buildJavaCodeBlock(array.joinToString(prefix = "{", postfix = "}") { placeholder }, arguments)
    )
}
