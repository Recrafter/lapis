package io.github.recrafter.lapis.layers.lowering.models

import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import io.github.recrafter.lapis.layers.lowering.types.IrParameterizedTypeName
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName

sealed class IrDescWrapper(
    val className: IrClassName,
    val descClassName: IrClassName,
    builtin: IrClassName,
    val receiverTypeName: IrTypeName?,
) {
    val superClassTypeName: IrParameterizedTypeName = builtin.parameterizedBy(descClassName)
}

sealed interface IrInvokableDescWrapper {
    val parameters: List<IrFunctionTypeParameter>
}

class IrDescBodyWrapper(
    className: IrClassName,
    descClassName: IrClassName,
    builtin: IrClassName,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescWrapper(className, descClassName, builtin, null), IrInvokableDescWrapper

class IrDescFieldGetWrapper(
    className: IrClassName,
    descClassName: IrClassName,
    builtin: IrClassName,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescWrapper(className, descClassName, builtin, receiverTypeName)

class IrDescFieldSetWrapper(
    className: IrClassName,
    descClassName: IrClassName,
    builtin: IrClassName,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescWrapper(className, descClassName, builtin, receiverTypeName)

class IrDescArrayGetWrapper(
    className: IrClassName,
    descClassName: IrClassName,
    builtin: IrClassName,
    val arrayTypeName: IrTypeName,
    val arrayComponentTypeName: IrTypeName,
) : IrDescWrapper(className, descClassName, builtin, null)

class IrDescArraySetWrapper(
    className: IrClassName,
    descClassName: IrClassName,
    builtin: IrClassName,
    val arrayTypeName: IrTypeName,
    val arrayComponentTypeName: IrTypeName,
) : IrDescWrapper(className, descClassName, builtin, null)

class IrDescCallWrapper(
    className: IrClassName,
    descClassName: IrClassName,
    builtin: IrClassName,
    receiverTypeName: IrTypeName?,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescWrapper(className, descClassName, builtin, receiverTypeName), IrInvokableDescWrapper

class IrDescCancelWrapper(
    className: IrClassName,
    descClassName: IrClassName,
    builtin: IrClassName,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescWrapper(className, descClassName, builtin, null), IrInvokableDescWrapper
