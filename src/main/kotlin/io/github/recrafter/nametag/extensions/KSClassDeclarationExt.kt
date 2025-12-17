package io.github.recrafter.nametag.extensions

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration

fun KSClassDeclaration.isInterface(): Boolean =
    classKind == ClassKind.INTERFACE
