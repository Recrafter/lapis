package io.github.recrafter.nametag.extensions.poets.kotlin

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import io.github.recrafter.nametag.accessors.processor.KotlinFile
import io.github.recrafter.nametag.accessors.processor.KotlinType

fun buildKotlinFunction(name: String, builder: FunSpec.Builder.() -> Unit = {}): FunSpec =
    FunSpec.builder(name).apply(builder).build()

fun buildKotlinParameter(
    type: KotlinType,
    name: String,
    builder: ParameterSpec.Builder.() -> Unit = {}
): ParameterSpec =
    ParameterSpec.builder(name, type).apply(builder).build()

fun buildKotlinFile(packageName: String, name: String, builder: FileSpec.Builder.() -> Unit = {}): KotlinFile =
    KotlinFile.builder(packageName, name).apply(builder).indent("    ").build()
