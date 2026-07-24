package io.github.recrafter.lapis.extensions.kp

import io.github.diskria.poetesse.kotlin.KPTypeName

fun KPTypeName?.orUnit(): KPTypeName =
    this ?: KPUnit
