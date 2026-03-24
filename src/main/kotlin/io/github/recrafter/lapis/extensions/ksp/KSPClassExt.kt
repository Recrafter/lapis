package io.github.recrafter.lapis.extensions.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.ClassKind
import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.psi.PSIClass
import io.github.recrafter.lapis.extensions.psi.qualifiedName
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.layers.parser.PSIHelper
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

val KSPClassDecl.isClass: Boolean
    get() = classKind == ClassKind.CLASS

val KSPClassDecl.isInner: Boolean
    get() = modifiers.contains(KSPModifier.INNER)

val KSPClassDecl.propertyDeclarations: List<KSPPropertyDecl>
    get() = getDeclaredProperties().toList()

val KSPClassDecl.functionDeclarations: List<KSPFunctionDecl>
    get() = getDeclaredFunctions().toList()

fun KSPClassDecl.getSuperClassTypeOrNull(): KSPType? =
    superTypes.map { it.resolve() }.find { it.toClassDeclOrNull()?.isClass == true }

fun KSPClassDecl.takeNotNothing(): KSPClassDecl? =
    takeUnless { it.isInstance(Nothing::class) }

fun KSPClassDecl.findPsi(): PSIClass {
    val (psiFile, _) = PSIHelper.findPSIFile(this)
    val kspQualifiedName = qualifiedName?.asString()
    return psiFile.collectDescendantsOfType<PSIClass>()
        .find { it.qualifiedName == kspQualifiedName }
        ?: lapisError("Unable to locate PSI for class ${kspQualifiedName?.quoted()}")
}

fun KSPClassDecl?.isSame(other: KSPClassDecl?): Boolean {
    val thisName = this?.qualifiedName?.asString() ?: return false
    return thisName == other?.qualifiedName?.asString()
}
