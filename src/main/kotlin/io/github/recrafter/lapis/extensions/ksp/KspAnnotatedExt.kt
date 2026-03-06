package io.github.recrafter.lapis.extensions.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.isAnnotationPresent
import kotlin.reflect.KClass

@OptIn(KspExperimental::class)
fun KspAnnotated.hasAnnotation(annotation: KClass<out Annotation>): Boolean =
    isAnnotationPresent(annotation)

@OptIn(KspExperimental::class)
inline fun <reified A : Annotation> KspAnnotated.hasAnnotation(): Boolean =
    hasAnnotation(A::class)

@OptIn(KspExperimental::class)
inline fun <reified A : Annotation> KspAnnotated.getAnnotationOrNull(): A? =
    getAnnotationsByType(A::class).firstOrNull()
