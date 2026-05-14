package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.phases.generator.builders.Builder

inline fun <reified A : Annotation> JPParameterBuilder.addAnnotation(builder: Builder<JPAnnotationBuilder> = {}) {
    addAnnotation(buildJavaAnnotation<A>(builder))
}
