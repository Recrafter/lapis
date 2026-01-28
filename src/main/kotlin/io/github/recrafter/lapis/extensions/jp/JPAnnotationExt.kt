package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.layers.lowering.IrClassName

fun JPAnnotationBuilder.addIntMember(name: String, int: Int) {
    addMember(name, buildJavaCodeBlock("%L") { arg(int) })
}

fun JPAnnotationBuilder.addStringMember(name: String, string: String) {
    addMember(name, buildJavaCodeBlock("%S") { arg(string) })
}

fun JPAnnotationBuilder.addClassMember(name: String, type: IrClassName) {
    addMember(name, buildJavaCodeBlock("%T.class") { arg(type) })
}

inline fun <reified A : Annotation> JPAnnotationBuilder.addAnnotationMember(
    name: String,
    crossinline builder: JPAnnotationBuilder.() -> Unit = {},
) {
    addMember(name, buildJavaCodeBlock("%L") { arg(buildJavaAnnotation<A>(builder)) })
}
