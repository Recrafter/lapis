package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation

inline fun <reified A : Annotation> KSAnnotated.findAnnotation(): KSAnnotation? =
    annotations.find { it.isType<A>() }

inline fun <reified A : Annotation> KSAnnotated.hasAnnotation(): Boolean =
    findAnnotation<A>() != null
