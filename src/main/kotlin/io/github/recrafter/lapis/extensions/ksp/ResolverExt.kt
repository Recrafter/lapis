package io.github.recrafter.lapis.extensions.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import io.github.recrafter.lapis.extensions.common.lapisError

inline fun <reified A : Annotation> Resolver.getSymbolsAnnotatedWith(): List<KSAnnotated> =
    getSymbolsWithAnnotation(
        A::class.qualifiedName
            ?: lapisError("Cannot resolve qualified name for annotation class ${A::class.simpleName}")
    ).toList()
