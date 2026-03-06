package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.layers.generator.IrJavaCodeBlockBuilder
import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName

typealias JPCodeBlockBuilder = com.palantir.javapoet.CodeBlock.Builder
typealias JPCodeBlock = com.palantir.javapoet.CodeBlock

typealias JPAnnotationBuilder = com.palantir.javapoet.AnnotationSpec.Builder
typealias JPAnnotation = com.palantir.javapoet.AnnotationSpec

typealias JPFieldBuilder = com.palantir.javapoet.FieldSpec.Builder
typealias JPField = com.palantir.javapoet.FieldSpec

typealias JPMethodBuilder = com.palantir.javapoet.MethodSpec.Builder
typealias JPMethod = com.palantir.javapoet.MethodSpec

typealias JPParameterBuilder = com.palantir.javapoet.ParameterSpec.Builder
typealias JPParameter = com.palantir.javapoet.ParameterSpec

typealias JPTypeBuilder = com.palantir.javapoet.TypeSpec.Builder
typealias JPType = com.palantir.javapoet.TypeSpec

typealias JPFile = com.palantir.javapoet.JavaFile

typealias JPTypeName = com.palantir.javapoet.TypeName
typealias JPClassName = com.palantir.javapoet.ClassName
typealias JPParameterizedTypeName = com.palantir.javapoet.ParameterizedTypeName
typealias JPWildcardTypeName = com.palantir.javapoet.WildcardTypeName
typealias JPTypeVariableName = com.palantir.javapoet.TypeVariableName
typealias JPArrayTypeName = com.palantir.javapoet.ArrayTypeName

typealias JPModifier = javax.lang.model.element.Modifier

val JPVoid: JPTypeName = JPTypeName.VOID
val JPBoolean: JPTypeName = JPTypeName.BOOLEAN
val JPByte: JPTypeName = JPTypeName.BYTE
val JPShort: JPTypeName = JPTypeName.SHORT
val JPInt: JPTypeName = JPTypeName.INT
val JPLong: JPTypeName = JPTypeName.LONG
val JPChar: JPTypeName = JPTypeName.CHAR
val JPFloat: JPTypeName = JPTypeName.FLOAT
val JPDouble: JPTypeName = JPTypeName.DOUBLE

val JPObject: JPClassName = JPClassName.OBJECT
val JPString: JPClassName = JPClassName.get(String::class.java)
val JPList: JPClassName = JPClassName.get(List::class.java)
val JPSet: JPClassName = JPClassName.get(Set::class.java)
val JPMap: JPClassName = JPClassName.get(Map::class.java)

val JPStar: JPWildcardTypeName = JPWildcardTypeName.subtypeOf(JPObject)

inline fun <reified A : Annotation> buildJavaAnnotation(builder: JPAnnotationBuilder.() -> Unit = {}): JPAnnotation =
    JPAnnotation.builder(JPClassName.get(A::class.java)).apply(builder).build()

fun buildJavaCodeBlock(builder: IrJavaCodeBlockBuilder.() -> Unit = {}): JPCodeBlock =
    IrJavaCodeBlockBuilder(JPCodeBlock.builder()).apply(builder).build()

fun buildJavaCodeBlock(
    format: String,
    arguments: IrJavaCodeBlockBuilder.Arguments.() -> Unit = {}
): JPCodeBlock =
    buildJavaCodeBlock {
        add(format, arguments)
    }

fun buildJavaField(name: String, type: IrTypeName, builder: JPFieldBuilder.() -> Unit = {}): JPField =
    JPField.builder(type.java, name).apply(builder).build()

fun buildJavaMethod(name: String, builder: JPMethodBuilder.() -> Unit = {}): JPMethod =
    JPMethod.methodBuilder(name).apply(builder).build()

fun buildJavaParameter(name: String, type: IrTypeName, builder: JPParameterBuilder.() -> Unit = {}): JPParameter =
    JPParameter.builder(type.java, name).apply(builder).build()

fun buildJavaInterface(name: String, builder: JPTypeBuilder.() -> Unit = {}): JPType =
    JPType.interfaceBuilder(name).apply(builder).build()

fun buildJavaClass(name: String, builder: JPTypeBuilder.() -> Unit = {}): JPType =
    JPType.classBuilder(name).apply(builder).build()

fun buildJavaFile(className: IrClassName, builder: () -> JPType): JPFile =
    JPFile.builder(className.packageName, builder()).indent("    ").skipJavaLangImports(true).build()
