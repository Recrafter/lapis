package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed interface IrDescriptor

sealed class IrInvokableDescriptor(
    val bodyWrapperImpl: IrBodyDescriptorWrapperImpl?,
    val callWrapperImpl: IrCallDescriptorWrapperImpl?,
    val cancelWrapperImpl: IrCancelDescriptorWrapperImpl?,
    val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptor

class IrConstructorDescriptor(
    callWrapperImpl: IrCallDescriptorWrapperImpl?,
    parameters: List<IrFunctionTypeParameter>,
    returnTypeName: IrTypeName,
) : IrInvokableDescriptor(null, callWrapperImpl, null, parameters, returnTypeName)

class IrMethodDescriptor(
    val name: String,
    bodyWrapperImpl: IrBodyDescriptorWrapperImpl?,
    callWrapperImpl: IrCallDescriptorWrapperImpl?,
    cancelWrapperImpl: IrCancelDescriptorWrapperImpl?,
    parameters: List<IrFunctionTypeParameter>,
    returnTypeName: IrTypeName?,
) : IrInvokableDescriptor(
    bodyWrapperImpl,
    callWrapperImpl,
    cancelWrapperImpl,
    parameters,
    returnTypeName,
)

class IrFieldDescriptor(
    val name: String,
    val fieldGetWrapperImpl: IrFieldGetDescriptorWrapperImpl?,
    val fieldSetWrapperImpl: IrFieldSetDescriptorWrapperImpl?,
    val arrayGetWrapperImpl: IrArrayGetDescriptorWrapperImpl?,
    val arraySetWrapperImpl: IrArraySetDescriptorWrapperImpl?,
    val typeName: IrTypeName,
) : IrDescriptor
