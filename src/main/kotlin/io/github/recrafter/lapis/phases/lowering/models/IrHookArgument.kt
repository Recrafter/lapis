package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.builtins.LocalVarImplBuiltin

sealed interface IrHookArgument

sealed interface IrHookOriginArgument : IrHookArgument
object IrHookOriginValueArgument : IrHookOriginArgument

sealed class IrHookOriginDescriptorWrapperImplArgument<T : IrDescriptorWrapperImpl>(
    open val wrapperImpl: T
) : IrHookOriginArgument

class IrHookOriginDescriptorBodyWrapperImplArgument(
    override val wrapperImpl: IrDescriptorBodyWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrDescriptorBodyWrapperImpl>(wrapperImpl)

class IrHookOriginDescriptorFieldGetWrapperImplArgument(
    override val wrapperImpl: IrDescriptorFieldGetWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrDescriptorFieldGetWrapperImpl>(wrapperImpl)

class IrHookOriginDescriptorFieldSetWrapperImplArgument(
    override val wrapperImpl: IrDescriptorFieldSetWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrDescriptorFieldSetWrapperImpl>(wrapperImpl)

class IrHookOriginDescriptorArrayGetWrapperImplArgument(
    override val wrapperImpl: IrDescriptorArrayGetWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrDescriptorArrayGetWrapperImpl>(wrapperImpl)

class IrHookOriginDescriptorArraySetWrapperImplArgument(
    override val wrapperImpl: IrDescriptorArraySetWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrDescriptorArraySetWrapperImpl>(wrapperImpl)

class IrHookOriginDescriptorCallWrapperImplArgument(
    override val wrapperImpl: IrDescriptorCallWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrDescriptorCallWrapperImpl>(wrapperImpl)

object IrHookOriginInstanceofArgument : IrHookOriginArgument
class IrHookCancelArgument(val wrapperImpl: IrDescriptorCancelWrapperImpl) : IrHookArgument
object IrHookOrdinalArgument : IrHookArgument

class IrHookLocalArgument(
    val name: String,
    val isBody: Boolean,
    val isShare: Boolean,
    val varBuiltin: LocalVarImplBuiltin?,
) : IrHookArgument
