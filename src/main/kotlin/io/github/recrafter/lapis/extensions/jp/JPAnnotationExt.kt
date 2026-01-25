package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.kj.KJTypeName

fun JPAnnotationBuilder.addIntMember(name: String, value: Int) {
    addMember(name, "\$L", value)
}

fun JPAnnotationBuilder.addStringMember(name: String, value: String) {
    addMember(name, "\$S", value)
}

fun JPAnnotationBuilder.addClassMember(name: String, type: KJTypeName) {
    addMember(name, "\$T.class", type.javaVersion)
}

inline fun <reified A : Annotation> JPAnnotationBuilder.addAnnotationMember(
    name: String,
    builder: JPAnnotationBuilder.() -> Unit = {},
) {
    addMember(name, "\$L", buildJavaAnnotation<A>(builder))
}
