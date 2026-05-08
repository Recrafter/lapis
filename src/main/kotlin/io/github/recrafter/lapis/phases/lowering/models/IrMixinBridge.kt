package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed class IrMixinBridge(
    override val originatingFiles: List<KSFile>,
    override val className: IrClassName,
    open val entries: List<IrMixinBridgeEntry>,
) : IrKotlinBlueprint()

class IrMixinExternalBridge(
    originatingFiles: List<KSFile>,
    className: IrClassName,
    override val entries: List<IrMixinExternalBridgeEntry>,
) : IrMixinBridge(originatingFiles, className, entries)

class IrMixinInternalBridge(
    originatingFiles: List<KSFile>,
    className: IrClassName,
    override val entries: List<IrMixinInternalBridgeEntry>,
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

sealed class IrMixinBridgePropertyEntry(
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

sealed class IrMixinBridgeFunctionEntry(
    override val sourceName: String,
    override val name: String,
    override val sourceJvmName: String,
    override val parameters: List<IrParameter>,
    override val returnTypeName: IrTypeName?,
) : IrMixinBridgeEntry, IrMixinBridgeEntryKind {
    override val kinds: List<IrMixinBridgeEntryKind> = listOf(this)
}

sealed interface IrMixinExternalBridgeEntry : IrMixinBridgeEntry

class IrMixinExternalBridgePropertyEntry(
    sourceName: String,
    typeName: IrTypeName,
    getterName: String,
    getterSourceJvmName: String,
    setterName: String?,
    setterSourceJvmName: String?,
) : IrMixinBridgePropertyEntry(
    sourceName,
    typeName,
    getterName,
    getterSourceJvmName,
    setterName,
    setterSourceJvmName,
), IrMixinExternalBridgeEntry

class IrMixinExternalBridgeFunctionEntry(
    sourceName: String,
    name: String,
    sourceJvmName: String,
    parameters: List<IrParameter>,
    returnTypeName: IrTypeName?,
) : IrMixinBridgeFunctionEntry(sourceName, name, sourceJvmName, parameters, returnTypeName),
    IrMixinExternalBridgeEntry

sealed interface IrMixinInternalBridgeEntry : IrMixinBridgeEntry

class IrMixinInternalBridgePropertyEntry(
    sourceName: String,
    typeName: IrTypeName,
    getterName: String,
    getterSourceJvmName: String,
    setterName: String?,
    setterSourceJvmName: String?,
    val mappingName: String,
    val isStatic: Boolean,
    val isFinal: Boolean,
) : IrMixinBridgePropertyEntry(
    sourceName,
    typeName,
    getterName,
    getterSourceJvmName,
    setterName,
    setterSourceJvmName,
), IrMixinInternalBridgeEntry

class IrMixinInternalBridgeFunctionEntry(
    sourceName: String,
    name: String,
    sourceJvmName: String,
    parameters: List<IrParameter>,
    returnTypeName: IrTypeName?,
    val mappingName: String,
    val isStatic: Boolean,
) : IrMixinBridgeFunctionEntry(sourceName, name, sourceJvmName, parameters, returnTypeName),
    IrMixinInternalBridgeEntry
