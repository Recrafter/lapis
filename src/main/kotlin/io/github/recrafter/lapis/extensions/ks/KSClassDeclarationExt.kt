package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate

val KSClassDeclaration.starProjectedType: KSType
    get() = asStarProjectedType()

val KSClassDeclaration.type: KSType
    get() = asType(emptyList())

val KSClassDeclaration.isClass: Boolean
    get() = classKind == ClassKind.CLASS

val KSClassDeclaration.isInner: Boolean
    get() = modifiers.contains(Modifier.INNER)

val KSClassDeclaration.isValid: Boolean
    get() = validate()

val KSClassDeclaration.classDeclarations: List<KSClassDeclaration>
    get() = declarations.filterIsInstance<KSClassDeclaration>().toList()

val KSClassDeclaration.propertyDeclarations: List<KSPropertyDeclaration>
    get() = getDeclaredProperties().toList()

val KSClassDeclaration.functionDeclarations: List<KSFunctionDeclaration>
    get() = getDeclaredFunctions().toList()

fun KSClassDeclaration.findCompanionObject(): KSClassDeclaration? =
    classDeclarations.find { it.isCompanionObject }

fun KSClassDeclaration.isAssignableFrom(other: KSClassDeclaration): Boolean =
    type.isAssignableFrom(other.type)
