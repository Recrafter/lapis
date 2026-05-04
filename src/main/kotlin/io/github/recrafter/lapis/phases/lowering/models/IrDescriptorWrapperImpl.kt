package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrParameterizedTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed class IrDescriptorWrapperImpl(
    originatingFile: KSFile?,

    val className: IrClassName,
    val descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    val receiverTypeName: IrTypeName?,
) : IrGeneratedSource(originatingFile) {
    val superClassTypeName: IrParameterizedTypeName = wrapperBuiltinClassName.parameterizedBy(descriptorClassName)
}

sealed interface IrInvokableDescriptorWrapperImpl {
    val parameters: List<IrFunctionTypeParameter>
}

class IrBodyDescriptorWrapperImpl(
    originatingFile: KSFile?,

    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl(originatingFile, className, descriptorClassName, wrapperBuiltinClassName, null),
    IrInvokableDescriptorWrapperImpl

class IrFieldGetDescriptorWrapperImpl(
    originatingFile: KSFile?,

    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescriptorWrapperImpl(originatingFile, className, descriptorClassName, wrapperBuiltinClassName, receiverTypeName)

class IrFieldSetDescriptorWrapperImpl(
    originatingFile: KSFile?,

    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescriptorWrapperImpl(originatingFile, className, descriptorClassName, wrapperBuiltinClassName, receiverTypeName)

class IrArrayGetDescriptorWrapperImpl(
    originatingFile: KSFile?,

    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    val arrayTypeName: IrTypeName,
    val arrayComponentTypeName: IrTypeName,
) : IrDescriptorWrapperImpl(originatingFile, className, descriptorClassName, wrapperBuiltinClassName, null)

class IrArraySetDescriptorWrapperImpl(
    originatingFile: KSFile?,

    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    val arrayTypeName: IrTypeName,
    val arrayComponentTypeName: IrTypeName,
) : IrDescriptorWrapperImpl(originatingFile, className, descriptorClassName, wrapperBuiltinClassName, null)

class IrCallDescriptorWrapperImpl(
    originatingFile: KSFile?,

    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    receiverTypeName: IrTypeName?,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl(originatingFile, className, descriptorClassName, wrapperBuiltinClassName, receiverTypeName),
    IrInvokableDescriptorWrapperImpl

class IrCancelDescriptorWrapperImpl(
    originatingFile: KSFile?,

    className: IrClassName,
    descriptorClassName: IrClassName,
    wrapperBuiltinClassName: IrClassName,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl(originatingFile, className, descriptorClassName, wrapperBuiltinClassName, null),
    IrInvokableDescriptorWrapperImpl
