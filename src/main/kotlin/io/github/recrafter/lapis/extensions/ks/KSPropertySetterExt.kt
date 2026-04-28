package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSPropertySetter
import com.google.devtools.ksp.symbol.Modifier

val KSPropertySetter.isPublic: Boolean
    get() = modifiers.contains(Modifier.PUBLIC)
