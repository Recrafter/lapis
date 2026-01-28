package io.github.recrafter.lapis.extensions.psi

val PsiNamedDeclaration.qualifiedName: String?
    get() = fqName?.asString()
