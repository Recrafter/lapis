package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.Origin

val KSValueArgument.isExplicit: Boolean
    get() = origin != Origin.SYNTHETIC
