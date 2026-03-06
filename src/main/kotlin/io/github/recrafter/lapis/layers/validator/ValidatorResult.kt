package io.github.recrafter.lapis.layers.validator

import io.github.recrafter.lapis.annotations.enums.LapisPatchSide
import io.github.recrafter.lapis.extensions.ksp.KspAnnotated
import io.github.recrafter.lapis.extensions.ksp.KspClassDeclaration
import io.github.recrafter.lapis.extensions.ksp.KspSymbol
import io.github.recrafter.lapis.extensions.ksp.KspType
import io.github.recrafter.lapis.layers.lowering.asIr
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName

class ValidatorResult(
    val descriptors: List<Descriptor>,
    val patches: List<Patch>,
    val unresolvedSymbols: List<KspAnnotated>,
)

class FunctionParameter(
    val name: String,
    type: KspType,
) {
    val type: IrTypeName = type.asIr()
}

sealed class Descriptor(
    override val source: KspSymbol,

    val targetName: String,
    val classDeclaration: KspClassDeclaration,
    receiverType: KspType,
    val kspReturnType: KspType?,
) : KspSourceHolder() {
    val receiverType: IrTypeName = receiverType.asIr()
    val returnType: IrTypeName? = kspReturnType?.asIr()
}

class ConstructorDescriptor(
    override val source: KspSymbol,

    classDeclaration: KspClassDeclaration,
    val classType: KspType,
    parameters: List<FunctionParameter>,
) : MethodDescriptor(
    source,
    classDeclaration,
    classType,
    classType,
    "",
    parameters,
    true
)

open class MethodDescriptor(
    override val source: KspSymbol,

    classDeclaration: KspClassDeclaration,
    receiverType: KspType,
    returnType: KspType?,
    targetName: String,
    val parameters: List<FunctionParameter>,
    val isStatic: Boolean,
) : Descriptor(source, targetName, classDeclaration, receiverType, returnType)

class FieldDescriptor(
    override val source: KspSymbol,

    classDeclaration: KspClassDeclaration,
    receiverType: KspType,
    targetName: String,
    val type: KspType,
) : Descriptor(source, targetName, classDeclaration, receiverType, type)

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
    val isSetterPublic: Boolean,
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
    val descriptor: MethodDescriptor,
    val returnType: KspType?,
    val parameters: List<HookParameter>,
) : KspSourceHolder()

class MethodBodyHook(
    override val source: KspSymbol,

    name: String,
    descriptor: MethodDescriptor,
    returnType: KspType?,
    parameters: List<HookParameter>,
) : Hook(source, name, descriptor, returnType, parameters)

class InvokeMethodHook(
    override val source: KspSymbol,

    name: String,
    descriptor: MethodDescriptor,
    returnType: KspType?,
    parameters: List<HookParameter>,
    val targetDescriptor: MethodDescriptor,
    val ordinals: List<Int>,
) : Hook(source, name, descriptor, returnType, parameters)

class LiteralHook(
    override val source: KspSymbol,

    name: String,
    descriptor: MethodDescriptor,
    parameters: List<HookParameter>,
    val literalType: KspType,
    val literalTypeName: String,
    val literalValue: String,
    val ordinals: List<Int>,
) : Hook(source, name, descriptor, literalType, parameters)

sealed interface HookParameter
class HookContextParameter(val descriptor: MethodDescriptor) : HookParameter
class HookTargetParameter(val descriptor: MethodDescriptor) : HookParameter
class HookLiteralParameter(val type: KspType, val typeName: String, val value: String) : HookParameter
class HookOrdinalParameter(val indices: List<Int>) : HookParameter
class HookLocalParameter(val name: String, val type: KspType, val ordinal: Int) : HookParameter
