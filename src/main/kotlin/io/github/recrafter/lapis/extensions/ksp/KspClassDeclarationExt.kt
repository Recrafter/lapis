package io.github.recrafter.lapis.extensions.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.ClassKind
import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.psi.PsiClass
import io.github.recrafter.lapis.extensions.psi.findPsiElement
import io.github.recrafter.lapis.extensions.psi.qualifiedName
import io.github.recrafter.lapis.utils.PsiCompanion

fun KspClassDeclaration.isClass(): Boolean =
    classKind == ClassKind.CLASS

fun KspClassDeclaration.isInner(): Boolean =
    modifiers.contains(KspModifier.INNER)

fun KspClassDeclaration.getProperties(): List<KspPropertyDeclaration> =
    getDeclaredProperties().toList()

fun KspClassDeclaration.getFunctions(): List<KspFunctionDeclaration> =
    getDeclaredFunctions().toList()

fun KspClassDeclaration.getSuperClassOrNull(): KspType? =
    superTypes.map { it.resolve() }.find { it.declaration.castOrNull<KspClassDeclaration>()?.isClass() == true }

fun KspClassDeclaration.findPsi(psiCompanion: PsiCompanion): PsiClass? =
    psiCompanion.findPsiFile(this)?.findPsiElement<PsiClass> {
        it.qualifiedName == qualifiedName?.asString()
    }
