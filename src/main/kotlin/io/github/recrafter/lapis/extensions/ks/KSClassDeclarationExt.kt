package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate

val KSClassDeclaration.type: KSType
    get() = asStarProjectedType()

val KSClassDeclaration.isClass: Boolean
    get() = classKind == ClassKind.CLASS

val KSClassDeclaration.isInner: Boolean
    get() = modifiers.contains(Modifier.INNER)

val KSClassDeclaration.isValid: Boolean
    get() = validate()

val KSClassDeclaration.propertyDeclarations: List<KSPropertyDeclaration>
    get() = getDeclaredProperties().toList()

val KSClassDeclaration.functionDeclarations: List<KSFunctionDeclaration>
    get() = getDeclaredFunctions().toList()

fun KSClassDeclaration.isSame(other: KSClassDeclaration?): Boolean {
    val thisName = qualifiedName?.asString() ?: return false
    val otherName = other?.qualifiedName?.asString() ?: return false
    return thisName == otherName
}

fun KSClassDeclaration.findCompanionObject(): KSClassDeclaration? =
    declarations.filterIsInstance<KSClassDeclaration>().find { it.isCompanionObject }
