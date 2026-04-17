package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation

inline fun <reified A : Annotation> KSAnnotated.findAnnotation(): KSAnnotation? =
    annotations.find {
        it.shortName.getShortName() == A::class.simpleName &&
            it.annotationType.resolve().declaration.qualifiedName?.asString() == A::class.qualifiedName
    }

inline fun <reified A : Annotation> KSAnnotated.hasAnnotation(): Boolean =
    findAnnotation<A>() != null
