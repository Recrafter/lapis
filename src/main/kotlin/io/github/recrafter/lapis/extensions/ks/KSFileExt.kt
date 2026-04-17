package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSFile

fun List<KSFile>.toDependencies(aggregating: Boolean = false): Dependencies =
    Dependencies(aggregating, *toTypedArray())
