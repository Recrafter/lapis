package io.github.recrafter.lapis.phases.validator.models.schemas

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import io.github.recrafter.lapis.annotations.Side
import io.github.recrafter.lapis.common.JvmClassName
import io.github.recrafter.lapis.extensions.ks.starProjectedType
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import io.github.recrafter.lapis.phases.validator.models.common.SourceFile

class Schema(
    symbol: KSNode,
    classDeclaration: KSClassDeclaration,

    val originJvmClassName: JvmClassName,
    val originClassDeclaration: KSClassDeclaration,
    val side: Side,

    val isAccessible: Boolean,

    val accessRequest: AccessRequest?,
    val descriptors: List<Descriptor>,
) : SourceFile(symbol, classDeclaration) {
    val originTypeName: IrTypeName = originClassDeclaration.starProjectedType.asIrTypeName()
}
