package io.github.recrafter.lapis.extensions.ksp

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import io.github.recrafter.lapis.extensions.common.requireQualifiedName

inline fun <reified A : Annotation> Resolver.getSymbolsAnnotatedWith(): Sequence<KSAnnotated> =
    getSymbolsWithAnnotation(A::class.requireQualifiedName()).sortedWith(
        compareBy<KSAnnotated>(
            { it.containingFile?.packageName?.asString() },
            { it.containingFile?.fileName?.lowercase() },
        ).thenBy { it.containingFile?.fileName }
    )
