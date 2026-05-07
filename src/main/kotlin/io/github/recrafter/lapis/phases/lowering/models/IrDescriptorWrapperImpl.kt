package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.phases.builtins.DescriptorWrapperBuiltin
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed class IrDescriptorWrapperImpl<T : IrDescriptorWrapperImpl<T>>(
    override val originatingFiles: List<KSFile>,

    val descriptorClassName: IrClassName,
    val wrapperBuiltin: DescriptorWrapperBuiltin<T>,
    val receiverTypeName: IrTypeName?,
) : IrKotlinBlueprint() {
    override val className: IrClassName = descriptorClassName.derived(wrapperBuiltin.name)
}

sealed interface IrInvokableDescriptorWrapperImpl {
    val parameters: List<IrFunctionTypeParameter>
}

class IrBodyDescriptorWrapperImpl(
    originatingFiles: List<KSFile>,

    descriptorClassName: IrClassName,
    wrapperBuiltin: DescriptorWrapperBuiltin<IrBodyDescriptorWrapperImpl>,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl<IrBodyDescriptorWrapperImpl>(
    originatingFiles, descriptorClassName, wrapperBuiltin, null
), IrInvokableDescriptorWrapperImpl

class IrFieldGetDescriptorWrapperImpl(
    originatingFiles: List<KSFile>,

    descriptorClassName: IrClassName,
    wrapperBuiltin: DescriptorWrapperBuiltin<IrFieldGetDescriptorWrapperImpl>,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescriptorWrapperImpl<IrFieldGetDescriptorWrapperImpl>(
    originatingFiles, descriptorClassName, wrapperBuiltin, receiverTypeName
)

class IrFieldSetDescriptorWrapperImpl(
    originatingFiles: List<KSFile>,

    descriptorClassName: IrClassName,
    wrapperBuiltin: DescriptorWrapperBuiltin<IrFieldSetDescriptorWrapperImpl>,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescriptorWrapperImpl<IrFieldSetDescriptorWrapperImpl>(
    originatingFiles, descriptorClassName, wrapperBuiltin, receiverTypeName
)

class IrArrayGetDescriptorWrapperImpl(
    originatingFiles: List<KSFile>,

    descriptorClassName: IrClassName,
    wrapperBuiltin: DescriptorWrapperBuiltin<IrArrayGetDescriptorWrapperImpl>,
    val typeName: IrTypeName,
    val componentTypeName: IrTypeName,
) : IrDescriptorWrapperImpl<IrArrayGetDescriptorWrapperImpl>(
    originatingFiles, descriptorClassName, wrapperBuiltin, null
)

class IrArraySetDescriptorWrapperImpl(
    originatingFiles: List<KSFile>,

    descriptorClassName: IrClassName,
    wrapperBuiltin: DescriptorWrapperBuiltin<IrArraySetDescriptorWrapperImpl>,
    val typeName: IrTypeName,
    val componentTypeName: IrTypeName,
) : IrDescriptorWrapperImpl<IrArraySetDescriptorWrapperImpl>(
    originatingFiles, descriptorClassName, wrapperBuiltin, null
)

class IrCallDescriptorWrapperImpl(
    originatingFiles: List<KSFile>,

    descriptorClassName: IrClassName,
    wrapperBuiltin: DescriptorWrapperBuiltin<IrCallDescriptorWrapperImpl>,
    receiverTypeName: IrTypeName?,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl<IrCallDescriptorWrapperImpl>(
    originatingFiles, descriptorClassName, wrapperBuiltin, receiverTypeName
), IrInvokableDescriptorWrapperImpl

class IrCancelDescriptorWrapperImpl(
    originatingFiles: List<KSFile>,

    descriptorClassName: IrClassName,
    wrapperBuiltin: DescriptorWrapperBuiltin<IrCancelDescriptorWrapperImpl>,
    override val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl<IrCancelDescriptorWrapperImpl>(
    originatingFiles, descriptorClassName, wrapperBuiltin, null
), IrInvokableDescriptorWrapperImpl
