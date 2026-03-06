package io.github.recrafter.lapis.layers.validator

import io.github.recrafter.lapis.annotations.enums.LapisPatchSide
import io.github.recrafter.lapis.extensions.ksp.KspClassDeclaration
import io.github.recrafter.lapis.extensions.ksp.KspSymbol
import io.github.recrafter.lapis.extensions.ksp.KspType
import io.github.recrafter.lapis.layers.lowering.IrJvmType

class ValidatorResult(
    val descriptors: List<Descriptor>,
    val rootPatches: List<Patch>,
)

class FunctionParameter(
    val name: String,
    val type: KspType,
)

sealed class Descriptor(
    override val source: KspSymbol,

    val name: String,
    val containerClassDeclaration: KspClassDeclaration,
    val classDeclaration: KspClassDeclaration,
    val receiverType: KspType,
    val returnType: KspType?,
) : KspSourceHolder()

class ConstructorDescriptor(
    override val source: KspSymbol,

    containerClassDeclaration: KspClassDeclaration,
    classDeclaration: KspClassDeclaration,
    val classType: KspType,
    parameters: List<FunctionParameter>,
) : MethodDescriptor(
    source,
    containerClassDeclaration,
    classDeclaration,
    classType,
    classType,
    IrJvmType.CONSTRUCTOR_NAME,
    parameters,
    true
)

open class MethodDescriptor(
    override val source: KspSymbol,

    containerClassDeclaration: KspClassDeclaration,
    classDeclaration: KspClassDeclaration,
    receiverType: KspType,
    returnType: KspType?,
    name: String,
    val parameters: List<FunctionParameter>,
    val isStatic: Boolean,
) : Descriptor(source, name, containerClassDeclaration, classDeclaration, receiverType, returnType)

class FieldDescriptor(
    override val source: KspSymbol,

    containerClassDeclaration: KspClassDeclaration,
    classDeclaration: KspClassDeclaration,
    receiverType: KspType,
    name: String,
    val type: KspType,
) : Descriptor(source, name, containerClassDeclaration, classDeclaration, receiverType, type)

class Patch(
    override val source: KspSymbol,

    val name: String,
    val side: LapisPatchSide,

    val classDeclaration: KspClassDeclaration,
    val targetClassDeclaration: KspClassDeclaration,

    val accessProperties: List<AccessProperty>,
    val accessFunctions: List<AccessFunction>,
    val accessConstructors: List<AccessConstructor>,

    val sharedProperties: List<SharedProperty>,
    val sharedFunctions: List<SharedFunction>,

    val hooks: List<Hook>,

    val innerPatches: MutableList<Patch> = mutableListOf(),
) : KspSourceHolder()

class AccessProperty(
    override val source: KspSymbol,

    val name: String,
    val type: KspType,

    val vanillaName: String,
    val isStatic: Boolean,
    val isMutable: Boolean,
) : KspSourceHolder()

open class AccessFunction(
    override val source: KspSymbol,

    val name: String,
    val vanillaName: String,
    val isStatic: Boolean,
    val parameters: List<FunctionParameter>,
    val returnType: KspType?,
) : KspSourceHolder()

class AccessConstructor(
    override val source: KspSymbol,

    val name: String,
    val classType: KspType,
    val parameters: List<FunctionParameter>,
) : KspSourceHolder()

class SharedProperty(
    override val source: KspSymbol,

    val name: String,
    val type: KspType,
    val isMutable: Boolean,
) : KspSourceHolder()

class SharedFunction(
    override val source: KspSymbol,

    val name: String,
    val parameters: List<FunctionParameter>,
    val returnType: KspType?,
) : KspSourceHolder()

sealed class Hook(
    override val source: KspSymbol,

    val name: String,
    val method: MethodDescriptor,
    val returnType: KspType?,
    val parameters: List<HookParameter>,
) : KspSourceHolder()

class MethodBodyHook(
    override val source: KspSymbol,

    name: String,
    method: MethodDescriptor,
    returnType: KspType?,
    parameters: List<HookParameter>,
) : Hook(source, name, method, returnType, parameters)

class InvokeMethodHook(
    override val source: KspSymbol,

    name: String,
    method: MethodDescriptor,
    returnType: KspType?,
    parameters: List<HookParameter>,
    val target: MethodDescriptor,
    val ordinals: List<Int>,
) : Hook(source, name, method, returnType, parameters)

class LiteralHook(
    override val source: KspSymbol,

    name: String,
    method: MethodDescriptor,
    parameters: List<HookParameter>,
    val literalType: KspType,
    val literalTypeName: String,
    val literalValue: String,
    val ordinals: List<Int>,
) : Hook(source, name, method, literalType, parameters)

sealed interface HookParameter
class HookContextParameter(val descriptor: MethodDescriptor) : HookParameter
class HookTargetParameter(val descriptor: MethodDescriptor) : HookParameter
class HookLiteralParameter(val type: KspType, val typeName: String, val value: String) : HookParameter
class HookOrdinalParameter(val indices: List<Int>) : HookParameter
class HookLocalParameter(val name: String, val type: KspType, val ordinal: Int) : HookParameter
