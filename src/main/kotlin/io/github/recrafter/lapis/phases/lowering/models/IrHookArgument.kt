package io.github.recrafter.lapis.phases.lowering.models

sealed interface IrHookArgument

sealed interface IrHookOriginArgument : IrHookArgument
object IrHookOriginValueArgument : IrHookOriginArgument

sealed class IrHookOriginDescriptorWrapperArgument<W : IrDescriptorWrapper>(open val wrapper: W) : IrHookOriginArgument

class IrHookOriginDescriptorBodyWrapperArgument(override val wrapper: IrDescriptorBodyWrapper) :
    IrHookOriginDescriptorWrapperArgument<IrDescriptorBodyWrapper>(wrapper)

class IrHookOriginDescriptorFieldGetWrapperArgument(override val wrapper: IrDescriptorFieldGetWrapper) :
    IrHookOriginDescriptorWrapperArgument<IrDescriptorFieldGetWrapper>(wrapper)

class IrHookOriginDescriptorFieldSetWrapperArgument(override val wrapper: IrDescriptorFieldSetWrapper) :
    IrHookOriginDescriptorWrapperArgument<IrDescriptorFieldSetWrapper>(wrapper)

class IrHookOriginDescriptorArrayGetWrapperArgument(override val wrapper: IrDescriptorArrayGetWrapper) :
    IrHookOriginDescriptorWrapperArgument<IrDescriptorArrayGetWrapper>(wrapper)

class IrHookOriginDescriptorArraySetWrapperArgument(override val wrapper: IrDescriptorArraySetWrapper) :
    IrHookOriginDescriptorWrapperArgument<IrDescriptorArraySetWrapper>(wrapper)

class IrHookOriginDescriptorCallWrapperArgument(override val wrapper: IrDescriptorCallWrapper) :
    IrHookOriginDescriptorWrapperArgument<IrDescriptorCallWrapper>(wrapper)

object IrHookOriginInstanceofArgument : IrHookOriginArgument
class IrHookCancelArgument(val wrapper: IrDescriptorCancelWrapper) : IrHookArgument
object IrHookOrdinalArgument : IrHookArgument
class IrHookParamArgument(val name: String) : IrHookArgument
class IrHookLocalArgument(val name: String) : IrHookArgument
