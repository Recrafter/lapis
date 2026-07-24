package io.github.recrafter.lapis.extensions.jp

import io.github.diskria.poetesse.java.JPAnnotationBuilder
import io.github.diskria.poetesse.java.JPParameterBuilder
import io.github.recrafter.lapis.extensions.common.Builder

inline fun <reified A : Annotation> JPParameterBuilder.addAnnotation(builder: Builder<JPAnnotationBuilder> = {}) {
    addAnnotation(buildJavaAnnotation<A>(builder))
}
