package io.github.recrafter.lapis.phases.validator.models.common

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSNode
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrClassName

open class SourceFile(
    symbol: KSNode,
    classDeclaration: KSClassDeclaration,
) {
    val className: IrClassName = classDeclaration.asIrClassName()
    val containingFile: KSFile? = symbol.containingFile
}
