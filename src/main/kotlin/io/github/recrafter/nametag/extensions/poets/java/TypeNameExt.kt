package io.github.recrafter.nametag.extensions.poets.java

import com.palantir.javapoet.TypeName
import io.github.recrafter.nametag.accessors.processor.JavaType

fun TypeName.boxIfPrimitive(extraCondition: Boolean = true): TypeName =
    if (extraCondition && isPrimitive && !isBoxedPrimitive) box()
    else this

fun TypeName?.orVoid(): TypeName =
    this ?: JavaType.VOID
