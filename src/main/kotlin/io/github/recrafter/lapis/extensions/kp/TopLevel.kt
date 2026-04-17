package io.github.recrafter.lapis.extensions.kp

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.AnnotationSpec.UseSiteTarget
import com.squareup.kotlinpoet.jvm.jvmName
import io.github.recrafter.lapis.extensions.capitalize
import io.github.recrafter.lapis.phases.generator.GeneratorConstants
import io.github.recrafter.lapis.phases.generator.builders.Builder
import io.github.recrafter.lapis.phases.generator.builders.IrKotlinCodeBlock
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

typealias KPAnnotationBuilder = AnnotationSpec.Builder
typealias KPAnnotation = AnnotationSpec

typealias KPCodeBlockBuilder = CodeBlock.Builder
typealias KPCodeBlock = CodeBlock

typealias KPPropertyBuilder = PropertySpec.Builder
typealias KPProperty = PropertySpec

typealias KPFunctionBuilder = FunSpec.Builder
typealias KPFunction = FunSpec

typealias KPParameterBuilder = ParameterSpec.Builder
typealias KPParameter = ParameterSpec

typealias KPClassBuilder = TypeSpec.Builder
typealias KPClass = TypeSpec

typealias KPTypeAliasBuilder = TypeAliasSpec.Builder
typealias KPTypeAlias = TypeAliasSpec

typealias KPFileBuilder = FileSpec.Builder
typealias KPFile = FileSpec

typealias KPTypeName = TypeName
typealias KPClassName = ClassName
typealias KPParameterizedTypeName = ParameterizedTypeName
typealias KPWildcardTypeName = WildcardTypeName
typealias KPTypeVariableName = TypeVariableName
typealias KPLambdaTypeName = LambdaTypeName
typealias KPDynamic = Dynamic

typealias KPModifier = KModifier

val KPNothing: KPClassName = KPClassName("kotlin", "Nothing")

val KPUnit: KPClassName = UNIT
val KPBoolean: KPClassName = BOOLEAN
val KPByte: KPClassName = BYTE
val KPShort: KPClassName = SHORT
val KPInt: KPClassName = INT
val KPLong: KPClassName = LONG
val KPChar: KPClassName = CHAR
val KPFloat: KPClassName = FLOAT
val KPDouble: KPClassName = DOUBLE

val KPAny: KPClassName = ANY
val KPString: KPClassName = STRING
val KPList: KPClassName = LIST
val KPSet: KPClassName = SET
val KPMap: KPClassName = MAP

val KPArray: KPClassName = ARRAY
val KPBooleanArray: KPClassName = BOOLEAN_ARRAY
val KPByteArray: KPClassName = BYTE_ARRAY
val KPShortArray: KPClassName = SHORT_ARRAY
val KPIntArray: KPClassName = INT_ARRAY
val KPLongArray: KPClassName = LONG_ARRAY
val KPCharArray: KPClassName = CHAR_ARRAY
val KPFloatArray: KPClassName = FLOAT_ARRAY
val KPDoubleArray: KPClassName = DOUBLE_ARRAY

val KPStar: KPWildcardTypeName = STAR

inline fun <reified A : Annotation> buildKotlinAnnotation(
    useSiteTarget: UseSiteTarget? = null,
    builder: Builder<KPAnnotationBuilder> = {}
): KPAnnotation =
    KPAnnotation.builder(A::class).apply {
        useSiteTarget(useSiteTarget)
        builder()
    }.build()

fun buildKotlinCodeBlock(builder: Builder<IrKotlinCodeBlock> = {}): KPCodeBlock =
    IrKotlinCodeBlock(KPCodeBlock.builder()).apply(builder).build()

fun buildKotlinCodeBlock(
    format: String,
    arguments: Builder<IrKotlinCodeBlock.Arguments> = {}
): KPCodeBlock =
    buildKotlinCodeBlock {
        add(format, arguments)
    }

fun buildKotlinProperty(
    name: String,
    typeName: IrTypeName,
    jvmNamespace: IrClassName? = null,
    builder: KPPropertyBuilder.(propertyName: String) -> Unit = {}
): KPProperty {
    val initialBuilder = KPProperty.builder(name, typeName.kotlin).apply {
        builder(name)
    }
    val property = initialBuilder.build()
    if (jvmNamespace == null) {
        return property
    }
    val finalBuilder = property.toBuilder()
    val useSiteTargets = buildList {
        add(UseSiteTarget.GET)
        if (property.mutable) {
            add(UseSiteTarget.SET)
        }
    }
    useSiteTargets.forEach { useSiteTarget ->
        finalBuilder.addAnnotation<JvmName>(useSiteTarget) {
            setStringMember(
                JvmName::name,
                jvmNamespace.simpleName + "_" + useSiteTarget.name.lowercase() + name.capitalize()
            )
        }
    }
    return finalBuilder.build()
}

fun buildKotlinGetter(builder: Builder<KPFunctionBuilder> = {}): KPFunction =
    KPFunction.getterBuilder().apply(builder).build()

fun buildKotlinSetter(builder: Builder<KPFunctionBuilder> = {}): KPFunction =
    KPFunction.setterBuilder().apply(builder).build()

fun buildKotlinFunction(
    name: String,
    jvmNamespace: IrClassName? = null,
    builder: KPFunctionBuilder.(functionName: String) -> Unit = {}
): KPFunction =
    KPFunction.builder(name).apply {
        jvmNamespace?.simpleName?.let {
            jvmName(it + "_" + name)
        }
        builder(name)
    }.build()

fun buildKotlinParameter(
    name: String,
    typeName: IrTypeName,
    builder: Builder<KPParameterBuilder> = {}
): KPParameter =
    KPParameter.builder(name, typeName.kotlin).apply(builder).build()

fun buildKotlinParameter(parameter: IrParameter, builder: Builder<KPParameterBuilder> = {}): KPParameter =
    buildKotlinParameter(parameter.name, parameter.typeName, builder)

fun buildKotlinInterface(name: String, builder: Builder<KPClassBuilder> = {}): KPClass =
    KPClass.interfaceBuilder(name).apply(builder).build()

fun buildKotlinConstructor(builder: Builder<KPFunctionBuilder> = {}): KPFunction =
    KPFunction.constructorBuilder().apply(builder).build()

fun buildKotlinClass(name: String, builder: Builder<KPClassBuilder> = {}): KPClass =
    KPClass.classBuilder(name).apply(builder).build()

fun buildKotlinObject(name: String, builder: Builder<KPClassBuilder> = {}): KPClass =
    KPClass.objectBuilder(name).apply(builder).build()

fun buildKotlinTypeAlias(name: String, typeName: IrTypeName, builder: Builder<KPTypeAliasBuilder> = {}): KPTypeAlias =
    KPTypeAlias.builder(name, typeName.kotlin).apply(builder).build()

fun buildKotlinFile(packageName: String, name: String, builder: Builder<KPFileBuilder> = {}): KPFile =
    KPFile.builder(packageName, name)
        .addFileComment(GeneratorConstants.GENERATED_HEADER)
        .apply(builder)
        .indent(GeneratorConstants.INDENT)
        .build()

fun buildKotlinFile(className: IrClassName, builder: Builder<KPFileBuilder> = {}): KPFile =
    buildKotlinFile(className.packageName, className.nestedName, builder)
