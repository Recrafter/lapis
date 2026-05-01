package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.builtins.LocalVarImplBuiltin

sealed interface IrHookArgument

sealed interface IrHookOriginArgument : IrHookArgument
object IrHookOriginValueArgument : IrHookOriginArgument

sealed class IrHookOriginDescriptorWrapperImplArgument<T : IrDescriptorWrapperImpl>(
    open val wrapperImpl: T
) : IrHookOriginArgument

class IrHookOriginBodyDescriptorWrapperImplArgument(
    override val wrapperImpl: IrBodyDescriptorWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrBodyDescriptorWrapperImpl>(wrapperImpl)

class IrHookOriginFieldGetDescriptorWrapperImplArgument(
    override val wrapperImpl: IrFieldGetDescriptorWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrFieldGetDescriptorWrapperImpl>(wrapperImpl)

class IrHookOriginFieldSetDescriptorWrapperImplArgument(
    override val wrapperImpl: IrFieldSetDescriptorWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrFieldSetDescriptorWrapperImpl>(wrapperImpl)

class IrHookOriginArrayGetDescriptorWrapperImplArgument(
    override val wrapperImpl: IrArrayGetDescriptorWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrArrayGetDescriptorWrapperImpl>(wrapperImpl)

class IrHookOriginArraySetDescriptorWrapperImplArgument(
    override val wrapperImpl: IrArraySetDescriptorWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrArraySetDescriptorWrapperImpl>(wrapperImpl)

class IrHookOriginCallDescriptorWrapperImplArgument(
    override val wrapperImpl: IrCallDescriptorWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrCallDescriptorWrapperImpl>(wrapperImpl)

object IrHookOriginInstanceofWrapperImplArgument : IrHookOriginArgument
class IrHookCancelDescriptorWrapperImplArgument(val wrapperImpl: IrCancelDescriptorWrapperImpl) : IrHookArgument
object IrHookOrdinalArgument : IrHookArgument

class IrHookLocalArgument(
    val name: String,
    val isBody: Boolean,
    val isShare: Boolean,
    val varBuiltin: LocalVarImplBuiltin?,
) : IrHookArgument
