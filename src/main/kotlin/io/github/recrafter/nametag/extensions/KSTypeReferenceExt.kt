package io.github.recrafter.nametag.extensions

import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.recrafter.nametag.extensions.poets.kotlin.isUnit
import io.github.recrafter.nametag.extensions.poets.kotlin.toJavaType

fun KSTypeReference.toKotlinType(): com.squareup.kotlinpoet.TypeName =
    toTypeName()

fun KSTypeReference.toJavaType(): com.palantir.javapoet.TypeName =
    toKotlinType().toJavaType()

fun KSTypeReference?.isUnitOrNull(): Boolean =
    this == null || toTypeName().isUnit
