package io.github.recrafter.lapis.extensions.kp

fun KPTypeName?.orUnit(): KPTypeName =
    this ?: KPUnit
