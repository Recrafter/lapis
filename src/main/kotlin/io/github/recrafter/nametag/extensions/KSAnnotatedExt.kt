package io.github.recrafter.nametag.extensions

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSType

@OptIn(KspExperimental::class)
inline fun <reified A : Annotation> KSAnnotated.hasAnnotation(): Boolean =
    isAnnotationPresent(A::class)

@OptIn(KspExperimental::class)
inline fun <reified A : Annotation> KSAnnotated.getSingleAnnotationOrNull(): A? =
    getAnnotationsByType(A::class).singleOrNull()

inline fun <reified A : Annotation> KSAnnotated.getAnnotationArgumentType(argumentName: String): KSType =
    requireNotNull(annotations.singleOrNull {
        it.shortName.getShortName() == A::class.simpleName &&
                it.annotationType.resolve().declaration.qualifiedName?.asString() == A::class.qualifiedName
    }?.arguments?.singleOrNull { it.name?.asString() == argumentName }?.value as? KSType) {
        "Failed to get argument '${argumentName}' from ${A::class.atName} annotation."
    }

fun KSAnnotated.toDependencies(aggregating: Boolean = false): Dependencies =
    containingFile?.let { Dependencies(aggregating, it) } ?: Dependencies(aggregating)
