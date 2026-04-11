package io.github.recrafter.lapis.layers.lowering.models

sealed interface IrHookArgument

sealed interface IrHookOriginArgument : IrHookArgument
class IrHookOriginValueArgument : IrHookOriginArgument

sealed class IrHookOriginDescWrapperArgument<W : IrDescWrapper>(open val wrapper: W) : IrHookOriginArgument

class IrHookOriginDescBodyWrapperArgument(override val wrapper: IrDescBodyWrapper) :
    IrHookOriginDescWrapperArgument<IrDescBodyWrapper>(wrapper)

class IrHookOriginDescFieldGetWrapperArgument(override val wrapper: IrDescFieldGetWrapper) :
    IrHookOriginDescWrapperArgument<IrDescFieldGetWrapper>(wrapper)

class IrHookOriginDescFieldSetWrapperArgument(override val wrapper: IrDescFieldSetWrapper) :
    IrHookOriginDescWrapperArgument<IrDescFieldSetWrapper>(wrapper)

class IrHookOriginDescArrayGetWrapperArgument(override val wrapper: IrDescArrayGetWrapper) :
    IrHookOriginDescWrapperArgument<IrDescArrayGetWrapper>(wrapper)

class IrHookOriginDescArraySetWrapperArgument(override val wrapper: IrDescArraySetWrapper) :
    IrHookOriginDescWrapperArgument<IrDescArraySetWrapper>(wrapper)

class IrHookOriginDescCallWrapperArgument(override val wrapper: IrDescCallWrapper) :
    IrHookOriginDescWrapperArgument<IrDescCallWrapper>(wrapper)

class IrHookCancelArgument(val wrapper: IrDescCancelWrapper) : IrHookArgument
object IrHookOrdinalArgument : IrHookArgument
class IrHookParamArgument(val name: String) : IrHookArgument
class IrHookLocalArgument(val name: String) : IrHookArgument
