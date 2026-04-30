package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.builtins.LocalVarImplBuiltin
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed interface IrInjectionParameter

class IrInjectionReceiverParameter(
    val typeName: IrTypeName,
    val isCoerce: Boolean,
) : IrInjectionParameter

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
    val varImplBuiltin: LocalVarImplBuiltin?,
) : IrInjectionParameter

class IrInjectionParamLocalParameter(
    val name: String,
    typeName: IrTypeName,
    varImplBuiltin: LocalVarImplBuiltin?,
    val localIndex: Int,
) : IrInjectionLocalParameter(typeName, varImplBuiltin)

class IrInjectionBodyLocalParameter(
    val name: String,
    typeName: IrTypeName,
    varImplBuiltin: LocalVarImplBuiltin?,
    val local: IrLocal,
) : IrInjectionLocalParameter(typeName, varImplBuiltin)

class IrInjectionShareParameter(
    val name: String,
    typeName: IrTypeName,
    varImplBuiltin: LocalVarImplBuiltin,
    val key: String,
    val namespace: String?,
) : IrInjectionLocalParameter(typeName, varImplBuiltin)
