package io.github.recrafter.nametag.extensions.poets.kotlin

import com.palantir.javapoet.ArrayTypeName
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.ParameterizedTypeName

fun ParameterizedTypeName.toJavaType(): com.palantir.javapoet.TypeName =
    when (rawType) {
        ARRAY -> {
            val componentType = typeArguments.firstOrNull()?.toJavaType()
                ?: throw IllegalStateException("Array with no type! $this")
            ArrayTypeName.of(componentType)
        }

        else -> {
            com.palantir.javapoet.ParameterizedTypeName.get(
                rawType.toJavaClassName() as com.palantir.javapoet.ClassName,
                *typeArguments.map { it.toJavaType(shouldBox = true) }.toTypedArray()
            )
        }
    }


