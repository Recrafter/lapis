package io.github.recrafter.nametag.extensions

import com.google.devtools.ksp.symbol.KSDeclaration

val KSDeclaration.name: String
    get() = simpleName.asString()
