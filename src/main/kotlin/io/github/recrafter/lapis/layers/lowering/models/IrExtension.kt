package io.github.recrafter.lapis.layers.lowering.models

import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName

class IrExtension(
    val className: IrClassName,
    val kinds: List<IrExtensionKind>,
)

sealed class IrExtensionKind(
    val name: String,
    val parameters: List<IrParameter>,
    val returnTypeName: IrTypeName?,
)

class IrFieldGetterExtension(
    name: String,
    val typeName: IrTypeName,
) : IrExtensionKind(name, emptyList(), typeName)

class IrFieldSetterExtension(
    name: String,
    val typeName: IrTypeName,
) : IrExtensionKind(name, listOf(IrParameter("newValue", typeName)), null)

class IrMethodExtension(
    name: String,
    parameters: List<IrParameter>,
    returnTypeName: IrTypeName?,
) : IrExtensionKind(name, parameters, returnTypeName)
