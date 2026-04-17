package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed class IrDescriptor(
    val makePublic: Boolean,
    val removeFinal: Boolean,
)

sealed class IrInvokableDescriptor(
    makePublic: Boolean,
    removeFinal: Boolean,
    val bodyWrapper: IrDescriptorBodyWrapper?,
    val callWrapper: IrDescriptorCallWrapper?,
    val cancelWrapper: IrDescriptorCancelWrapper?,
    val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptor(makePublic, removeFinal)

class IrConstructorDescriptor(
    makePublic: Boolean,
    callWrapper: IrDescriptorCallWrapper?,
    cancelWrapper: IrDescriptorCancelWrapper?,
    parameters: List<IrFunctionTypeParameter>,
    returnTypeName: IrTypeName,
) : IrInvokableDescriptor(makePublic, false, null, callWrapper, cancelWrapper, parameters, returnTypeName)

class IrMethodDescriptor(
    makePublic: Boolean,
    removeFinal: Boolean,
    val name: String,
    val targetName: String,
    bodyWrapper: IrDescriptorBodyWrapper?,
    callWrapper: IrDescriptorCallWrapper?,
    cancelWrapper: IrDescriptorCancelWrapper?,
    parameters: List<IrFunctionTypeParameter>,
    returnTypeName: IrTypeName?,
) : IrInvokableDescriptor(makePublic, removeFinal, bodyWrapper, callWrapper, cancelWrapper, parameters, returnTypeName)

class IrFieldDescriptor(
    makePublic: Boolean,
    removeFinal: Boolean,
    val name: String,
    val targetName: String,
    val fieldGetWrapper: IrDescriptorFieldGetWrapper?,
    val fieldSetWrapper: IrDescriptorFieldSetWrapper?,
    val arrayGetWrapper: IrDescriptorArrayGetWrapper?,
    val arraySetWrapper: IrDescriptorArraySetWrapper?,
    val typeName: IrTypeName,
) : IrDescriptor(makePublic, removeFinal)
