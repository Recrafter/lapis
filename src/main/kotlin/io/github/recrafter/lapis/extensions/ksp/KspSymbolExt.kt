package io.github.recrafter.lapis.extensions.ksp

import com.google.devtools.ksp.containingFile

fun KspSymbol.toDependencies(aggregating: Boolean = false): KspDependencies =
    containingFile?.let { KspDependencies(aggregating, it) } ?: KspDependencies(aggregating)

fun List<KspDependencies>.flatten(aggregating: Boolean = false): KspDependencies =
    KspDependencies(aggregating, *flatMap { it.originatingFiles }.toTypedArray())
