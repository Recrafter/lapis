package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.layers.lowering.IrTypeName

inline fun <reified A : Annotation> JPTypeBuilder.addAnnotation(builder: JPAnnotationBuilder.() -> Unit = {}) {
    addAnnotation(buildJavaAnnotation<A>(builder))
}

fun JPTypeBuilder.addSuperInterface(type: IrTypeName) {
    addSuperinterface(type.java)
}
