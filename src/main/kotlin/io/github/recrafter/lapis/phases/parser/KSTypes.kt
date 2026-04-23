package io.github.recrafter.lapis.phases.parser

import com.google.devtools.ksp.processing.KSBuiltIns
import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.extensions.kp.*

@JvmInline
value class KSTypes(private val builtIns: KSBuiltIns) {
    val nothing: KSType get() = builtIns.nothingType

    val unit: KSType get() = builtIns.unitType
    val boolean: KSType get() = builtIns.booleanType
    val byte: KSType get() = builtIns.byteType
    val short: KSType get() = builtIns.shortType
    val int: KSType get() = builtIns.intType
    val long: KSType get() = builtIns.longType
    val char: KSType get() = builtIns.charType
    val float: KSType get() = builtIns.floatType
    val double: KSType get() = builtIns.doubleType

    val any: KSType get() = builtIns.anyType
    val string: KSType get() = builtIns.stringType

    val array: KSType get() = builtIns.arrayType
}

fun KSType.isNothing(types: KSTypes): Boolean =
    this == types.nothing

fun KSType.isUnit(types: KSTypes): Boolean =
    this == types.unit

fun KSType.isAny(types: KSTypes): Boolean =
    this == types.any

fun KSType.isArray(types: KSTypes): Boolean =
    this == types.array

fun KSType.findArrayComponentType(types: KSTypes): KSType? =
    if (isArray(types)) arguments.firstOrNull()?.type?.resolve()
    else when (declaration.qualifiedName?.asString()) {
        KPBooleanArray.qualifiedName -> types.boolean
        KPByteArray.qualifiedName -> types.byte
        KPShortArray.qualifiedName -> types.short
        KPIntArray.qualifiedName -> types.int
        KPLongArray.qualifiedName -> types.long
        KPCharArray.qualifiedName -> types.char
        KPFloatArray.qualifiedName -> types.float
        KPDoubleArray.qualifiedName -> types.double
        else -> null
    }
