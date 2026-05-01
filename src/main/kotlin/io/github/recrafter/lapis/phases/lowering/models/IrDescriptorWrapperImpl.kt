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

class IrBodyDescriptorWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl(className, descriptorClassName, wrapperBuiltinClassName, null),
    IrInvokableDescriptorWrapperImpl

class IrFieldGetDescriptorWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescriptorWrapperImpl(className, descriptorClassName, wrapperBuiltinClassName, receiverTypeName)

class IrFieldSetDescriptorWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescriptorWrapperImpl(className, descriptorClassName, wrapperBuiltinClassName, receiverTypeName)

class IrArrayGetDescriptorWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    val arrayTypeName: IrTypeName,
    val arrayComponentTypeName: IrTypeName,
) : IrDescriptorWrapperImpl(className, descriptorClassName, wrapperBuiltinClassName, null)

class IrArraySetDescriptorWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    val arrayTypeName: IrTypeName,
    val arrayComponentTypeName: IrTypeName,
) : IrDescriptorWrapperImpl(className, descriptorClassName, wrapperBuiltinClassName, null)

class IrCallDescriptorWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    receiverTypeName: IrTypeName?,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl(className, descriptorClassName, wrapperBuiltinClassName, receiverTypeName),
    IrInvokableDescriptorWrapperImpl

class IrCancelDescriptorWrapperImpl(
    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl(className, descriptorClassName, wrapperBuiltinClassName, null),
    IrInvokableDescriptorWrapperImpl
