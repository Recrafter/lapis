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
) : IrKotlinClassBlueprint(IrKotlinClassKind.CLASS) {
    override val className: IrClassName = descriptorClassName.derived(wrapperBuiltin.name)
}

sealed interface IrInvokableDescriptorWrapperImpl : IrReturnable {
    val parameters: List<IrFunctionTypeParameter>
}

class IrFieldGetDescriptorWrapperImpl(
    originatingFiles: List<KSFile>,

    descriptorClassName: IrClassName,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescriptorWrapperImpl<IrFieldGetDescriptorWrapperImpl>(
    originatingFiles, descriptorClassName, DescriptorWrapperBuiltin.FieldGet, receiverTypeName
)

class IrFieldSetDescriptorWrapperImpl(
    originatingFiles: List<KSFile>,

    descriptorClassName: IrClassName,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescriptorWrapperImpl<IrFieldSetDescriptorWrapperImpl>(
    originatingFiles, descriptorClassName, DescriptorWrapperBuiltin.FieldSet, receiverTypeName
)

class IrArrayGetDescriptorWrapperImpl(
    originatingFiles: List<KSFile>,

    descriptorClassName: IrClassName,
    val typeName: IrTypeName,
    val componentTypeName: IrTypeName,
) : IrDescriptorWrapperImpl<IrArrayGetDescriptorWrapperImpl>(
    originatingFiles, descriptorClassName, DescriptorWrapperBuiltin.ArrayGet, null
)

class IrArraySetDescriptorWrapperImpl(
    originatingFiles: List<KSFile>,

    descriptorClassName: IrClassName,
    val typeName: IrTypeName,
    val componentTypeName: IrTypeName,
) : IrDescriptorWrapperImpl<IrArraySetDescriptorWrapperImpl>(
    originatingFiles, descriptorClassName, DescriptorWrapperBuiltin.ArraySet, null
)

class IrBodyDescriptorWrapperImpl(
    originatingFiles: List<KSFile>,

    descriptorClassName: IrClassName,
    override val parameters: List<IrFunctionTypeParameter>,
    override val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl<IrBodyDescriptorWrapperImpl>(
    originatingFiles, descriptorClassName, DescriptorWrapperBuiltin.Body, null
), IrInvokableDescriptorWrapperImpl

class IrCallDescriptorWrapperImpl(
    originatingFiles: List<KSFile>,

    descriptorClassName: IrClassName,
    receiverTypeName: IrTypeName?,
    override val parameters: List<IrFunctionTypeParameter>,
    override val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl<IrCallDescriptorWrapperImpl>(
    originatingFiles, descriptorClassName, DescriptorWrapperBuiltin.Call, receiverTypeName
), IrInvokableDescriptorWrapperImpl

class IrCancelDescriptorWrapperImpl(
    originatingFiles: List<KSFile>,

    descriptorClassName: IrClassName,
    override val parameters: List<IrFunctionTypeParameter>,
    override val returnTypeName: IrTypeName?,
) : IrDescriptorWrapperImpl<IrCancelDescriptorWrapperImpl>(
    originatingFiles, descriptorClassName, DescriptorWrapperBuiltin.Cancel, null
), IrInvokableDescriptorWrapperImpl
