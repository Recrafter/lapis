package io.github.recrafter.lapis.extensions.psi

val PSINamedDeclaration.qualifiedName: String?
    get() = fqName?.asString()
