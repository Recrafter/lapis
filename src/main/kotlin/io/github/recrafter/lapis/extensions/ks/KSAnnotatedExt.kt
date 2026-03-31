package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.isAnnotationPresent
import kotlin.reflect.KClass

@OptIn(KspExperimental::class)
fun KSAnnotated.hasAnnotation(annotation: KClass<out Annotation>): Boolean =
    isAnnotationPresent(annotation)

@OptIn(KspExperimental::class)
inline fun <reified A : Annotation> KSAnnotated.hasAnnotation(): Boolean =
    hasAnnotation(A::class)

@OptIn(KspExperimental::class)
inline fun <reified A : Annotation> KSAnnotated.getAnnotationOrNull(): A? =
    getAnnotationsByType(A::class).firstOrNull()
