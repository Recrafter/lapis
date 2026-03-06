package io.github.recrafter.lapis.extensions.ksp

import io.github.recrafter.lapis.extensions.psi.PsiFunction
import io.github.recrafter.lapis.extensions.psi.qualifiedName
import io.github.recrafter.lapis.layers.parser.PsiHelper
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

fun KspFunctionDeclaration.isExtension(): Boolean =
    extensionReceiver != null

fun KspFunctionDeclaration.findPsi(): PsiFunction? {
    val file = PsiHelper.findPsiFile(this) ?: return null
    val kspName = simpleName.asString()
    val kspParametersCount = parameters.size
    val kspClassQualifiedName = parentDeclaration?.qualifiedName?.asString()
    return file.collectDescendantsOfType<PsiFunction>().firstOrNull { psiFunction ->
        psiFunction.name == kspName &&
            psiFunction.valueParameters.size == kspParametersCount &&
            psiFunction.getStrictParentOfType<KtClass>()?.qualifiedName == kspClassQualifiedName
    }
}

fun KspFunctionDeclaration.getReturnTypeOrNull(): KspType? =
    returnType?.resolve()?.takeNotUnit()
