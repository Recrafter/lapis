package io.github.recrafter.nametag.extensions

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated

inline fun <reified T : Annotation> Resolver.getSymbolsWithAnnotation(): Sequence<KSAnnotated> =
    T::class.qualifiedName?.let { getSymbolsWithAnnotation(it) }.orEmpty()
