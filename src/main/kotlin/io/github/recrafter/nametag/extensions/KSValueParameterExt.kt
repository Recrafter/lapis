package io.github.recrafter.nametag.extensions

import com.google.devtools.ksp.symbol.KSValueParameter

fun KSValueParameter.requireName(): String =
    requireNotNull(name?.asString()) {
        "Unnamed parameter is not supported in this context: $this."
    }
