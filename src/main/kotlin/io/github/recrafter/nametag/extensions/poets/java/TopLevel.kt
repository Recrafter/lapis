package io.github.recrafter.nametag.extensions.poets.java

import com.palantir.javapoet.*
import io.github.recrafter.nametag.accessors.processor.JavaType

fun buildJavaInterface(name: String, builder: TypeSpec.Builder.() -> Unit = {}): TypeSpec =
    TypeSpec.interfaceBuilder(name).apply(builder).build()

fun buildJavaMethod(name: String, builder: MethodSpec.Builder.() -> Unit = {}): MethodSpec =
    MethodSpec.methodBuilder(name).apply(builder).build()

fun buildJavaParameter(
    type: JavaType,
    name: String,
    builder: ParameterSpec.Builder.() -> Unit = {}
): ParameterSpec =
    ParameterSpec.builder(type, name).apply(builder).build()

inline fun <reified A : Annotation> buildJavaAnnotation(
    builder: AnnotationSpec.Builder.() -> Unit = {}
): AnnotationSpec =
    AnnotationSpec.builder(ClassName.get(A::class.java)).apply(builder).build()
