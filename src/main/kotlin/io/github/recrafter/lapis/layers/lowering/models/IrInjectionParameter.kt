package io.github.recrafter.lapis.layers.lowering.models

import io.github.recrafter.lapis.layers.lowering.types.IrTypeName

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

sealed class IrInjectionLocalBasedParameter : IrInjectionParameter
class IrInjectionParamParameter(
    val name: String?,
    val index: Int,
    val typeName: IrTypeName,
    val localIndex: Int,
) : IrInjectionLocalBasedParameter()

class IrInjectionLocalParameter(
    val name: String,
    val typeName: IrTypeName,
    val local: IrLocal,
) : IrInjectionLocalBasedParameter()
