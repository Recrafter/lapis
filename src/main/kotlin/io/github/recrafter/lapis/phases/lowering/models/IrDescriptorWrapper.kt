package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrParameterizedTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed class IrDescriptorWrapper(
    val className: IrClassName,
    val descriptorClassName: IrClassName,
    builtin: IrClassName,
    val receiverTypeName: IrTypeName?,
) {
    val superClassTypeName: IrParameterizedTypeName = builtin.parameterizedBy(descriptorClassName)
}

sealed interface IrInvokableDescWrapper {
    val parameters: List<IrFunctionTypeParameter>
}

class IrDescriptorBodyWrapper(
    className: IrClassName,
    descriptorClassName: IrClassName,
    builtin: IrClassName,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapper(className, descriptorClassName, builtin, null), IrInvokableDescWrapper

class IrDescriptorFieldGetWrapper(
    className: IrClassName,
    descriptorClassName: IrClassName,
    builtin: IrClassName,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescriptorWrapper(className, descriptorClassName, builtin, receiverTypeName)

class IrDescriptorFieldSetWrapper(
    className: IrClassName,
    descriptorClassName: IrClassName,
    builtin: IrClassName,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescriptorWrapper(className, descriptorClassName, builtin, receiverTypeName)

class IrDescriptorArrayGetWrapper(
    className: IrClassName,
    descriptorClassName: IrClassName,
    builtin: IrClassName,
    val arrayTypeName: IrTypeName,
    val arrayComponentTypeName: IrTypeName,
) : IrDescriptorWrapper(className, descriptorClassName, builtin, null)

class IrDescriptorArraySetWrapper(
    className: IrClassName,
    descriptorClassName: IrClassName,
    builtin: IrClassName,
    val arrayTypeName: IrTypeName,
    val arrayComponentTypeName: IrTypeName,
) : IrDescriptorWrapper(className, descriptorClassName, builtin, null)

class IrDescriptorCallWrapper(
    className: IrClassName,
    descriptorClassName: IrClassName,
    builtin: IrClassName,
    receiverTypeName: IrTypeName?,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapper(className, descriptorClassName, builtin, receiverTypeName), IrInvokableDescWrapper

class IrDescriptorCancelWrapper(
    className: IrClassName,
    descriptorClassName: IrClassName,
    builtin: IrClassName,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapper(className, descriptorClassName, builtin, null), IrInvokableDescWrapper
