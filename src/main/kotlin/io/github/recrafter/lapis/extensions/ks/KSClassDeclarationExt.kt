package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.*
import com.google.devtools.ksp.symbol.*

val KSClassDeclaration.starProjectedType: KSType
    get() = asStarProjectedType()

val KSClassDeclaration.type: KSType
    get() = asType(emptyList())

val KSClassDeclaration.isInterface: Boolean
    get() = classKind == ClassKind.INTERFACE

val KSClassDeclaration.isClass: Boolean
    get() = classKind == ClassKind.CLASS

val KSClassDeclaration.isObject: Boolean
    get() = classKind == ClassKind.OBJECT

val KSClassDeclaration.isExplicitlyOpen: Boolean
    get() = modifiers.contains(Modifier.OPEN)

val KSClassDeclaration.isExplicitlyAbstract: Boolean
    get() = modifiers.contains(Modifier.ABSTRACT)

val KSClassDeclaration.isSealed: Boolean
    get() = modifiers.contains(Modifier.SEALED)

val KSClassDeclaration.isValid: Boolean
    get() = validate()

val KSClassDeclaration.constructorPropertyDeclarations: Sequence<KSPropertyDeclaration>
    get() {
        val constructorPropertyNames = primaryConstructor
            ?.parameters
            ?.filter { it.isVal || it.isVar }
            ?.mapNotNull { it.name?.asString() }
            ?.toSet()
            ?: emptySet()
        return getDeclaredProperties().filter { it.simpleName.asString() in constructorPropertyNames }
    }

val KSClassDeclaration.bodyPropertyDeclarations: Sequence<KSPropertyDeclaration>
    get() = getDeclaredProperties() - constructorPropertyDeclarations.toSet()

val KSClassDeclaration.constructorDeclarations: Sequence<KSFunctionDeclaration>
    get() = getConstructors()

val KSClassDeclaration.functionDeclarations: Sequence<KSFunctionDeclaration>
    get() = getDeclaredFunctions().filter { !it.isConstructor() }

val KSClassDeclaration.classDeclarations: Sequence<KSClassDeclaration>
    get() = declarations.filterIsInstance<KSClassDeclaration>()

fun KSClassDeclaration.findCompanionObjectClassDeclaration(): KSClassDeclaration? =
    classDeclarations.find { it.isCompanionObject }

fun KSClassDeclaration.isAssignableFrom(other: KSClassDeclaration): Boolean =
    type.isAssignableFrom(other.type)
