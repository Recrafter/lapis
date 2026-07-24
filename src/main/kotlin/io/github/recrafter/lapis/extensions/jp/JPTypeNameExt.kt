package io.github.recrafter.lapis.extensions.jp

import io.github.diskria.poetesse.java.JPTypeName

fun JPTypeName?.orVoid(): JPTypeName =
    this ?: JPVoid
