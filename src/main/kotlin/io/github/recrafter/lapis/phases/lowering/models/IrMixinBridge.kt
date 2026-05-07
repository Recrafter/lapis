package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

class IrMixinBridge(
    override val originatingFiles: List<KSFile>,

    override val className: IrClassName,
    val entries: List<IrMixinBridgeEntry>,
) : IrKotlinBlueprint()

sealed class IrMixinBridgeEntry(
    val sourceName: String,
    open val impl: IrMixinBridgeEntryImpl,
) {
    abstract val kinds: List<IrMixinBridgeEntryKind>
}

sealed interface IrMixinBridgeEntryKind {
    val name: String
    val sourceJvmName: String
    val parameters: List<IrParameter>
    val returnTypeName: IrTypeName?
}

class IrMixinBridgeEntryPropertyGetterKind(
    override val name: String,
    override val sourceJvmName: String,
    typeName: IrTypeName,
) : IrMixinBridgeEntryKind {
    override val parameters: List<IrParameter> = emptyList()
    override val returnTypeName: IrTypeName = typeName
}

class IrMixinBridgeEntryPropertySetterKind(
    override val name: String,
    override val sourceJvmName: String,
    typeName: IrTypeName,
) : IrMixinBridgeEntryKind {
    override val parameters: List<IrParameter> = listOf(IrSetterParameter(typeName))
    override val returnTypeName: IrTypeName? = null
}

class IrMixinBridgeEntryProperty(
    sourceName: String,
    val typeName: IrTypeName,
    override val impl: IrMixinBridgeEntryPropertyImpl,
    val getterName: String,
    val getterSourceJvmName: String,
    setterName: String?,
    setterSourceJvmName: String?,
) : IrMixinBridgeEntry(sourceName, impl) {
    val getter: IrMixinBridgeEntryPropertyGetterKind = IrMixinBridgeEntryPropertyGetterKind(
        getterName, getterSourceJvmName, typeName
    )
    val setter: IrMixinBridgeEntryPropertySetterKind? = if (setterName != null && setterSourceJvmName != null) {
        IrMixinBridgeEntryPropertySetterKind(setterName, setterSourceJvmName, typeName)
    } else null

    override val kinds: List<IrMixinBridgeEntryKind> = listOfNotNull(getter, setter)
}

class IrMixinBridgeEntryFunctionKind(
    sourceName: String,
    override val name: String,
    override val sourceJvmName: String,
    override val parameters: List<IrParameter>,
    override val returnTypeName: IrTypeName?,
    override val impl: IrMixinBridgeEntryFunctionImpl,
) : IrMixinBridgeEntry(sourceName, impl), IrMixinBridgeEntryKind {
    override val kinds: List<IrMixinBridgeEntryKind> = listOf(this)
}

sealed interface IrMixinBridgeEntryImpl
sealed interface IrMixinBridgeEntryPropertyImpl : IrMixinBridgeEntryImpl
sealed interface IrMixinBridgeEntryFunctionImpl : IrMixinBridgeEntryImpl

sealed interface IrMixinBridgeEntryExtensionImpl
object IrMixinBridgeEntryExtensionPropertyImpl : IrMixinBridgeEntryPropertyImpl, IrMixinBridgeEntryExtensionImpl
object IrMixinBridgeEntryExtensionFunctionImpl : IrMixinBridgeEntryFunctionImpl, IrMixinBridgeEntryExtensionImpl
