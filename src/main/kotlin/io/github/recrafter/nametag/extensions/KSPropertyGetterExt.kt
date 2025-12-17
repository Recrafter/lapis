package io.github.recrafter.nametag.extensions

import com.google.devtools.ksp.symbol.KSPropertyGetter
import com.google.devtools.ksp.symbol.Modifier

val KSPropertyGetter.isAbstract: Boolean
    get() = modifiers.contains(Modifier.ABSTRACT)
