package io.github.recrafter.lapis.extensions.ksp

inline fun <reified A : Annotation> KspResolver.getSymbolsAnnotatedWith(): List<KspAnnotated> =
    getSymbolsWithAnnotation(requireNotNull(A::class.qualifiedName)).toList()
