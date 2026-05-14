package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.extensions.jp.JPModifier
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import ksp.org.jetbrains.kotlin.builtins.functions.BuiltInFunctionArity

sealed class IrMixinBridge(
    override val originatingFiles: List<KSFile>,
    override val className: IrClassName,
    open val entries: List<IrMixinBridgeEntry>,
) : IrKotlinClassBlueprint(IrKotlinClassKind.INTERFACE)

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

    val hasBigArity: Boolean
        get() = parameters.size >= BuiltInFunctionArity.BIG_ARITY
}

sealed class IrMixinBridgePropertyEntry(
    override val sourceName: String,
    val typeName: IrTypeName,
    getterName: String,
    sourceGetterJvmName: String,
    setterName: String?,
    sourceSetterJvmName: String?,
) : IrMixinBridgeEntry {
    val getter: IrMixinBridgeEntryPropertyGetter = IrMixinBridgeEntryPropertyGetter(
        getterName, sourceGetterJvmName, typeName
    )
    val setter: IrMixinBridgeEntryPropertySetter? = if (setterName != null && sourceSetterJvmName != null) {
        IrMixinBridgeEntryPropertySetter(setterName, sourceSetterJvmName, typeName)
    } else null

    override val kinds: List<IrMixinBridgeEntryKind> = listOfNotNull(getter, setter)

    class IrMixinBridgeEntryPropertyGetter(
        override val name: String,
        override val sourceJvmName: String,
        val typeName: IrTypeName,
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
    sourceGetterJvmName: String,
    sourceSetterJvmName: String?,
    getterName: String,
    setterName: String?,
) : IrMixinBridgePropertyEntry(
    sourceName,
    typeName,
    getterName,
    sourceGetterJvmName,
    setterName,
    sourceSetterJvmName,
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

sealed interface IrMixinInternalBridgeShadowEntry : IrMixinInternalBridgeEntry {
    val modifiers: List<JPModifier>
    val isStatic: Boolean get() = JPModifier.STATIC in modifiers
}

class IrMixinInternalBridgeShadowPropertyEntry(
    sourceName: String,
    typeName: IrTypeName,
    getterName: String,
    sourceGetterJvmName: String,
    setterName: String?,
    sourceSetterJvmName: String?,
    val mappingName: String,
    override val modifiers: List<JPModifier>,
    val isFinal: Boolean,
) : IrMixinBridgePropertyEntry(
    sourceName,
    typeName,
    getterName,
    sourceGetterJvmName,
    setterName,
    sourceSetterJvmName,
), IrMixinInternalBridgeShadowEntry

class IrMixinInternalBridgeShadowFunctionEntry(
    sourceName: String,
    name: String,
    sourceJvmName: String,
    parameters: List<IrParameter>,
    returnTypeName: IrTypeName?,
    val mappingName: String,
    override val modifiers: List<JPModifier>,
) : IrMixinBridgeFunctionEntry(sourceName, name, sourceJvmName, parameters, returnTypeName),
    IrMixinInternalBridgeShadowEntry
