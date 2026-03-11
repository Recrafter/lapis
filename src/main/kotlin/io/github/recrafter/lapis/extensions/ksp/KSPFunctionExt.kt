package io.github.recrafter.lapis.extensions.ksp

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.psi.PSIFile
import io.github.recrafter.lapis.extensions.psi.PSIFunction
import io.github.recrafter.lapis.extensions.psi.qualifiedName
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.layers.parser.PsiHelper
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import kotlin.math.abs

fun KSPFunction.isExtension(): Boolean =
    extensionReceiver != null

fun KSPFunction.getReturnTypeOrNull(): KSPType? =
    returnType?.resolve()?.takeNotUnit()

fun KSPFunction.findPsi(): PSIFunction {
    val (psiFile, kspLine) = PsiHelper.findPsiFile(this)
    val kspQualifiedName = qualifiedName?.asString()
    val kspParameters = parameters.map { it.name?.asString() }
    val candidates = psiFile.collectDescendantsOfType<PSIFunction>().filter { psiFunction ->
        psiFunction.qualifiedName == kspQualifiedName &&
            psiFunction.valueParameters.map { it.name } == kspParameters
    }
    if (candidates.isEmpty()) {
        lapisError("Unable to locate PSI for function ${kspQualifiedName?.quoted()}")
    }
    return candidates.minBy {
        val psiLine = psiFile.getLineNumber(it.startOffset)
        abs(psiLine - kspLine)
    }
}

private fun PSIFile.getLineNumber(offset: Int): Int =
    viewProvider.contents.subSequence(0, offset).count { it == '\n' } + 1
