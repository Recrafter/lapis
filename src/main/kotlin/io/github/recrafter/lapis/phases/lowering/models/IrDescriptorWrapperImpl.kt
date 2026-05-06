package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.phases.builtins.DescriptorWrapperBuiltin
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed class IrDescriptorWrapperImpl<T : IrDescriptorWrapperImpl<T>>(
    originatingFile: KSFile?,

    val descriptorClassName: IrClassName,
    val wrapperBuiltin: DescriptorWrapperBuiltin<T>,
    val receiverTypeName: IrTypeName?,
) : IrGeneratedSource(originatingFile) {
    override val className = IrClassName.of(
        descriptorClassName.packageName,
        descriptorClassName.nestedName.replace('.', '_') + "_" + wrapperBuiltin.name
    )
}

sealed interface IrInvokableDescriptorWrapperImpl {
    val parameters: List<IrFunctionTypeParameter>
}

class IrBodyDescriptorWrapperImpl(
    originatingFile: KSFile?,

    descriptorClassName: IrClassName,
    wrapperBuiltin: DescriptorWrapperBuiltin<IrBodyDescriptorWrapperImpl>,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl<IrBodyDescriptorWrapperImpl>
    (originatingFile, descriptorClassName, wrapperBuiltin, null),
    IrInvokableDescriptorWrapperImpl

class IrFieldGetDescriptorWrapperImpl(
    originatingFile: KSFile?,

    descriptorClassName: IrClassName,
    wrapperBuiltin: DescriptorWrapperBuiltin<IrFieldGetDescriptorWrapperImpl>,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescriptorWrapperImpl<IrFieldGetDescriptorWrapperImpl>
    (originatingFile, descriptorClassName, wrapperBuiltin, receiverTypeName)

class IrFieldSetDescriptorWrapperImpl(
    originatingFile: KSFile?,

    descriptorClassName: IrClassName,
    wrapperBuiltin: DescriptorWrapperBuiltin<IrFieldSetDescriptorWrapperImpl>,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescriptorWrapperImpl<IrFieldSetDescriptorWrapperImpl>
    (originatingFile, descriptorClassName, wrapperBuiltin, receiverTypeName)

class IrArrayGetDescriptorWrapperImpl(
    originatingFile: KSFile?,

    descriptorClassName: IrClassName,
    wrapperBuiltin: DescriptorWrapperBuiltin<IrArrayGetDescriptorWrapperImpl>,
    val arrayTypeName: IrTypeName,
    val arrayComponentTypeName: IrTypeName,
) : IrDescriptorWrapperImpl<IrArrayGetDescriptorWrapperImpl>
    (originatingFile, descriptorClassName, wrapperBuiltin, null)

class IrArraySetDescriptorWrapperImpl(
    originatingFile: KSFile?,

    descriptorClassName: IrClassName,
    wrapperBuiltin: DescriptorWrapperBuiltin<IrArraySetDescriptorWrapperImpl>,
    val arrayTypeName: IrTypeName,
    val arrayComponentTypeName: IrTypeName,
) : IrDescriptorWrapperImpl<IrArraySetDescriptorWrapperImpl>
    (originatingFile, descriptorClassName, wrapperBuiltin, null)

class IrCallDescriptorWrapperImpl(
    originatingFile: KSFile?,

    descriptorClassName: IrClassName,
    wrapperBuiltin: DescriptorWrapperBuiltin<IrCallDescriptorWrapperImpl>,
    receiverTypeName: IrTypeName?,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl<IrCallDescriptorWrapperImpl>
    (originatingFile, descriptorClassName, wrapperBuiltin, receiverTypeName),
    IrInvokableDescriptorWrapperImpl

class IrCancelDescriptorWrapperImpl(
    originatingFile: KSFile?,

    descriptorClassName: IrClassName,
    wrapperBuiltin: DescriptorWrapperBuiltin<IrCancelDescriptorWrapperImpl>,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl<IrCancelDescriptorWrapperImpl>
    (originatingFile, descriptorClassName, wrapperBuiltin, null),
    IrInvokableDescriptorWrapperImpl
