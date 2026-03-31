package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.layers.generator.builders.Builder
import io.github.recrafter.lapis.layers.generator.builders.IrJavaCodeBlock
import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

inline fun <reified A : Annotation> JPAnnotationBuilder.setIntMember(property: KProperty1<A, Int>, int: Int) {
    addMember(property.name, buildJavaCodeBlock("%L") { arg(int) })
}

fun <A : Annotation> JPAnnotationBuilder.setBooleanMember(property: KProperty1<A, Boolean>, boolean: Boolean) {
    addMember(property.name, buildJavaCodeBlock("%L") { arg(boolean) })
}

fun <A : Annotation> JPAnnotationBuilder.setStringMember(property: KProperty1<A, String>, string: String) {
    addMember(property.name, buildJavaCodeBlock("%S") { arg(string) })
}

inline fun <reified O : Annotation, reified I : Annotation> JPAnnotationBuilder.setAnnotationMember(
    property: KProperty1<O, I>,
    crossinline builder: Builder<JPAnnotationBuilder> = {},
) {
    addMember(property.name, buildJavaCodeBlock("%L") { arg(buildJavaAnnotation<I>(builder)) })
}

inline fun <reified A : Annotation> JPAnnotationBuilder.setStringArrayMember(
    property: KProperty1<A, Array<String>>,
    vararg strings: String
) {
    setArrayMember(property, strings, "%S") {
        strings.forEach { arg(it) }
    }
}

inline fun <reified A : Annotation> JPAnnotationBuilder.setClassArrayMember(
    property: KProperty1<A, Array<KClass<*>>>,
    vararg classNames: IrClassName
) {
    setArrayMember(property, classNames, "%T.class") {
        classNames.forEach { arg(it) }
    }
}

inline fun <reified O : Annotation, reified I : Annotation> JPAnnotationBuilder.setAnnotationArrayMember(
    property: KProperty1<O, Array<I>>,
    vararg annotations: JPAnnotation,
) {
    setArrayMember(property, annotations, "%L") {
        annotations.forEach { arg(it) }
    }
}

inline fun <reified O : Annotation, reified I : Annotation> JPAnnotationBuilder.setAnnotationArrayMember(
    property: KProperty1<O, Array<I>>,
    crossinline builder: Builder<JPAnnotationBuilder> = {},
) {
    setAnnotationArrayMember(property, buildJavaAnnotation<I>(builder))
}

inline fun <reified A : Annotation> JPAnnotationBuilder.setArrayMember(
    property: KProperty1<A, Array<*>>,
    array: Array<*>,
    placeholder: String,
    noinline arguments: Builder<IrJavaCodeBlock.Arguments> = {}
) {
    addMember(
        property.name,
        buildJavaCodeBlock(array.joinToString(prefix = "{", postfix = "}") { placeholder }, arguments)
    )
}
