package io.github.recrafter.nametag.extensions

import kotlin.reflect.KClass

val KClass<out Annotation>.atName: String
    get() = "@$simpleName"
