package io.github.recrafter.lapis.extensions.common

import com.squareup.kotlinpoet.asClassName
import io.github.recrafter.lapis.layers.lowering.IrClassName
import io.github.recrafter.lapis.layers.lowering.asIr
import kotlin.reflect.KClass

inline fun <reified T : Any> Any.castOrNull(): T? =
    this as? T

fun KClass<*>.asIr(): IrClassName =
    asClassName().asIr()
