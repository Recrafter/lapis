package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.extensions.common.Builder
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

inline fun <reified A : Annotation> JPClassBuilder.addAnnotation(builder: Builder<JPAnnotationBuilder> = {}) {
    addAnnotation(buildJavaAnnotation<A>(builder))
}

fun JPClassBuilder.addSuperInterface(typeName: IrTypeName) {
    addSuperinterface(typeName.java)
}
