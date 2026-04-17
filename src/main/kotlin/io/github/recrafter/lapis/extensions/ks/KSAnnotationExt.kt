package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.Origin

val KSAnnotation.explicitArguments: List<KSValueArgument>
    get() = arguments.filter { it.origin != Origin.SYNTHETIC }
