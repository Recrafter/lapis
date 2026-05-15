package io.github.recrafter.lapis.phases.validator.models.patches

import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.extensions.jp.JPModifier
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import io.github.recrafter.lapis.phases.validator.models.common.FunctionParameter

sealed interface BridgeSource
sealed class BridgeSourceProperty(
    val name: String,
    val getterJvmName: String,
    val setterJvmName: String?,
    type: KSType,
) : BridgeSource {
    val typeName: IrTypeName = type.asIrTypeName()
}

sealed class BridgeSourceFunction(
    val name: String,
    val jvmName: String,
    val parameters: List<FunctionParameter>,
    returnType: KSType?,
) : BridgeSource {
    val returnTypeName: IrTypeName? = returnType?.asIrTypeName()
}

sealed interface PatchExtensionSource
class ExtensionProperty(
    name: String,
    getterJvmName: String,
    setterJvmName: String?,
    type: KSType,
) : BridgeSourceProperty(name, getterJvmName, setterJvmName, type), PatchExtensionSource

class ExtensionFunction(
    name: String,
    jvmName: String,
    parameters: List<FunctionParameter>,
    returnType: KSType?,
) : BridgeSourceFunction(name, jvmName, parameters, returnType), PatchExtensionSource

sealed interface PatchShadowSource {
    val modifiers: List<JPModifier>
}

class ShadowProperty(
    name: String,
    getterJvmName: String,
    setterJvmName: String?,
    type: KSType,
    val mappingName: String,
    override val modifiers: List<JPModifier>,
) : BridgeSourceProperty(name, getterJvmName, setterJvmName, type), PatchShadowSource

class ShadowFunction(
    name: String,
    jvmName: String,
    parameters: List<FunctionParameter>,
    returnType: KSType?,
    val mappingName: String,
    override val modifiers: List<JPModifier>,
) : BridgeSourceFunction(name, jvmName, parameters, returnType), PatchShadowSource
