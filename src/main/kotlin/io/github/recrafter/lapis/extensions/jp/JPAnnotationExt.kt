package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.layers.generator.IrJavaCodeBlockBuilder
import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

fun <A : Annotation> JPAnnotationBuilder.setIntMember(property: KProperty1<A, Int>, int: Int) {
    addMember(property.name, buildJavaCodeBlock("%L") { arg(int) })
}

fun <A : Annotation> JPAnnotationBuilder.setBooleanMember(property: KProperty1<A, Boolean>, boolean: Boolean) {
    addMember(property.name, buildJavaCodeBlock("%L") { arg(boolean) })
}

fun <A : Annotation> JPAnnotationBuilder.setStringMember(property: KProperty1<A, String>, string: String) {
    addMember(property.name, buildJavaCodeBlock("%S") { arg(string) })
}

inline fun <reified A : Annotation> JPAnnotationBuilder.setStringArrayMember(
    property: KProperty1<A, Array<String>>,
    vararg strings: String
) {
    addArrayMember(property, strings, "%S") {
        strings.forEach { arg(it) }
    }
}

inline fun <reified A : Annotation> JPAnnotationBuilder.setClassArrayMember(
    property: KProperty1<A, Array<KClass<*>>>,
    vararg types: IrClassName
) {
    addArrayMember(property, types, "%T.class") {
        types.forEach { arg(it) }
    }
}

inline fun <reified R : Annotation, reified A : Annotation> JPAnnotationBuilder.setAnnotationArrayMember(
    property: KProperty1<R, Array<A>>,
    vararg annotations: JPAnnotation,
) {
    addArrayMember(property, annotations, "%L") {
        annotations.forEach { arg(it) }
    }
}

inline fun <reified O : Annotation, reified I : Annotation> JPAnnotationBuilder.setAnnotationArrayMember(
    property: KProperty1<O, Array<I>>,
    crossinline builder: JPAnnotationBuilder.() -> Unit = {},
) {
    setAnnotationArrayMember(property, buildJavaAnnotation<I>(builder))
}

inline fun <reified A : Annotation> JPAnnotationBuilder.addArrayMember(
    property: KProperty1<A, Array<*>>,
    array: Array<*>,
    placeholder: String,
    noinline arguments: IrJavaCodeBlockBuilder.Arguments.() -> Unit = {}
) {
    addMember(
        property.name,
        buildJavaCodeBlock(array.joinToString(prefix = "{", postfix = "}") { placeholder }, arguments)
    )
}
