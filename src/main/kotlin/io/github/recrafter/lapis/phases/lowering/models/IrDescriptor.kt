package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed class IrDescriptor(
    val makePublic: Boolean,
    val removeFinal: Boolean,
)

sealed class IrInvokableDescriptor(
    makePublic: Boolean,
    removeFinal: Boolean,
    val bodyWrapperImpl: IrBodyDescriptorWrapperImpl?,
    val callWrapperImpl: IrCallDescriptorWrapperImpl?,
    val cancelWrapperImpl: IrCancelDescriptorWrapperImpl?,
    val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptor(makePublic, removeFinal)

class IrConstructorDescriptor(
    makePublic: Boolean,
    callWrapperImpl: IrCallDescriptorWrapperImpl?,
    parameters: List<IrFunctionTypeParameter>,
    returnTypeName: IrTypeName,
) : IrInvokableDescriptor(makePublic, false, null, callWrapperImpl, null, parameters, returnTypeName)

class IrMethodDescriptor(
    makePublic: Boolean,
    removeFinal: Boolean,
    val name: String,
    val mappingName: String,
    bodyWrapperImpl: IrBodyDescriptorWrapperImpl?,
    callWrapperImpl: IrCallDescriptorWrapperImpl?,
    cancelWrapperImpl: IrCancelDescriptorWrapperImpl?,
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
    val mappingName: String,
    val fieldGetWrapperImpl: IrFieldGetDescriptorWrapperImpl?,
    val fieldSetWrapperImpl: IrFieldSetDescriptorWrapperImpl?,
    val arrayGetWrapperImpl: IrArrayGetDescriptorWrapperImpl?,
    val arraySetWrapperImpl: IrArraySetDescriptorWrapperImpl?,
    val typeName: IrTypeName,
) : IrDescriptor(makePublic, removeFinal)
