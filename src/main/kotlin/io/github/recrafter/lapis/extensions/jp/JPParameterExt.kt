package io.github.recrafter.lapis.extensions.jp

inline fun <reified A : Annotation> JPParameterBuilder.addAnnotation(builder: JPAnnotationBuilder.() -> Unit = {}) {
    addAnnotation(buildJavaAnnotation<A>(builder))
}
