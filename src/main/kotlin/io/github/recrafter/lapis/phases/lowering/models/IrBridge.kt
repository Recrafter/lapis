package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

class IrBridge(
    originatingFile: KSFile?,

    val className: IrClassName,
    val functions: List<IrBridgeFunction>,
) : IrGeneratedSource(originatingFile)

sealed class IrBridgeFunction(
    val sourceName: String,
    open val impl: IrBridgeFunctionImpl,
) {
    abstract val accessors: List<IrBridgeFunctionAccessor>
}

sealed interface IrBridgeFunctionAccessor {
    val name: String
    val sourceJvmName: String
    val parameters: List<IrParameter>
    val returnTypeName: IrTypeName?
}

class IrBridgeFunctionPropertyGetter(
    override val name: String,
    override val sourceJvmName: String,
    typeName: IrTypeName,
) : IrBridgeFunctionAccessor {
    override val parameters: List<IrParameter> = emptyList()
    override val returnTypeName: IrTypeName = typeName
}

class IrBridgeFunctionPropertySetter(
    override val name: String,
    override val sourceJvmName: String,
    typeName: IrTypeName,
) : IrBridgeFunctionAccessor {
    override val parameters: List<IrParameter> = listOf(IrSetterParameter(typeName))
    override val returnTypeName: IrTypeName? = null
}

class IrBridgeFunctionProperty(
    sourceName: String,
    val typeName: IrTypeName,
    override val impl: IrBridgeFunctionPropertyImpl,
    val getterName: String,
    val getterSourceJvmName: String,
    setterName: String?,
    setterSourceJvmName: String?,
) : IrBridgeFunction(sourceName, impl) {
    val getter: IrBridgeFunctionPropertyGetter =
        IrBridgeFunctionPropertyGetter(getterName, getterSourceJvmName, typeName)
    val setter: IrBridgeFunctionPropertySetter? = if (setterName != null && setterSourceJvmName != null) {
        IrBridgeFunctionPropertySetter(setterName, setterSourceJvmName, typeName)
    } else null

    override val accessors: List<IrBridgeFunctionAccessor> = listOfNotNull(getter, setter)
}

class IrBridgeFunctionFunction(
    sourceName: String,
    override val name: String,
    override val sourceJvmName: String,
    override val parameters: List<IrParameter>,
    override val returnTypeName: IrTypeName?,
    override val impl: IrBridgeFunctionFunctionImpl,
) : IrBridgeFunction(sourceName, impl), IrBridgeFunctionAccessor {
    override val accessors: List<IrBridgeFunctionAccessor> = listOf(this)
}

sealed interface IrBridgeFunctionImpl
sealed interface IrBridgeFunctionPropertyImpl : IrBridgeFunctionImpl
sealed interface IrBridgeFunctionFunctionImpl : IrBridgeFunctionImpl

sealed interface IrBridgeFunctionExtensionImpl
object IrBridgeFunctionPropertyExtensionImpl : IrBridgeFunctionPropertyImpl, IrBridgeFunctionExtensionImpl
object IrBridgeFunctionFunctionExtensionImpl : IrBridgeFunctionFunctionImpl, IrBridgeFunctionExtensionImpl
