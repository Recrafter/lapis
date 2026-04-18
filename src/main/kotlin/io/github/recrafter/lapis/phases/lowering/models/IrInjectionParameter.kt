package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.builtins.LocalVarImplBuiltin
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed interface IrInjectionParameter

class IrInjectionReceiverParameter(val typeName: IrTypeName) : IrInjectionParameter

class IrInjectionArgumentParameter(
    val name: String?,
    val index: Int,
    val typeName: IrTypeName,
) : IrInjectionParameter

class IrInjectionOperationParameter(val returnTypeName: IrTypeName?) : IrInjectionParameter
class IrInjectionValueParameter(val typeName: IrTypeName) : IrInjectionParameter
class IrInjectionCallbackParameter(val returnTypeName: IrTypeName?) : IrInjectionParameter

sealed class IrInjectionLocalParameter(
    val typeName: IrTypeName,
    val varBuiltin: LocalVarImplBuiltin?,
) : IrInjectionParameter

class IrInjectionParamLocalParameter(
    val name: String?,
    val index: Int,
    typeName: IrTypeName,
    varBuiltin: LocalVarImplBuiltin?,
    val localIndex: Int,
) : IrInjectionLocalParameter(typeName, varBuiltin)

class IrInjectionBodyLocalParameter(
    val name: String,
    typeName: IrTypeName,
    varBuiltin: LocalVarImplBuiltin?,
    val local: IrLocal,
) : IrInjectionLocalParameter(typeName, varBuiltin)
