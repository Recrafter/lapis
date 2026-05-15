package io.github.recrafter.lapis.extensions.jp

fun JPTypeName?.orVoid(): JPTypeName =
    this ?: JPVoid
