package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import io.github.recrafter.lapis.extensions.common.requireQualifiedName

inline fun <reified A : Annotation> KSAnnotated.findAnnotation(): KSAnnotation? {
    val qualifiedName = A::class.requireQualifiedName()
    return annotations.find {
        qualifiedName.endsWith(it.shortName.asString()) &&
            it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName
    }
}

inline fun <reified A : Annotation> KSAnnotated.hasAnnotation(): Boolean =
    findAnnotation<A>() != null
