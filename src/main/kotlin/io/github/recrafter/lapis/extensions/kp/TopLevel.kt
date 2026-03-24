package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.LapisMeta
import io.github.recrafter.lapis.layers.generator.builders.IrKotlinCodeBlockBuilder
import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName

typealias KPAnnotationBuilder = com.squareup.kotlinpoet.AnnotationSpec.Builder
typealias KPAnnotation = com.squareup.kotlinpoet.AnnotationSpec

typealias KPCodeBlockBuilder = com.squareup.kotlinpoet.CodeBlock.Builder
typealias KPCodeBlock = com.squareup.kotlinpoet.CodeBlock

typealias KPPropertyBuilder = com.squareup.kotlinpoet.PropertySpec.Builder
typealias KPProperty = com.squareup.kotlinpoet.PropertySpec

typealias KPFunctionBuilder = com.squareup.kotlinpoet.FunSpec.Builder
typealias KPFunction = com.squareup.kotlinpoet.FunSpec

typealias KPParameterBuilder = com.squareup.kotlinpoet.ParameterSpec.Builder
typealias KPParameter = com.squareup.kotlinpoet.ParameterSpec

typealias KPClassBuilder = com.squareup.kotlinpoet.TypeSpec.Builder
typealias KPClass = com.squareup.kotlinpoet.TypeSpec

typealias KPFileBuilder = com.squareup.kotlinpoet.FileSpec.Builder
typealias KPFile = com.squareup.kotlinpoet.FileSpec

typealias KPType = com.squareup.kotlinpoet.TypeName
typealias KPClassType = com.squareup.kotlinpoet.ClassName
typealias KPGenericType = com.squareup.kotlinpoet.ParameterizedTypeName
typealias KPWildcardType = com.squareup.kotlinpoet.WildcardTypeName
typealias KPVariableType = com.squareup.kotlinpoet.TypeVariableName
typealias KPFunctionType = com.squareup.kotlinpoet.LambdaTypeName
typealias KPDynamicType = com.squareup.kotlinpoet.Dynamic

typealias KPModifier = com.squareup.kotlinpoet.KModifier

val KPNothing: KPClassType = KPClassType("kotlin", "Nothing")

val KPUnit: KPClassType = com.squareup.kotlinpoet.UNIT
val KPBoolean: KPClassType = com.squareup.kotlinpoet.BOOLEAN
val KPByte: KPClassType = com.squareup.kotlinpoet.BYTE
val KPShort: KPClassType = com.squareup.kotlinpoet.SHORT
val KPInt: KPClassType = com.squareup.kotlinpoet.INT
val KPLong: KPClassType = com.squareup.kotlinpoet.LONG
val KPChar: KPClassType = com.squareup.kotlinpoet.CHAR
val KPFloat: KPClassType = com.squareup.kotlinpoet.FLOAT
val KPDouble: KPClassType = com.squareup.kotlinpoet.DOUBLE

val KPAny: KPClassType = com.squareup.kotlinpoet.ANY
val KPString: KPClassType = com.squareup.kotlinpoet.STRING
val KPList: KPClassType = com.squareup.kotlinpoet.LIST
val KPSet: KPClassType = com.squareup.kotlinpoet.SET
val KPMap: KPClassType = com.squareup.kotlinpoet.MAP

val KPArray: KPClassType = com.squareup.kotlinpoet.ARRAY
val KPBooleanArray: KPClassType = com.squareup.kotlinpoet.BOOLEAN_ARRAY
val KPByteArray: KPClassType = com.squareup.kotlinpoet.BYTE_ARRAY
val KPShortArray: KPClassType = com.squareup.kotlinpoet.SHORT_ARRAY
val KPIntArray: KPClassType = com.squareup.kotlinpoet.INT_ARRAY
val KPLongArray: KPClassType = com.squareup.kotlinpoet.LONG_ARRAY
val KPCharArray: KPClassType = com.squareup.kotlinpoet.CHAR_ARRAY
val KPFloatArray: KPClassType = com.squareup.kotlinpoet.FLOAT_ARRAY
val KPDoubleArray: KPClassType = com.squareup.kotlinpoet.DOUBLE_ARRAY

val KPStar: KPWildcardType = com.squareup.kotlinpoet.STAR

inline fun <reified A : Annotation> buildKotlinAnnotation(builder: KPAnnotationBuilder.() -> Unit = {}): KPAnnotation =
    KPAnnotation.builder(A::class).apply(builder).build()

fun buildKotlinCodeBlock(builder: IrKotlinCodeBlockBuilder.() -> Unit = {}): KPCodeBlock =
    IrKotlinCodeBlockBuilder(KPCodeBlock.builder()).apply(builder).build()

fun buildKotlinCodeBlock(
    format: String,
    arguments: IrKotlinCodeBlockBuilder.Arguments.() -> Unit = {}
): KPCodeBlock =
    buildKotlinCodeBlock {
        add(format, arguments)
    }

fun buildKotlinProperty(name: String, typeName: IrTypeName, builder: KPPropertyBuilder.() -> Unit = {}): KPProperty =
    KPProperty.builder(name, typeName.kotlin).apply(builder).build()

fun buildKotlinGetter(builder: KPFunctionBuilder.() -> Unit = {}): KPFunction =
    KPFunction.getterBuilder().apply(builder).build()

fun buildKotlinSetter(builder: KPFunctionBuilder.() -> Unit = {}): KPFunction =
    KPFunction.setterBuilder().apply(builder).build()

fun buildKotlinFunction(name: String, builder: KPFunctionBuilder.() -> Unit = {}): KPFunction =
    KPFunction.builder(name).apply(builder).build()

fun buildKotlinParameter(
    name: String,
    typeName: IrTypeName,
    builder: KPParameterBuilder.() -> Unit = {}
): KPParameter =
    KPParameter.builder(name, typeName.kotlin).apply(builder).build()

fun buildKotlinInterface(name: String, builder: KPClassBuilder.() -> Unit = {}): KPClass =
    KPClass.interfaceBuilder(name).apply(builder).build()

fun buildKotlinConstructor(builder: KPFunctionBuilder.() -> Unit = {}): KPFunction =
    KPFunction.constructorBuilder().apply(builder).build()

fun buildKotlinClass(name: String, builder: KPClassBuilder.() -> Unit = {}): KPClass =
    KPClass.classBuilder(name).apply(builder).build()

fun buildKotlinObject(name: String, builder: KPClassBuilder.() -> Unit = {}): KPClass =
    KPClass.objectBuilder(name).apply(builder).build()

fun buildKotlinFile(packageName: String, name: String, builder: KPFileBuilder.() -> Unit = {}): KPFile =
    KPFile.builder(packageName, name)
        .addFileComment("Generated by ${LapisMeta.NAME}. Do not edit!")
        .apply(builder)
        .indent(" ".repeat(4))
        .build()

fun buildKotlinFile(className: IrClassName, builder: KPFileBuilder.() -> Unit = {}): KPFile =
    buildKotlinFile(className.packageName, className.nestedName, builder)
