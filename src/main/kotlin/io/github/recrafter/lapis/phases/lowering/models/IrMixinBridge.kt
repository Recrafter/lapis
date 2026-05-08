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
    val kinds: List<IrMixinBridgeEntryKind>
}

sealed interface IrMixinBridgeEntryKind : IrReturnable {
    val name: String
    val sourceJvmName: String
    val parameters: List<IrParameter>
    override val returnTypeName: IrTypeName?
}

sealed class IrMixinBridgeEntryProperty(
    override val sourceName: String,
    val typeName: IrTypeName,
    getterName: String,
    getterSourceJvmName: String,
    setterName: String?,
    setterSourceJvmName: String?,
) : IrMixinBridgeEntry {
    val getter: IrMixinBridgeEntryPropertyGetter = IrMixinBridgeEntryPropertyGetter(
        getterName, getterSourceJvmName, typeName
    )
    val setter: IrMixinBridgeEntryPropertySetter? = if (setterName != null && setterSourceJvmName != null) {
        IrMixinBridgeEntryPropertySetter(setterName, setterSourceJvmName, typeName)
    } else null

    override val kinds: List<IrMixinBridgeEntryKind> = listOfNotNull(getter, setter)

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
        val parameter: IrParameter = IrSetterParameter(typeName)
        override val parameters: List<IrParameter> = listOf(parameter)
        override val returnTypeName: IrTypeName? = null
    }
}

sealed class IrMixinBridgeEntryFunction(
    override val sourceName: String,
    override val name: String,
    override val sourceJvmName: String,
    override val parameters: List<IrParameter>,
    override val returnTypeName: IrTypeName?,
) : IrMixinBridgeEntry, IrMixinBridgeEntryKind {
    override val kinds: List<IrMixinBridgeEntryKind> = listOf(this)
}

sealed interface IrMixinExtensionBridgeEntry : IrMixinBridgeEntry

class IrMixinExtensionBridgeEntryProperty(
    sourceName: String,
    typeName: IrTypeName,
    getterName: String,
    getterSourceJvmName: String,
    setterName: String?,
    setterSourceJvmName: String?,
) : IrMixinBridgeEntryProperty(
    sourceName,
    typeName,
    getterName,
    getterSourceJvmName,
    setterName,
    setterSourceJvmName,
), IrMixinExtensionBridgeEntry

class IrMixinExtensionBridgeEntryFunction(
    sourceName: String,
    name: String,
    sourceJvmName: String,
    parameters: List<IrParameter>,
    returnTypeName: IrTypeName?,
) : IrMixinBridgeEntryFunction(sourceName, name, sourceJvmName, parameters, returnTypeName),
    IrMixinExtensionBridgeEntry

sealed interface IrMixinShadowBridgeEntry : IrMixinBridgeEntry

class IrMixinShadowBridgeEntryProperty(
    sourceName: String,
    typeName: IrTypeName,
    getterName: String,
    getterSourceJvmName: String,
    setterName: String?,
    setterSourceJvmName: String?,
    val mappingName: String,
    val isStatic: Boolean,
    val isFinal: Boolean,
) : IrMixinBridgeEntryProperty(
    sourceName,
    typeName,
    getterName,
    getterSourceJvmName,
    setterName,
    setterSourceJvmName,
), IrMixinShadowBridgeEntry

class IrMixinShadowBridgeEntryFunction(
    sourceName: String,
    name: String,
    sourceJvmName: String,
    parameters: List<IrParameter>,
    returnTypeName: IrTypeName?,
    val mappingName: String,
    val isStatic: Boolean,
) : IrMixinBridgeEntryFunction(sourceName, name, sourceJvmName, parameters, returnTypeName),
    IrMixinShadowBridgeEntry
