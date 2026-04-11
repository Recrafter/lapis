package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.validate

val KSClassDecl.isClass: Boolean
    get() = classKind == ClassKind.CLASS

val KSClassDecl.isInner: Boolean
    get() = modifiers.contains(KSPModifier.INNER)

val KSClassDecl.isValid: Boolean
    get() = validate()

val KSClassDecl.propertyDeclarations: List<KSPropertyDecl>
    get() = getDeclaredProperties().toList()

val KSClassDecl.functionDeclarations: List<KSFunctionDecl>
    get() = getDeclaredFunctions().toList()

fun KSClassDecl.getSuperClassTypeOrNull(): KSType? =
    superTypes.map { it.resolve() }.find { it.getClassDecl()?.takeNotAny()?.isClass == true }

fun KSClassDecl.takeNotNothing(): KSClassDecl? =
    takeUnless { it.isInstance(Nothing::class) }

fun KSClassDecl.takeNotAny(): KSClassDecl? =
    takeUnless { it.isInstance(Any::class) }

fun KSClassDecl.isSame(other: KSClassDecl?): Boolean {
    val thisName = qualifiedName?.asString() ?: return false
    val otherName = other?.qualifiedName?.asString() ?: return false
    return thisName == otherName
}
