package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.builtins.LocalVarImplBuiltin

sealed interface IrHookArgument

sealed interface IrHookOriginArgument : IrHookArgument
object IrHookOriginValueArgument : IrHookOriginArgument

sealed class IrHookOriginDescriptorWrapperImplArgument<T : IrDescriptorWrapperImpl>(
    open val impl: T
) : IrHookOriginArgument

class IrHookOriginDescriptorBodyWrapperImplArgument(
    override val impl: IrDescriptorBodyWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrDescriptorBodyWrapperImpl>(impl)

class IrHookOriginDescriptorFieldGetWrapperImplArgument(
    override val impl: IrDescriptorFieldGetWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrDescriptorFieldGetWrapperImpl>(impl)

class IrHookOriginDescriptorFieldSetWrapperImplArgument(
    override val impl: IrDescriptorFieldSetWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrDescriptorFieldSetWrapperImpl>(impl)

class IrHookOriginDescriptorArrayGetWrapperImplArgument(
    override val impl: IrDescriptorArrayGetWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrDescriptorArrayGetWrapperImpl>(impl)

class IrHookOriginDescriptorArraySetWrapperImplArgument(
    override val impl: IrDescriptorArraySetWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrDescriptorArraySetWrapperImpl>(impl)

class IrHookOriginDescriptorCallWrapperImplArgument(
    override val impl: IrDescriptorCallWrapperImpl
) : IrHookOriginDescriptorWrapperImplArgument<IrDescriptorCallWrapperImpl>(impl)

object IrHookOriginInstanceofArgument : IrHookOriginArgument
class IrHookCancelArgument(val impl: IrDescriptorCancelWrapperImpl) : IrHookArgument
object IrHookOrdinalArgument : IrHookArgument

class IrHookLocalArgument(
    val name: String,
    val isBody: Boolean,
    val isShare: Boolean,
    val varBuiltin: LocalVarImplBuiltin?,
) : IrHookArgument
