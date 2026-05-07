package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.lowering.IrModifier
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

open class IrParameter(
    val name: String,
    val typeName: IrTypeName,
    vararg val modifiers: IrModifier = arrayOf(),
)

class IrSetterParameter(
    typeName: IrTypeName,
    vararg modifiers: IrModifier = arrayOf(),
) : IrParameter("newValue", typeName, *modifiers)
