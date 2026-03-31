package io.github.recrafter.lapis.extensions.ks

import io.github.recrafter.lapis.extensions.ksp.KSPDependencies

fun List<KSFile>.toDependencies(aggregating: Boolean = false): KSPDependencies =
    KSPDependencies(aggregating, *toTypedArray())
