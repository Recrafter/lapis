package io.github.recrafter.lapis.extensions.jp

import io.github.diskria.poetesse.java.JPAnnotationBuilder
import io.github.diskria.poetesse.java.JPTypeBuilder
import io.github.recrafter.lapis.extensions.common.Builder
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

inline fun <reified A : Annotation> JPTypeBuilder.addAnnotation(builder: Builder<JPAnnotationBuilder> = {}) {
    addAnnotation(buildJavaAnnotation<A>(builder))
}

fun JPTypeBuilder.addSuperInterface(typeName: IrTypeName) {
    addSuperinterface(typeName.java)
}
