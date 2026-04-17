package io.github.recrafter.lapis.phases.parser

import com.google.devtools.ksp.processing.KSBuiltIns
import com.google.devtools.ksp.symbol.KSType

@JvmInline
value class KSTypes(private val builtIns: KSBuiltIns) {
    val any: KSType get() = builtIns.anyType
    val nothing: KSType get() = builtIns.nothingType
    val unit: KSType get() = builtIns.unitType
    val byte: KSType get() = builtIns.byteType
    val short: KSType get() = builtIns.shortType
    val int: KSType get() = builtIns.intType
    val long: KSType get() = builtIns.longType
    val float: KSType get() = builtIns.floatType
    val double: KSType get() = builtIns.doubleType
    val char: KSType get() = builtIns.charType
    val boolean: KSType get() = builtIns.booleanType
    val string: KSType get() = builtIns.stringType
}
