package io.github.recrafter.lapis.layers.validator

import com.google.devtools.ksp.containingFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.recrafter.lapis.Side
import io.github.recrafter.lapis.extensions.common.defaultValue
import io.github.recrafter.lapis.extensions.ksp.*
import io.github.recrafter.lapis.layers.lowering.asIr
import io.github.recrafter.lapis.layers.lowering.types.IrClassType
import io.github.recrafter.lapis.layers.lowering.types.IrType
import org.spongepowered.asm.mixin.injection.At

class ValidatorResult(
    val schemas: List<Schema>,
    val patches: List<Patch>,
)

class Schema(
    symbol: KSPSymbol,

    classType: KSPClass,
    targetClassType: KSPClass?,
    val needAccess: Boolean,
    val needRemoveFinal: Boolean,
    val widener: String,
    val descriptors: List<Descriptor>,
) {
    val irClassType: IrClassType = classType.asIr()
    val irTargetClassType: IrClassType = targetClassType?.asIr() ?: IrClassType.ofBinaryClassName(widener)
    val containingFile: KSPFile? = symbol.containingFile?.warmUp()
}

sealed class Descriptor(
    val name: String,
    val targetName: String,
    classType: KSPClass,
    receiverType: KSPType,
    val parameters: List<FunctionParameter>,
    val returnType: KSPType?,
    val isStatic: Boolean,
    val needAccess: Boolean,
    val needRemoveFinal: Boolean,
) {
    val irClassType: IrClassType = classType.asIr()
    val irReceiverType: IrType = receiverType.asIr()
    open val irReturnType: IrType? = returnType?.asIr()
}

sealed class InvokableDescriptor(
    name: String,
    targetName: String,
    classType: KSPClass,
    receiverType: KSPType,
    parameters: List<FunctionParameter>,
    returnType: KSPType?,
    isStatic: Boolean,
    needAccess: Boolean,
    needRemoveFinal: Boolean,
) : Descriptor(name, targetName, classType, receiverType, parameters, returnType, isStatic, needAccess, needRemoveFinal)

class ConstructorDescriptor(
    name: String,
    classType: KSPClass,
    ownerClassType: KSPType,
    parameters: List<FunctionParameter>,
    needAccess: Boolean,
    needRemoveFinal: Boolean,
) : InvokableDescriptor(
    name,
    "",
    classType,
    ownerClassType,
    parameters,
    ownerClassType,
    true,
    needAccess,
    needRemoveFinal
) {
    val irOwnerReturnType: IrType = ownerClassType.asIr()
}

open class MethodDescriptor(
    name: String,
    classType: KSPClass,
    receiverType: KSPType,
    returnType: KSPType?,
    targetName: String,
    parameters: List<FunctionParameter>,
    isStatic: Boolean,
    needAccess: Boolean,
    needRemoveFinal: Boolean,
) : InvokableDescriptor(
    name,
    targetName,
    classType,
    receiverType,
    parameters,
    returnType,
    isStatic,
    needAccess,
    needRemoveFinal
)

class FieldDescriptor(
    name: String,
    classType: KSPClass,
    receiverType: KSPType,
    targetName: String,
    val type: KSPType,
    isStatic: Boolean,
    needAccess: Boolean,
    needRemoveFinal: Boolean,
) : Descriptor(name, targetName, classType, receiverType, emptyList(), type, isStatic, needAccess, needRemoveFinal) {
    val irType: IrType = type.asIr()
}

class Patch(
    symbol: KSPSymbol,

    val name: String,
    val side: Side,

    classType: KSPClass,
    targetClassType: KSPClass,

    val sharedProperties: List<SharedProperty>,
    val sharedFunctions: List<SharedFunction>,

    val hooks: List<HookModel>,
) {
    val irClassType: IrClassType = classType.asIr()
    val irTargetClassType: IrClassType = targetClassType.asIr()
    val containingFile: KSPFile? = symbol.containingFile?.warmUp()
}

class SharedProperty(
    val name: String,
    type: KSPType,
    val isMutable: Boolean,
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

sealed class HookModel(
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
) : HookModel(name, descriptor, returnType, parameters, listOf(At::ordinal.defaultValue))

class CallHook(
    name: String,
    descriptor: InvokableDescriptor,
    returnType: KSPType?,
    parameters: List<HookParameter>,
    val methodDescriptor: MethodDescriptor,
    ordinals: List<Int>,
) : HookModel(name, descriptor, returnType, parameters, ordinals)

class LiteralHook(
    name: String,
    descriptor: InvokableDescriptor,
    parameters: List<HookParameter>,
    type: KSPType,
    val typeName: String,
    val value: String,
    ordinals: List<Int>,
) : HookModel(name, descriptor, type, parameters, ordinals) {
    val irType: IrType = type.asIr()
}

class FieldGetHook(
    name: String,
    descriptor: InvokableDescriptor,
    type: KSPType,
    val fieldDescriptor: FieldDescriptor,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
) : HookModel(name, descriptor, type, parameters, ordinals) {
    val irType: IrType = type.asIr()
}

class FieldSetHook(
    name: String,
    descriptor: InvokableDescriptor,
    type: KSPType,
    val fieldDescriptor: FieldDescriptor,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
) : HookModel(name, descriptor, type, parameters, ordinals) {
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

class FunctionParameter(
    val name: String,
    type: KSPType,
) {
    val irType: IrType = type.asIr()
}

private fun KSPType.asIr(): IrType =
    toTypeName().asIr()

private fun KSPClass.asIr(): IrClassType =
    toClassName().asIr()
