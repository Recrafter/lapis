package io.github.recrafter.nametag.extensions.poets.java

import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.MethodSpec

inline fun <reified A : Annotation> MethodSpec.Builder.addAnnotation(builder: AnnotationSpec.Builder.() -> Unit = {}) {
    addAnnotation(buildJavaAnnotation<A>(builder))
}

fun MethodSpec.Builder.addStubStatement() {
    addStatement("throw new ${AssertionError::class.simpleName}()")
}
