package io.github.recrafter.nametag.extensions.poets.kotlin

import com.squareup.kotlinpoet.TypeVariableName

fun TypeVariableName.toJavaType(): com.palantir.javapoet.TypeVariableName =
    com.palantir.javapoet.TypeVariableName.get(name, *bounds.map { it.toJavaType(shouldBox = true) }.toTypedArray())
