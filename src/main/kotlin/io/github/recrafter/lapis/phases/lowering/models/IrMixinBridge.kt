package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

class IrMixinBridge(
    override val originatingFiles: List<KSFile>,

    override val className: IrClassName,
    val functions: List<IrMixinBridgeFunction>,
) : IrGeneratedSourceFile

sealed class IrMixinBridgeFunction(
    val sourceName: String,
    open val impl: IrMixinBridgeFunctionImpl,
) {
    abstract val kinds: List<IrMixinBridgeFunctionKind>
}

sealed interface IrMixinBridgeFunctionKind {
    val name: String
    val sourceJvmName: String
    val parameters: List<IrParameter>
    val returnTypeName: IrTypeName?
}

class IrMixinBridgeFunctionPropertyGetter(
    override val name: String,
    override val sourceJvmName: String,
    typeName: IrTypeName,
) : IrMixinBridgeFunctionKind {
    override val parameters: List<IrParameter> = emptyList()
    override val returnTypeName: IrTypeName = typeName
}

class IrMixinBridgeFunctionPropertySetter(
    override val name: String,
    override val sourceJvmName: String,
    typeName: IrTypeName,
) : IrMixinBridgeFunctionKind {
    override val parameters: List<IrParameter> = listOf(IrSetterParameter(typeName))
    override val returnTypeName: IrTypeName? = null
}

class IrMixinBridgeFunctionProperty(
    sourceName: String,
    val typeName: IrTypeName,
    override val impl: IrMixinBridgeFunctionPropertyImpl,
    val getterName: String,
    val getterSourceJvmName: String,
    setterName: String?,
    setterSourceJvmName: String?,
) : IrMixinBridgeFunction(sourceName, impl) {
    val getter: IrMixinBridgeFunctionPropertyGetter = IrMixinBridgeFunctionPropertyGetter(
        getterName, getterSourceJvmName, typeName
    )
    val setter: IrMixinBridgeFunctionPropertySetter? = if (setterName != null && setterSourceJvmName != null) {
        IrMixinBridgeFunctionPropertySetter(setterName, setterSourceJvmName, typeName)
    } else null

    override val kinds: List<IrMixinBridgeFunctionKind> = listOfNotNull(getter, setter)
}

class IrMixinBridgeFunctionFunction(
    sourceName: String,
    override val name: String,
    override val sourceJvmName: String,
    override val parameters: List<IrParameter>,
    override val returnTypeName: IrTypeName?,
    override val impl: IrMixinBridgeFunctionFunctionImpl,
) : IrMixinBridgeFunction(sourceName, impl), IrMixinBridgeFunctionKind {
    override val kinds: List<IrMixinBridgeFunctionKind> = listOf(this)
}

sealed interface IrMixinBridgeFunctionImpl
sealed interface IrMixinBridgeFunctionPropertyImpl : IrMixinBridgeFunctionImpl
sealed interface IrMixinBridgeFunctionFunctionImpl : IrMixinBridgeFunctionImpl

sealed interface IrMixinBridgeFunctionExtensionImpl
object IrMixinBridgeFunctionPropertyExtensionImpl : IrMixinBridgeFunctionPropertyImpl,
    IrMixinBridgeFunctionExtensionImpl

object IrMixinBridgeFunctionFunctionExtensionImpl : IrMixinBridgeFunctionFunctionImpl,
    IrMixinBridgeFunctionExtensionImpl
