package io.github.recrafter.lapis.layers.validator

import com.google.devtools.ksp.containingFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.recrafter.lapis.annotations.enums.LapisPatchSide
import io.github.recrafter.lapis.extensions.common.defaultValue
import io.github.recrafter.lapis.extensions.ksp.*
import io.github.recrafter.lapis.layers.lowering.asIr
import io.github.recrafter.lapis.layers.lowering.types.IrClassType
import io.github.recrafter.lapis.layers.lowering.types.IrType
import org.spongepowered.asm.mixin.injection.At

class ValidatorResult(
    val descriptors: List<Descriptor>,
    val patches: List<Patch>,
)

class FunctionParameter(
    val name: String,
    type: KSPType,
) {
    val irType: IrType = type.asIr()
}

sealed class Descriptor(
    symbol: KSPSymbol,

    val targetName: String,
    classType: KSPClass,
    receiverType: KSPType,
    val parameters: List<FunctionParameter>,
    val returnType: KSPType?,
    val isStatic: Boolean,
) {
    val irClassType: IrClassType = classType.asIr()
    val irReceiverType: IrType = receiverType.asIr()
    val irReturnType: IrType? = returnType?.asIr()
    val containingFile: KSPFile? = symbol.containingFile?.warmUp()
}

sealed class InvokableDescriptor(
    symbol: KSPSymbol,

    targetName: String,
    classType: KSPClass,
    receiverType: KSPType,
    parameters: List<FunctionParameter>,
    returnType: KSPType?,
    isStatic: Boolean,
) : Descriptor(symbol, targetName, classType, receiverType, parameters, returnType, isStatic)

class ConstructorDescriptor(
    symbol: KSPSymbol,

    classType: KSPClass,
    returnType: KSPType,
    parameters: List<FunctionParameter>,
) : InvokableDescriptor(symbol, "", classType, returnType, parameters, returnType, true)

open class MethodDescriptor(
    symbol: KSPSymbol,

    classType: KSPClass,
    receiverType: KSPType,
    returnType: KSPType?,
    targetName: String,
    parameters: List<FunctionParameter>,
    isStatic: Boolean,
) : InvokableDescriptor(symbol, targetName, classType, receiverType, parameters, returnType, isStatic)

class FieldDescriptor(
    symbol: KSPSymbol,

    classType: KSPClass,
    receiverType: KSPType,
    targetName: String,
    val type: KSPType,
    isStatic: Boolean,
) : Descriptor(symbol, targetName, classType, receiverType, emptyList(), type, isStatic) {
    val irType: IrType = type.asIr()
}

class Patch(
    symbol: KSPSymbol,

    val name: String,
    val side: LapisPatchSide,

    classType: KSPClass,
    targetClassType: KSPClass,

    val accessProperties: List<AccessProperty>,
    val accessFunctions: List<AccessFunction>,
    val accessConstructors: List<AccessConstructor>,

    val sharedProperties: List<SharedProperty>,
    val sharedFunctions: List<SharedFunction>,

    val hooks: List<Hook>,

    val innerPatches: MutableList<Patch> = mutableListOf(),
) {
    val irClassType: IrClassType = classType.asIr()
    val irTargetClassType: IrClassType = targetClassType.asIr()
    val containingFile: KSPFile? = symbol.containingFile?.warmUp()
}

class AccessProperty(
    val name: String,
    type: KSPType,

    val vanillaName: String,
    val isStatic: Boolean,
    val isMutable: Boolean,
) {
    val irType: IrType = type.asIr()
}

open class AccessFunction(
    val name: String,
    val vanillaName: String,
    val isStatic: Boolean,
    val parameters: List<FunctionParameter>,
    returnType: KSPType?,
) {
    val irReturnType: IrType? = returnType?.asIr()
}

class AccessConstructor(
    val name: String,
    classType: KSPType,
    val parameters: List<FunctionParameter>,
) {
    val irClassType: IrType = classType.asIr()
}

class SharedProperty(
    val name: String,
    type: KSPType,
    val isMutable: Boolean,
    val isSetterPublic: Boolean,
) {
    val irType: IrType = type.asIr()
}

class SharedFunction(
    val name: String,
    val parameters: List<FunctionParameter>,
    returnType: KSPType?,
) {
    val irReturnType: IrType? = returnType?.asIr()
}

sealed class Hook(
    val name: String,
    val descriptor: Descriptor,
    returnType: KSPType?,
    val parameters: List<HookParameter>,
    val ordinals: List<Int>,
) {
    val irReturnType: IrType? = returnType?.asIr()
}

class BodyHook(
    name: String,
    descriptor: MethodDescriptor,
    returnType: KSPType?,
    parameters: List<HookParameter>,
) : Hook(name, descriptor, returnType, parameters, listOf(At::ordinal.defaultValue))

class CallHook(
    name: String,
    descriptor: InvokableDescriptor,
    returnType: KSPType?,
    parameters: List<HookParameter>,
    val methodDescriptor: MethodDescriptor,
    ordinals: List<Int>,
) : Hook(name, descriptor, returnType, parameters, ordinals)

class LiteralHook(
    name: String,
    descriptor: InvokableDescriptor,
    parameters: List<HookParameter>,
    type: KSPType,
    val typeName: String,
    val value: String,
    ordinals: List<Int>,
) : Hook(name, descriptor, type, parameters, ordinals) {
    val irType: IrType = type.asIr()
}

class FieldGetHook(
    name: String,
    descriptor: InvokableDescriptor,
    type: KSPType,
    val fieldDescriptor: FieldDescriptor,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
) : Hook(name, descriptor, type, parameters, ordinals) {
    val irType: IrType = type.asIr()
}

sealed interface HookParameter

sealed class HookDescriptorParameter(open val descriptor: Descriptor) : HookParameter

sealed class HookTargetParameter(override val descriptor: Descriptor) : HookDescriptorParameter(descriptor)
class HookCallableTargetParameter(override val descriptor: InvokableDescriptor) : HookTargetParameter(descriptor)
class HookGetterTargetParameter(override val descriptor: FieldDescriptor) : HookTargetParameter(descriptor)
class HookSetterTargetParameter(override val descriptor: FieldDescriptor) : HookTargetParameter(descriptor)

class HookContextParameter(override val descriptor: InvokableDescriptor) : HookDescriptorParameter(descriptor)

class HookLiteralParameter(
    val type: KSPType,
    val typeName: String,
    val value: String
) : HookParameter {
    val irType: IrType by lazy { type.asIr() }
}

class HookOrdinalParameter(val indices: List<Int>) : HookParameter
class HookLocalParameter(
    val name: String,
    type: KSPType,
    val ordinal: Int
) : HookParameter {
    val irType: IrType = type.asIr()
}

private fun KSPType.asIr(): IrType =
    toTypeName().asIr()

private fun KSPClass.asIr(): IrClassType =
    toClassName().asIr()
