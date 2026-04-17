package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

class IrExtension(
    val className: IrClassName,
    val kinds: List<IrExtensionKind>,
)

sealed class IrExtensionKind(
    val name: String,
    val parameters: List<IrParameter>,
    val returnTypeName: IrTypeName?,
)

class IrPropertyGetterExtension(
    name: String,
    val typeName: IrTypeName,
) : IrExtensionKind(name, emptyList(), typeName)

class IrPropertySetterExtension(
    name: String,
    val typeName: IrTypeName,
) : IrExtensionKind(name, listOf(IrSetterParameter(typeName)), null)

class IrFunctionCallExtension(
    name: String,
    parameters: List<IrParameter>,
    returnTypeName: IrTypeName?,
) : IrExtensionKind(name, parameters, returnTypeName)
