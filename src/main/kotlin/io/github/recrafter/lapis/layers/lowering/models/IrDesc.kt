package io.github.recrafter.lapis.layers.lowering.models

import io.github.recrafter.lapis.layers.lowering.types.IrTypeName

sealed class IrDesc(
    val makePublic: Boolean,
    val removeFinal: Boolean,
)

sealed class IrInvokableDesc(
    makePublic: Boolean,
    removeFinal: Boolean,
    val bodyWrapper: IrDescBodyWrapper?,
    val callWrapper: IrDescCallWrapper?,
    val cancelWrapper: IrDescCancelWrapper?,
    val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDesc(makePublic, removeFinal)

class IrConstructorDesc(
    makePublic: Boolean,
    callWrapper: IrDescCallWrapper?,
    cancelWrapper: IrDescCancelWrapper?,
    parameters: List<IrFunctionTypeParameter>,
    returnTypeName: IrTypeName,
) : IrInvokableDesc(makePublic, false, null, callWrapper, cancelWrapper, parameters, returnTypeName)

class IrMethodDesc(
    makePublic: Boolean,
    removeFinal: Boolean,
    val name: String,
    val targetName: String,
    bodyWrapper: IrDescBodyWrapper?,
    callWrapper: IrDescCallWrapper?,
    cancelWrapper: IrDescCancelWrapper?,
    parameters: List<IrFunctionTypeParameter>,
    returnTypeName: IrTypeName?,
) : IrInvokableDesc(makePublic, removeFinal, bodyWrapper, callWrapper, cancelWrapper, parameters, returnTypeName)

class IrFieldDesc(
    makePublic: Boolean,
    removeFinal: Boolean,
    val name: String,
    val targetName: String,
    val fieldGetWrapper: IrDescFieldGetWrapper?,
    val fieldSetWrapper: IrDescFieldSetWrapper?,
    val arrayGetWrapper: IrDescArrayGetWrapper?,
    val arraySetWrapper: IrDescArraySetWrapper?,
    val typeName: IrTypeName,
) : IrDesc(makePublic, removeFinal)
