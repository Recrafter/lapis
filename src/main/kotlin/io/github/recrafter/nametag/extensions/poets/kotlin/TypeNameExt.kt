package io.github.recrafter.nametag.extensions.poets.kotlin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName

// Adapted from https://gist.github.com/ShaishavGandhi/097033cc528ae25741186973e4d36ce4
// Originally created by Shaishav Gandhi (Uber) for KotlinPoet ↔ JavaPoet interop.
// Modified for Palantir JavaPoet.

fun TypeName.toJavaType(shouldBox: Boolean = false): com.palantir.javapoet.TypeName =
    when (this) {
        is ClassName -> toJavaClassName(shouldBox)
        is ParameterizedTypeName -> toJavaType()
        is TypeVariableName -> toJavaType()
        else -> throw IllegalStateException("Not applicable in Java!")
    }

val TypeName.isUnit: Boolean
    get() = toString() == "kotlin.Unit"
