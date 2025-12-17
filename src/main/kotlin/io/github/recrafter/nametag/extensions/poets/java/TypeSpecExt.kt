package io.github.recrafter.nametag.extensions.poets.java

import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.TypeSpec

inline fun <reified A : Annotation> TypeSpec.Builder.addAnnotation(builder: AnnotationSpec.Builder.() -> Unit = {}) {
    addAnnotation(buildJavaAnnotation<A>(builder))
}

fun TypeSpec.buildJavaFile(packageName: String): JavaFile =
    JavaFile.builder(packageName, this).indent("    ").build()
