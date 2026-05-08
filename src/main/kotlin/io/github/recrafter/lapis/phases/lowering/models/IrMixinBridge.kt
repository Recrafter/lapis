package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed class IrMixinBridge(
    override val originatingFiles: List<KSFile>,
    override val className: IrClassName,
    open val entries: List<IrMixinBridgeEntry>,
) : IrKotlinBlueprint()

class IrMixinExtensionBridge(
    originatingFiles: List<KSFile>,
    className: IrClassName,
    override val entries: List<IrMixinExtensionBridgeEntry>,
) : IrMixinBridge(originatingFiles, className, entries)

class IrMixinShadowBridge(
    originatingFiles: List<KSFile>,
    className: IrClassName,
    override val entries: List<IrMixinShadowBridgeEntry>,
) : IrMixinBridge(originatingFiles, className, entries)

sealed interface IrMixinBridgeEntry {
    val sourceName: String
    val impl: IrMixinBridgeEntryImpl
    val kinds: List<IrMixinBridgeEntryKind>
}

sealed interface IrMixinExtensionBridgeEntry : IrMixinBridgeEntry {
    override val sourceName: String
    override val impl: IrMixinBridgeEntryExtensionImpl
}

sealed interface IrMixinShadowBridgeEntry : IrMixinBridgeEntry {
    override val sourceName: String
    override val impl: IrMixinBridgeEntryShadowImpl
}

sealed interface IrMixinBridgeEntryKind {
    val name: String
    val sourceJvmName: String
    val parameters: List<IrParameter>
    val returnTypeName: IrTypeName?
}

class IrMixinBridgeEntryPropertyGetter(
    override val name: String,
    override val sourceJvmName: String,
    typeName: IrTypeName,
) : IrMixinBridgeEntryKind {
    override val parameters: List<IrParameter> = emptyList()
    override val returnTypeName: IrTypeName = typeName
}

class IrMixinBridgeEntryPropertySetter(
    override val name: String,
    override val sourceJvmName: String,
    val typeName: IrTypeName,
) : IrMixinBridgeEntryKind {
    override val parameters: List<IrParameter> = listOf(IrSetterParameter(typeName))
    override val returnTypeName: IrTypeName? = null
}

sealed class IrMixinBridgeEntryProperty(
    override val sourceName: String,
    val typeName: IrTypeName,
    getterName: String,
    getterSourceJvmName: String,
    setterName: String?,
    setterSourceJvmName: String?,
    override val impl: IrMixinBridgeEntryPropertyImpl,
) : IrMixinBridgeEntry {
    val getter: IrMixinBridgeEntryPropertyGetter = IrMixinBridgeEntryPropertyGetter(
        getterName, getterSourceJvmName, typeName
    )
    val setter: IrMixinBridgeEntryPropertySetter? = if (setterName != null && setterSourceJvmName != null) {
        IrMixinBridgeEntryPropertySetter(setterName, setterSourceJvmName, typeName)
    } else null

    override val kinds: List<IrMixinBridgeEntryKind> = listOfNotNull(getter, setter)
}

class IrMixinExtensionBridgeEntryProperty(
    sourceName: String,
    typeName: IrTypeName,
    getterName: String,
    getterSourceJvmName: String,
    setterName: String?,
    setterSourceJvmName: String?,
    override val impl: IrMixinBridgeEntryExtensionPropertyImpl,
) : IrMixinBridgeEntryProperty(
    sourceName,
    typeName,
    getterName,
    getterSourceJvmName,
    setterName,
    setterSourceJvmName,
    impl,
), IrMixinExtensionBridgeEntry

class IrMixinShadowBridgeEntryProperty(
    sourceName: String,
    typeName: IrTypeName,
    getterName: String,
    getterSourceJvmName: String,
    setterName: String?,
    setterSourceJvmName: String?,
    override val impl: IrMixinBridgeEntryShadowPropertyImpl,
) : IrMixinBridgeEntryProperty(
    sourceName,
    typeName,
    getterName,
    getterSourceJvmName,
    setterName,
    setterSourceJvmName,
    impl,
), IrMixinShadowBridgeEntry

sealed class IrMixinBridgeEntryFunction(
    override val sourceName: String,
    override val name: String,
    override val sourceJvmName: String,
    override val parameters: List<IrParameter>,
    override val returnTypeName: IrTypeName?,
    override val impl: IrMixinBridgeEntryFunctionImpl,
) : IrMixinBridgeEntry, IrMixinBridgeEntryKind {
    override val kinds: List<IrMixinBridgeEntryKind> = listOf(this)
}

class IrMixinExtensionBridgeEntryFunction(
    sourceName: String,
    name: String,
    sourceJvmName: String,
    parameters: List<IrParameter>,
    returnTypeName: IrTypeName?,
    override val impl: IrMixinBridgeEntryExtensionFunctionImpl,
) : IrMixinBridgeEntryFunction(sourceName, name, sourceJvmName, parameters, returnTypeName, impl),
    IrMixinExtensionBridgeEntry

class IrMixinShadowBridgeEntryFunction(
    sourceName: String,
    name: String,
    sourceJvmName: String,
    parameters: List<IrParameter>,
    returnTypeName: IrTypeName?,
    override val impl: IrMixinBridgeEntryShadowFunctionImpl,
) : IrMixinBridgeEntryFunction(sourceName, name, sourceJvmName, parameters, returnTypeName, impl),
    IrMixinShadowBridgeEntry

sealed interface IrMixinBridgeEntryImpl
sealed interface IrMixinBridgeEntryPropertyImpl : IrMixinBridgeEntryImpl
sealed interface IrMixinBridgeEntryFunctionImpl : IrMixinBridgeEntryImpl

sealed interface IrMixinBridgeEntryExtensionImpl : IrMixinBridgeEntryImpl
object IrMixinBridgeEntryExtensionPropertyImpl : IrMixinBridgeEntryPropertyImpl, IrMixinBridgeEntryExtensionImpl
object IrMixinBridgeEntryExtensionFunctionImpl : IrMixinBridgeEntryFunctionImpl, IrMixinBridgeEntryExtensionImpl

sealed interface IrMixinBridgeEntryShadowImpl : IrMixinBridgeEntryImpl
class IrMixinBridgeEntryShadowPropertyImpl(
    val mappingName: String,
    val isStatic: Boolean,
    val isFinal: Boolean,
) : IrMixinBridgeEntryPropertyImpl, IrMixinBridgeEntryShadowImpl

class IrMixinBridgeEntryShadowFunctionImpl(
    val mappingName: String,
    val isStatic: Boolean,
) : IrMixinBridgeEntryFunctionImpl, IrMixinBridgeEntryShadowImpl
