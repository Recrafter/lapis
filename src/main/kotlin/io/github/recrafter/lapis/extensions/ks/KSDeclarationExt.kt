package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSDeclaration
import io.github.recrafter.lapis.phases.lowering.types.IrClassName

val KSDeclaration.name: String
    get() = simpleName.asString()

fun KSDeclaration.isInstance(className: IrClassName): Boolean =
    qualifiedName != null && qualifiedName?.asString() == className.qualifiedName
