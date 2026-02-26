package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.layers.generator.IrKotlinCodeBlockBuilder
import io.github.recrafter.lapis.layers.lowering.IrClassName
import io.github.recrafter.lapis.layers.lowering.IrTypeName

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

typealias KPTypeBuilder = com.squareup.kotlinpoet.TypeSpec.Builder
typealias KPType = com.squareup.kotlinpoet.TypeSpec

typealias KPTypeAliasBuilder = com.squareup.kotlinpoet.TypeAliasSpec.Builder
typealias KPTypeAlias = com.squareup.kotlinpoet.TypeAliasSpec

typealias KPFileBuilder = com.squareup.kotlinpoet.FileSpec.Builder
typealias KPFile = com.squareup.kotlinpoet.FileSpec

typealias KPTypeName = com.squareup.kotlinpoet.TypeName
typealias KPClassName = com.squareup.kotlinpoet.ClassName
typealias KPParameterizedTypeName = com.squareup.kotlinpoet.ParameterizedTypeName

typealias KPModifier = com.squareup.kotlinpoet.KModifier

val KPNothing: KPClassName = KPClassName("kotlin", "Nothing")

val KPUnit: KPClassName = com.squareup.kotlinpoet.UNIT
val KPBoolean: KPClassName = com.squareup.kotlinpoet.BOOLEAN
val KPByte: KPClassName = com.squareup.kotlinpoet.BYTE
val KPShort: KPClassName = com.squareup.kotlinpoet.SHORT
val KPInt: KPClassName = com.squareup.kotlinpoet.INT
val KPLong: KPClassName = com.squareup.kotlinpoet.LONG
val KPChar: KPClassName = com.squareup.kotlinpoet.CHAR
val KPFloat: KPClassName = com.squareup.kotlinpoet.FLOAT
val KPDouble: KPClassName = com.squareup.kotlinpoet.DOUBLE

val KPAny: KPClassName = com.squareup.kotlinpoet.ANY
val KPString: KPClassName = com.squareup.kotlinpoet.STRING
val KPList: KPClassName = com.squareup.kotlinpoet.LIST
val KPSet: KPClassName = com.squareup.kotlinpoet.SET
val KPMap: KPClassName = com.squareup.kotlinpoet.MAP

inline fun <reified A : Annotation> buildKotlinAnnotation(builder: KPAnnotationBuilder.() -> Unit = {}): KPAnnotation =
    KPAnnotation.builder(A::class).apply(builder).build()

fun buildKotlinCodeBlock(builder: IrKotlinCodeBlockBuilder.() -> Unit = {}): KPCodeBlock =
    IrKotlinCodeBlockBuilder(KPCodeBlock.builder()).apply(builder).build()

fun buildKotlinCodeBlock(
    format: String,
    arguments: IrKotlinCodeBlockBuilder.KotlinCodeBlockArguments.() -> Unit = {}
): KPCodeBlock =
    buildKotlinCodeBlock {
        add(format, arguments)
    }

fun buildKotlinCast(from: KPCodeBlock, to: IrClassName): KPCodeBlock =
    buildKotlinCodeBlock("%L as %T") {
        arg(from)
        arg(to)
    }

fun buildKotlinProperty(name: String, type: IrTypeName, builder: KPPropertyBuilder.() -> Unit = {}): KPProperty =
    KPProperty.builder(name, type.kotlin).apply(builder).build()

fun buildKotlinGetter(builder: KPFunctionBuilder.() -> Unit = {}): KPFunction =
    KPFunction.getterBuilder().apply(builder).build()

fun buildKotlinSetter(builder: KPFunctionBuilder.() -> Unit = {}): KPFunction =
    KPFunction.setterBuilder().apply(builder).build()

fun buildKotlinFunction(name: String, builder: KPFunctionBuilder.() -> Unit = {}): KPFunction =
    KPFunction.builder(name).apply(builder).build()

fun buildKotlinParameter(
    name: String,
    type: IrTypeName,
    builder: KPParameterBuilder.() -> Unit = {}
): KPParameter =
    KPParameter.builder(name, type.kotlin).apply(builder).build()

fun buildKotlinTypeAlias(
    name: String,
    type: IrClassName,
    builder: KPTypeAliasBuilder.() -> Unit = {}
): KPTypeAlias =
    KPTypeAlias.builder(name, type.kotlin).apply(builder).build()

fun buildKotlinInterface(name: String, builder: KPTypeBuilder.() -> Unit = {}): KPType =
    KPType.interfaceBuilder(name).apply(builder).build()

fun buildKotlinObject(name: String, builder: KPTypeBuilder.() -> Unit = {}): KPType =
    KPType.objectBuilder(name).apply(builder).build()

fun buildKotlinConstructor(builder: KPFunctionBuilder.() -> Unit = {}): KPFunction =
    KPFunction.constructorBuilder().apply(builder).build()

fun buildKotlinClass(name: String, builder: KPTypeBuilder.() -> Unit = {}): KPType =
    KPType.classBuilder(name).apply(builder).build()

fun buildKotlinFile(packageName: String, name: String, builder: KPFileBuilder.() -> Unit = {}): KPFile =
    KPFile.builder(packageName, name).apply(builder).indent("    ").build()

fun buildKotlinFile(className: IrClassName, builder: KPFileBuilder.() -> Unit = {}): KPFile =
    buildKotlinFile(className.packageName, className.name, builder)
