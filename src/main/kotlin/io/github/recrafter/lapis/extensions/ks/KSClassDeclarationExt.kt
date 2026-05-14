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
    get() = Modifier.OPEN in modifiers

val KSClassDeclaration.isExplicitlyAbstract: Boolean
    get() = Modifier.ABSTRACT in modifiers

val KSClassDeclaration.isSealed: Boolean
    get() = Modifier.SEALED in modifiers

val KSClassDeclaration.isValid: Boolean
    get() = validate()

val KSClassDeclaration.bodyPropertyDeclarations: Sequence<KSPropertyDeclaration>
    get() {
        val constructorPropertyNames = constructorDeclarations.flatMap {
            it.constructorProperties.mapNotNull { property -> property.name?.asString() }
        }
        return getDeclaredProperties().filter { it.name !in constructorPropertyNames }
    }

val KSClassDeclaration.constructorDeclarations: Sequence<KSFunctionDeclaration>
    get() = getConstructors()

val KSClassDeclaration.functionDeclarations: Sequence<KSFunctionDeclaration>
    get() = getDeclaredFunctions().filter { !it.isConstructor() }

val KSClassDeclaration.classDeclarations: Sequence<KSClassDeclaration>
    get() = declarations.filterIsInstance<KSClassDeclaration>()

val KSClassDeclaration.companionObjectClassDeclarations: Sequence<KSClassDeclaration>
    get() = classDeclarations.filter { it.isCompanionObject }
