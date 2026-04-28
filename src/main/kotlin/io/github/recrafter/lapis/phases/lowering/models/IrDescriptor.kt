package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed class IrDescriptor(
    val makePublic: Boolean,
    val removeFinal: Boolean,
)

sealed class IrInvokableDescriptor(
    makePublic: Boolean,
    removeFinal: Boolean,
    val bodyWrapperImpl: IrDescriptorBodyWrapperImpl?,
    val callWrapperImpl: IrDescriptorCallWrapperImpl?,
    val cancelWrapperImpl: IrDescriptorCancelWrapperImpl?,
    val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptor(makePublic, removeFinal)

class IrConstructorDescriptor(
    makePublic: Boolean,
    callWrapperImpl: IrDescriptorCallWrapperImpl?,
    parameters: List<IrFunctionTypeParameter>,
    returnTypeName: IrTypeName,
) : IrInvokableDescriptor(makePublic, false, null, callWrapperImpl, null, parameters, returnTypeName)

class IrMethodDescriptor(
    makePublic: Boolean,
    removeFinal: Boolean,
    val name: String,
    val bytecodeName: String,
    bodyWrapperImpl: IrDescriptorBodyWrapperImpl?,
    callWrapperImpl: IrDescriptorCallWrapperImpl?,
    cancelWrapperImpl: IrDescriptorCancelWrapperImpl?,
    parameters: List<IrFunctionTypeParameter>,
    returnTypeName: IrTypeName?,
) : IrInvokableDescriptor(
    makePublic,
    removeFinal,
    bodyWrapperImpl,
    callWrapperImpl,
    cancelWrapperImpl,
    parameters,
    returnTypeName
)

class IrFieldDescriptor(
    makePublic: Boolean,
    removeFinal: Boolean,
    val name: String,
    val bytecodeName: String,
    val fieldGetWrapperImpl: IrDescriptorFieldGetWrapperImpl?,
    val fieldSetWrapperImpl: IrDescriptorFieldSetWrapperImpl?,
    val arrayGetWrapperImpl: IrDescriptorArrayGetWrapperImpl?,
    val arraySetWrapperImpl: IrDescriptorArraySetWrapperImpl?,
    val typeName: IrTypeName,
) : IrDescriptor(makePublic, removeFinal)
