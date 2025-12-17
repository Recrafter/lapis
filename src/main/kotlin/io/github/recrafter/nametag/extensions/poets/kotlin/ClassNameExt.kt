package io.github.recrafter.nametag.extensions.poets.kotlin

import com.squareup.kotlinpoet.*
import io.github.recrafter.nametag.extensions.poets.java.boxIfPrimitive

fun ClassName.toJavaClassName(shouldBox: Boolean = false): com.palantir.javapoet.TypeName =
    when (copy(nullable = false)) {
        BOOLEAN -> com.palantir.javapoet.TypeName.BOOLEAN.boxIfPrimitive(shouldBox || isNullable)
        BYTE -> com.palantir.javapoet.TypeName.BYTE.boxIfPrimitive(shouldBox || isNullable)
        CHAR -> com.palantir.javapoet.TypeName.CHAR.boxIfPrimitive(shouldBox || isNullable)
        SHORT -> com.palantir.javapoet.TypeName.SHORT.boxIfPrimitive(shouldBox || isNullable)
        INT -> com.palantir.javapoet.TypeName.INT.boxIfPrimitive(shouldBox || isNullable)
        LONG -> com.palantir.javapoet.TypeName.LONG.boxIfPrimitive(shouldBox || isNullable)
        FLOAT -> com.palantir.javapoet.TypeName.FLOAT.boxIfPrimitive(shouldBox || isNullable)
        DOUBLE -> com.palantir.javapoet.TypeName.DOUBLE.boxIfPrimitive(shouldBox || isNullable)
        UNIT -> com.palantir.javapoet.TypeName.VOID
        ClassName("kotlin", "String") -> com.palantir.javapoet.ClassName.get("java.lang", "String")
        ClassName("kotlin", "List") -> com.palantir.javapoet.ClassName.get("java.util", "List")
        ClassName("kotlin", "Set") -> com.palantir.javapoet.ClassName.get("java.util", "Set")
        ClassName("kotlin", "Map") -> com.palantir.javapoet.ClassName.get("java.util", "Map")
        else -> {
            if (simpleNames.size == 1) {
                com.palantir.javapoet.ClassName.get(packageName, simpleName)
            } else {
                com.palantir.javapoet.ClassName.get(
                    packageName,
                    simpleNames.first(),
                    *simpleNames.drop(1).toTypedArray()
                )
            }
        }
    }
