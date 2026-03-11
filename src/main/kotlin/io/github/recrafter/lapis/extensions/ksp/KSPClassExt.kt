package io.github.recrafter.lapis.extensions.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.ClassKind
import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.psi.PSIClass
import io.github.recrafter.lapis.extensions.psi.qualifiedName
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.layers.parser.PsiHelper
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

fun KSPClass.isClass(): Boolean =
    classKind == ClassKind.CLASS

fun KSPClass.isInner(): Boolean =
    modifiers.contains(KSPModifier.INNER)

fun KSPClass.getProperties(): List<KSPProperty> =
    getDeclaredProperties().toList()

fun KSPClass.getFunctions(): List<KSPFunction> =
    getDeclaredFunctions().toList()

fun KSPClass.getSuperClassOrNull(): KSPType? =
    superTypes.map { it.resolve() }.find { it.declaration.castOrNull<KSPClass>()?.isClass() == true }

fun KSPClass.findPsi(): PSIClass {
    val (psiFile, _) = PsiHelper.findPsiFile(this)
    val kspQualifiedName = qualifiedName?.asString()
    return psiFile.collectDescendantsOfType<PSIClass>()
        .find { it.qualifiedName == kspQualifiedName }
        ?: lapisError("Unable to locate PSI for class ${kspQualifiedName?.quoted()}")
}

fun KSPClass?.isSame(other: KSPClass?): Boolean {
    val thisName = this?.qualifiedName?.asString() ?: return false
    return thisName == other?.qualifiedName?.asString()
}
