package io.github.recrafter.lapis.extensions.ksp

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.ks.KSAnnotated

inline fun <reified A : Annotation> KSPResolver.getSymbolsAnnotatedWith(): List<KSAnnotated> =
    getSymbolsWithAnnotation(
        A::class
            .qualifiedName
            ?: lapisError("Cannot resolve qualified name for annotation class ${A::class.simpleName}")
    ).toList()
