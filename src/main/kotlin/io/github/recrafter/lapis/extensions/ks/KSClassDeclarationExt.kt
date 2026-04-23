package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.*
import com.google.devtools.ksp.symbol.*

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

val KSClassDeclaration.propertyDeclarations: Sequence<KSPropertyDeclaration>
    get() = getDeclaredProperties()

val KSClassDeclaration.constructorDeclarations: Sequence<KSFunctionDeclaration>
    get() = getConstructors()

val KSClassDeclaration.functionDeclarations: Sequence<KSFunctionDeclaration>
    get() = getDeclaredFunctions().filter { !it.isConstructor() }

val KSClassDeclaration.classDeclarations: Sequence<KSClassDeclaration>
    get() = declarations.filterIsInstance<KSClassDeclaration>()

fun KSClassDeclaration.findCompanionObject(): KSClassDeclaration? =
    classDeclarations.find { it.isCompanionObject }

fun KSClassDeclaration.isAssignableFrom(other: KSClassDeclaration): Boolean =
    type.isAssignableFrom(other.type)
