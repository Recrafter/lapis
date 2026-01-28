package io.github.recrafter.lapis.extensions.psi

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiRecursiveElementWalkingVisitor

inline fun <reified T : PsiElement> PsiFile.findPsiElement(crossinline action: (T) -> Boolean): T? {
    var found: T? = null
    accept(object : PsiRecursiveElementWalkingVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element is T && action(element)) {
                found = element
                stopWalking()
                return
            }
            super.visitElement(element)
        }
    })
    return found
}
