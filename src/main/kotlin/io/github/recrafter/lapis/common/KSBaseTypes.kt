package io.github.recrafter.lapis.common

import com.google.devtools.ksp.processing.KSBuiltIns
import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.extensions.kp.*

@JvmInline
value class KSBaseTypes(private val builtins: KSBuiltIns) {
    val nothing: KSType get() = builtins.nothingType

    val unit: KSType get() = builtins.unitType
    val boolean: KSType get() = builtins.booleanType
    val byte: KSType get() = builtins.byteType
    val short: KSType get() = builtins.shortType
    val int: KSType get() = builtins.intType
    val long: KSType get() = builtins.longType
    val char: KSType get() = builtins.charType
    val float: KSType get() = builtins.floatType
    val double: KSType get() = builtins.doubleType

    val any: KSType get() = builtins.anyType
    val string: KSType get() = builtins.stringType

    val array: KSType get() = builtins.arrayType
}

fun KSType.isUnit(baseTypes: KSBaseTypes): Boolean =
    this == baseTypes.unit

fun KSType.isArray(baseTypes: KSBaseTypes): Boolean =
    this == baseTypes.array

fun KSType.findArrayComponentType(baseTypes: KSBaseTypes, typeArguments: List<KSType>): KSType? =
    when (declaration.qualifiedName?.asString()) {
        KPBooleanArray.qualifiedName -> baseTypes.boolean
        KPByteArray.qualifiedName -> baseTypes.byte
        KPShortArray.qualifiedName -> baseTypes.short
        KPIntArray.qualifiedName -> baseTypes.int
        KPLongArray.qualifiedName -> baseTypes.long
        KPCharArray.qualifiedName -> baseTypes.char
        KPFloatArray.qualifiedName -> baseTypes.float
        KPDoubleArray.qualifiedName -> baseTypes.double
        else -> {
            if (isArray(baseTypes)) typeArguments.first()
            else null
        }
    }
