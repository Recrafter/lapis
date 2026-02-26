package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.layers.lowering.IrClassName
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

fun <A : Annotation> JPAnnotationBuilder.setStringArrayMember(
    property: KProperty1<A, Array<String>>,
    vararg strings: String
) {
    addMember(
        property.name,
        buildJavaCodeBlock(buildString {
            append("{")
            append(strings.joinToString { "%S" })
            append("}")
        }) {
            strings.forEach { arg(it) }
        }
    )
}

fun <A : Annotation> JPAnnotationBuilder.setClassMember(property: KProperty1<A, *>, type: IrClassName) {
    addMember(property.name, buildJavaCodeBlock("%T.class") { arg(type) })
}

inline fun <reified A : Annotation> JPAnnotationBuilder.addAnnotationMember(
    property: KProperty1<*, *>,
    crossinline builder: JPAnnotationBuilder.() -> Unit = {},
) {
    addMember(property.name, buildJavaCodeBlock("%L") { arg(buildJavaAnnotation<A>(builder)) })
}
