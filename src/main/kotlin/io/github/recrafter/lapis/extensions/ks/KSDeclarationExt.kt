package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSDeclaration

val KSDeclaration.name: String
    get() = simpleName.asString()
