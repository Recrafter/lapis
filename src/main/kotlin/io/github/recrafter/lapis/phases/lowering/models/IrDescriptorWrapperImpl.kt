package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrParameterizedTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed class IrDescriptorWrapperImpl(
    val className: IrClassName,
    val descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    val receiverTypeName: IrTypeName?,
) {
    val superClassTypeName: IrParameterizedTypeName = wrapperBuiltinClassName.parameterizedBy(descriptorClassName)
}

sealed interface IrInvokableDescriptorWrapperImpl {
    val parameters: List<IrFunctionTypeParameter>
}

class IrDescriptorBodyWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl(className, descriptorClassName, wrapperBuiltinClassName, null),
    IrInvokableDescriptorWrapperImpl

class IrDescriptorFieldGetWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescriptorWrapperImpl(className, descriptorClassName, wrapperBuiltinClassName, receiverTypeName)

class IrDescriptorFieldSetWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescriptorWrapperImpl(className, descriptorClassName, wrapperBuiltinClassName, receiverTypeName)

class IrDescriptorArrayGetWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    val arrayTypeName: IrTypeName,
    val arrayComponentTypeName: IrTypeName,
) : IrDescriptorWrapperImpl(className, descriptorClassName, wrapperBuiltinClassName, null)

class IrDescriptorArraySetWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    val arrayTypeName: IrTypeName,
    val arrayComponentTypeName: IrTypeName,
) : IrDescriptorWrapperImpl(className, descriptorClassName, wrapperBuiltinClassName, null)

class IrDescriptorCallWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    receiverTypeName: IrTypeName?,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl(className, descriptorClassName, wrapperBuiltinClassName, receiverTypeName),
    IrInvokableDescriptorWrapperImpl

class IrDescriptorCancelWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl(className, descriptorClassName, wrapperBuiltinClassName, null),
    IrInvokableDescriptorWrapperImpl
