package io.github.recrafter.lapis.extensions.kp

fun KPType?.orUnit(): KPType =
    this ?: KPUnit
