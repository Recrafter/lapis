package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.lowering.IrModifier
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

open class IrParameter(
    val name: String,
    val typeName: IrTypeName,
    val modifiers: List<IrModifier> = emptyList(),
)

class IrSetterParameter(
    typeName: IrTypeName,
    modifiers: List<IrModifier> = emptyList(),
) : IrParameter("newValue", typeName, modifiers)
