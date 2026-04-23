package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrParameterizedTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed class IrDescriptorWrapperImpl(
    val className: IrClassName,
    val descriptorClassName: IrClassName,
    builtin: IrClassName,
    val receiverTypeName: IrTypeName?,
) {
    val superClassTypeName: IrParameterizedTypeName = builtin.parameterizedBy(descriptorClassName)
}

sealed interface IrInvokableDescriptorWrapperImpl {
    val parameters: List<IrFunctionTypeParameter>
}

class IrDescriptorBodyWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    builtin: IrClassName,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl(className, descriptorClassName, builtin, null), IrInvokableDescriptorWrapperImpl

class IrDescriptorFieldGetWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    builtin: IrClassName,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescriptorWrapperImpl(className, descriptorClassName, builtin, receiverTypeName)

class IrDescriptorFieldSetWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    builtin: IrClassName,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescriptorWrapperImpl(className, descriptorClassName, builtin, receiverTypeName)

class IrDescriptorArrayGetWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    builtin: IrClassName,
    val arrayTypeName: IrTypeName,
    val arrayComponentTypeName: IrTypeName,
) : IrDescriptorWrapperImpl(className, descriptorClassName, builtin, null)

class IrDescriptorArraySetWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    builtin: IrClassName,
    val arrayTypeName: IrTypeName,
    val arrayComponentTypeName: IrTypeName,
) : IrDescriptorWrapperImpl(className, descriptorClassName, builtin, null)

class IrDescriptorCallWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    builtin: IrClassName,
    receiverTypeName: IrTypeName?,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl(className, descriptorClassName, builtin, receiverTypeName), IrInvokableDescriptorWrapperImpl

class IrDescriptorCancelWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    builtin: IrClassName,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl(className, descriptorClassName, builtin, null), IrInvokableDescriptorWrapperImpl
