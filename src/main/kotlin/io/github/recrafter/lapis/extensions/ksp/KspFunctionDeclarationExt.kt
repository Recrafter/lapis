package io.github.recrafter.lapis.extensions.ksp

import io.github.recrafter.lapis.extensions.psi.PsiFunction
import io.github.recrafter.lapis.extensions.psi.qualifiedName
import io.github.recrafter.lapis.utils.PsiCompanion
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

fun KspFunctionDeclaration.isExtension(): Boolean =
    extensionReceiver != null

fun KspFunctionDeclaration.findPsi(psiCompanion: PsiCompanion): PsiFunction? {
    val file = psiCompanion.findPsiFile(this) ?: return null
    val kspName = simpleName.asString()
    val kspParametersCount = parameters.size
    val kspClassQualifiedName = parentDeclaration?.qualifiedName?.asString()
    return file.collectDescendantsOfType<PsiFunction>().firstOrNull { psiFunction ->
        val psiName = psiFunction.name
        val psiParametersCount = psiFunction.valueParameters.size
        val psiClassQualifiedName = psiFunction.getStrictParentOfType<KtClass>()?.qualifiedName
        psiName == kspName && psiParametersCount == kspParametersCount && psiClassQualifiedName == kspClassQualifiedName
    }
}

fun KspFunctionDeclaration.getReturnTypeOrNull(): KspType? =
    returnType?.resolve()?.takeNotUnit()
