package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

class IrBridge(
    val className: IrClassName,
    val functions: List<IrBridgeFunction>,
)

sealed class IrBridgeFunction(
    val sourceName: String,
    open val impl: IrBridgeFunctionImpl,
)

class IrPropertyBridgeFunction(
    sourceName: String,
    val typeName: IrTypeName,
    override val impl: IrPropertyBridgeFunctionImpl,
    val getterName: String,
    val getterSourceJvmName: String,
    setterName: String?,
    setterSourceJvmName: String?,
) : IrBridgeFunction(sourceName, impl) {
    val getter: IrPropertyBridgeFunctionGetter by lazy {
        IrPropertyBridgeFunctionGetter(getterName, getterSourceJvmName, typeName)
    }
    val setter: IrPropertyBridgeFunctionSetter? by lazy {
        if (setterName != null && setterSourceJvmName != null) {
            IrPropertyBridgeFunctionSetter(setterName, setterSourceJvmName, typeName)
        } else null
    }
}

sealed interface IrPropertyBridgeFunctionAccessor {
    val name: String
    val sourceJvmName: String
    val parameters: List<IrParameter>
    val returnTypeName: IrTypeName?
}

class IrPropertyBridgeFunctionGetter(
    override val name: String,
    override val sourceJvmName: String,
    typeName: IrTypeName,
) : IrPropertyBridgeFunctionAccessor {
    override val parameters: List<IrParameter> = emptyList()
    override val returnTypeName: IrTypeName = typeName
}

class IrPropertyBridgeFunctionSetter(
    override val name: String,
    override val sourceJvmName: String,
    typeName: IrTypeName,
) : IrPropertyBridgeFunctionAccessor {
    override val parameters: List<IrParameter> = listOf(IrSetterParameter(typeName))
    override val returnTypeName: IrTypeName? = null
}

class IrFunctionBridgeFunction(
    sourceName: String,
    override val name: String,
    override val sourceJvmName: String,
    override val parameters: List<IrParameter>,
    override val returnTypeName: IrTypeName?,
    override val impl: IrFunctionBridgeFunctionImpl,
) : IrBridgeFunction(sourceName, impl), IrPropertyBridgeFunctionAccessor

sealed interface IrBridgeFunctionImpl
sealed interface IrPropertyBridgeFunctionImpl : IrBridgeFunctionImpl
sealed interface IrFunctionBridgeFunctionImpl : IrBridgeFunctionImpl

sealed interface IrBridgeExtensionFunctionImpl
object IrPropertyBridgeExtensionFunctionImpl : IrPropertyBridgeFunctionImpl, IrBridgeExtensionFunctionImpl
object IrFunctionBridgeExtensionFunctionImpl : IrFunctionBridgeFunctionImpl, IrBridgeExtensionFunctionImpl
