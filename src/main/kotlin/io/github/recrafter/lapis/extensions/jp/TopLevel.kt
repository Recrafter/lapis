package io.github.recrafter.lapis.extensions.jp

import com.palantir.javapoet.*
import io.github.recrafter.lapis.phases.generator.GeneratorConstants
import io.github.recrafter.lapis.phases.generator.builders.Builder
import io.github.recrafter.lapis.phases.generator.builders.IrJavaCodeBlock
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

typealias JPCodeBlockBuilder = CodeBlock.Builder
typealias JPCodeBlock = CodeBlock

typealias JPAnnotationBuilder = AnnotationSpec.Builder
typealias JPAnnotation = AnnotationSpec

typealias JPFieldBuilder = FieldSpec.Builder
typealias JPField = FieldSpec

typealias JPMethodBuilder = MethodSpec.Builder
typealias JPMethod = MethodSpec

typealias JPParameterBuilder = ParameterSpec.Builder
typealias JPParameter = ParameterSpec

typealias JPClassBuilder = TypeSpec.Builder
typealias JPClass = TypeSpec

typealias JPFile = JavaFile

typealias JPTypeName = TypeName
typealias JPClassName = ClassName
typealias JPParameterizedTypeName = ParameterizedTypeName
typealias JPWildcardTypeName = WildcardTypeName
typealias JPTypeVariableName = TypeVariableName
typealias JPArrayTypeName = ArrayTypeName

typealias JPModifier = javax.lang.model.element.Modifier

val JPBoolean: JPTypeName = JPTypeName.BOOLEAN
val JPByte: JPTypeName = JPTypeName.BYTE
val JPShort: JPTypeName = JPTypeName.SHORT
val JPInt: JPTypeName = JPTypeName.INT
val JPLong: JPTypeName = JPTypeName.LONG
val JPChar: JPTypeName = JPTypeName.CHAR
val JPFloat: JPTypeName = JPTypeName.FLOAT
val JPDouble: JPTypeName = JPTypeName.DOUBLE
val JPVoid: JPTypeName = JPTypeName.VOID

val JPObject: JPClassName = JPClassName.OBJECT
val JPString: JPClassName = JPClassName.get(String::class.java)
val JPList: JPClassName = JPClassName.get(List::class.java)
val JPSet: JPClassName = JPClassName.get(Set::class.java)
val JPMap: JPClassName = JPClassName.get(Map::class.java)

inline fun <reified A : Annotation> buildJavaAnnotation(builder: Builder<JPAnnotationBuilder> = {}): JPAnnotation =
    JPAnnotation.builder(JPClassName.get(A::class.java)).apply(builder).build()

fun buildJavaCodeBlock(builder: Builder<IrJavaCodeBlock> = {}): JPCodeBlock =
    IrJavaCodeBlock(JPCodeBlock.builder()).apply(builder).build()

fun buildJavaCodeBlock(format: String, arguments: Builder<IrJavaCodeBlock.Arguments> = {}): JPCodeBlock =
    buildJavaCodeBlock {
        add(format, arguments)
    }

fun buildJavaField(name: String, typeName: IrTypeName, builder: Builder<JPFieldBuilder> = {}): JPField =
    JPField.builder(typeName.java, name).apply(builder).build()

fun buildJavaMethod(name: String, builder: Builder<JPMethodBuilder> = {}): JPMethod =
    JPMethod.methodBuilder(name).apply(builder).build()

fun buildJavaParameter(name: String, typeName: IrTypeName, builder: Builder<JPParameterBuilder> = {}): JPParameter =
    JPParameter.builder(typeName.java, name).apply(builder).build()

fun buildJavaInterface(name: String, builder: Builder<JPClassBuilder> = {}): JPClass =
    JPClass.interfaceBuilder(name).apply(builder).build()

fun buildJavaClass(name: String, builder: Builder<JPClassBuilder> = {}): JPClass =
    JPClass.classBuilder(name).apply(builder).build()

fun buildJavaFile(className: IrClassName, builder: () -> JPClass): JPFile =
    JPFile
        .builder(className.packageName, builder())
        .addFileComment(GeneratorConstants.GENERATED_HEADER)
        .indent(GeneratorConstants.INDENT)
        .build()
