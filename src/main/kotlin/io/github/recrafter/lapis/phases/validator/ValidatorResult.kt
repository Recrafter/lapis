package io.github.recrafter.lapis.phases.validator

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.recrafter.lapis.annotations.*
import io.github.recrafter.lapis.extensions.ks.starProjectedType
import io.github.recrafter.lapis.phases.common.JvmClassName
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import io.github.recrafter.lapis.phases.parser.KSTypes

class ValidatorResult(
    val schemas: List<Schema>,
    val patches: List<Patch>,
)

open class SourceFile(
    symbol: KSNode,
    classDeclaration: KSClassDeclaration,
) {
    val className: IrClassName = classDeclaration.asIrClassName()
    val containingFile: KSFile? = symbol.containingFile
}

class Schema(
    symbol: KSNode,
    classDeclaration: KSClassDeclaration,

    val originJvmClassName: JvmClassName,
    val originClassDeclaration: KSClassDeclaration,
    val side: Side,

    val isAccessible: Boolean,

    val accessRequest: AccessRequest?,
    val descriptors: List<Descriptor>,
) : SourceFile(symbol, classDeclaration) {
    val originTypeName: IrTypeName = originClassDeclaration.starProjectedType.asIrTypeName()
}

sealed class AccessRequest(val shouldRemoveFinal: Boolean)
class TweakAccessRequest(shouldRemoveFinal: Boolean) : AccessRequest(shouldRemoveFinal)
class MixinAccessRequest(
    shouldRemoveFinal: Boolean,
    val fieldOps: List<Op>,
) : AccessRequest(shouldRemoveFinal)

sealed class Descriptor(
    symbol: KSNode,
    classDeclaration: KSClassDeclaration,

    val name: String,
    val mappingName: String,
    receiverType: KSType,
    val inaccessibleReceiverJvmClassName: JvmClassName?,
    val parameters: List<FunctionTypeParameter>,
    val returnType: KSType?,
    val isStatic: Boolean,
    val accessRequest: AccessRequest?,
) : SourceFile(symbol, classDeclaration) {
    val receiverTypeName: IrTypeName = receiverType.asIrTypeName()
    val returnTypeName: IrTypeName? = returnType?.asIrTypeName()
}

sealed class InvokableDescriptor(
    symbol: KSNode,

    name: String,
    mappingName: String,
    classDeclaration: KSClassDeclaration,
    receiverType: KSType,
    inaccessibleReceiverJvmClassName: JvmClassName?,
    parameters: List<FunctionTypeParameter>,
    returnType: KSType?,
    isStatic: Boolean,
    accessRequest: AccessRequest?,
) : Descriptor(
    symbol,
    classDeclaration,
    name,
    mappingName,
    receiverType,
    inaccessibleReceiverJvmClassName,
    parameters,
    returnType,
    isStatic,
    accessRequest,
)

class ConstructorDescriptor(
    symbol: KSNode,

    name: String,
    classDeclaration: KSClassDeclaration,
    returnType: KSType,
    parameters: List<FunctionTypeParameter>,
    accessRequest: AccessRequest?,
) : InvokableDescriptor(
    symbol, name, "", classDeclaration, returnType, null, parameters, returnType, false, accessRequest,
)

open class MethodDescriptor(
    symbol: KSNode,

    name: String,
    mappingName: String,
    classDeclaration: KSClassDeclaration,
    receiverType: KSType,
    inaccessibleReceiverJvmClassName: JvmClassName?,
    returnType: KSType?,
    parameters: List<FunctionTypeParameter>,
    isStatic: Boolean,
    accessRequest: AccessRequest?,
) : InvokableDescriptor(
    symbol,
    name,
    mappingName,
    classDeclaration,
    receiverType,
    inaccessibleReceiverJvmClassName,
    parameters,
    returnType,
    isStatic,
    accessRequest,
)

class FieldDescriptor(
    symbol: KSNode,

    name: String,
    mappingName: String,
    classDeclaration: KSClassDeclaration,
    receiverType: KSType,
    inaccessibleReceiverJvmClassName: JvmClassName?,
    val fieldType: KSType,
    val arrayComponentType: KSType?,
    isStatic: Boolean,
    accessRequest: AccessRequest?,
) : Descriptor(
    symbol,
    classDeclaration,
    name,
    mappingName,
    receiverType,
    inaccessibleReceiverJvmClassName,
    emptyList(),
    fieldType,
    isStatic,
    accessRequest,
) {
    val fieldTypeName: IrTypeName = fieldType.asIrTypeName()
}

class Patch(
    symbol: KSNode,
    classDeclaration: KSClassDeclaration,
    val name: String,
    val side: Side,
    val initStrategy: InitStrategy,
    val isObject: Boolean,
    val isImplRequired: Boolean,
    val schema: Schema,
    val constructorParameters: List<PatchConstructorParameter>,
    val externalBridgeSources: List<PatchExternalBridgeSource>,
    val internalBridgeSources: List<PatchInternalBridgeSource>,
    val hooks: List<PatchHook>,
) : SourceFile(symbol, classDeclaration)

sealed interface PatchConstructorParameter
object PatchConstructorOriginParameter : PatchConstructorParameter

sealed interface PatchBridgeSource
sealed class PatchBridgeSourceProperty(
    val name: String,
    val getterJvmName: String,
    val setterJvmName: String?,
    type: KSType,
    val isMutable: Boolean,
) : PatchBridgeSource {
    val typeName: IrTypeName = type.asIrTypeName()
}

sealed class PatchBridgeSourceFunction(
    val name: String,
    val jvmName: String,
    val parameters: List<FunctionParameter>,
    returnType: KSType?,
) : PatchBridgeSource {
    val returnTypeName: IrTypeName? = returnType?.asIrTypeName()
}

sealed interface PatchExternalBridgeSource
class PatchExternalBridgeProperty(
    name: String,
    getterJvmName: String,
    setterJvmName: String?,
    type: KSType,
    isMutable: Boolean,
) : PatchBridgeSourceProperty(name, getterJvmName, setterJvmName, type, isMutable), PatchExternalBridgeSource

class PatchExternalBridgeFunction(
    name: String,
    jvmName: String,
    parameters: List<FunctionParameter>,
    returnType: KSType?,
) : PatchBridgeSourceFunction(name, jvmName, parameters, returnType), PatchExternalBridgeSource

sealed interface PatchInternalBridgeSource
class PatchInternalBridgeProperty(
    name: String,
    getterJvmName: String,
    setterJvmName: String?,
    type: KSType,
    isMutable: Boolean,
    val mappingName: String,
    val isStatic: Boolean,
    val isFinal: Boolean,
) : PatchBridgeSourceProperty(name, getterJvmName, setterJvmName, type, isMutable), PatchInternalBridgeSource

class PatchInternalBridgeFunction(
    name: String,
    jvmName: String,
    parameters: List<FunctionParameter>,
    returnType: KSType?,
    val mappingName: String,
    val isStatic: Boolean,
) : PatchBridgeSourceFunction(name, jvmName, parameters, returnType), PatchInternalBridgeSource

sealed class PatchHook(
    val jvmName: String,
    val descriptor: Descriptor,
    returnType: KSType?,
    val parameters: List<HookParameter>,
    val ordinals: List<Int>,
) {
    open val returnTypeName: IrTypeName? = returnType?.asIrTypeName()
    open val isInjectBased: Boolean = false
}

sealed interface HookWithTarget {
    val targetDescriptor: Descriptor
}

class MethodHeadHook(
    jvmName: String,
    descriptor: MethodDescriptor,
    parameters: List<HookParameter>,
) : PatchHook(jvmName, descriptor, null, parameters, emptyList()) {
    override val isInjectBased: Boolean = true
}

class ConstructorHeadHook(
    jvmName: String,
    descriptor: ConstructorDescriptor,
    parameters: List<HookParameter>,
    val phase: ConstructorHeadPhase,
) : PatchHook(jvmName, descriptor, null, parameters, emptyList()) {
    override val isInjectBased: Boolean = true
}

class BodyHook(
    jvmName: String,
    override val targetDescriptor: MethodDescriptor,
    returnType: KSType?,
    parameters: List<HookParameter>,
) : PatchHook(jvmName, targetDescriptor, returnType, parameters, emptyList()), HookWithTarget

class TailHook(
    jvmName: String,
    descriptor: InvokableDescriptor,
    parameters: List<HookParameter>,
) : PatchHook(jvmName, descriptor, null, parameters, emptyList()) {
    override val isInjectBased: Boolean = true
}

class LocalHook(
    jvmName: String,
    descriptor: InvokableDescriptor,
    type: KSType,
    parameters: List<HookParameter>,
    ordinals: List<Int>,
    val local: DomainLocal,
    val op: Op,
) : PatchHook(jvmName, descriptor, type, parameters, ordinals) {
    val typeName: IrTypeName = type.asIrTypeName()
}

class InstanceofHook(
    jvmName: String,
    descriptor: InvokableDescriptor,
    typeClassDeclaration: KSClassDeclaration,
    returnType: KSType,
    parameters: List<HookParameter>,
    ordinals: List<Int>,
) : PatchHook(jvmName, descriptor, returnType, parameters, ordinals) {
    val typeClassName: IrClassName = typeClassDeclaration.asIrClassName()
}

class ReturnHook(
    jvmName: String,
    descriptor: InvokableDescriptor,
    type: KSType?,
    parameters: List<HookParameter>,
    ordinals: List<Int>,
) : PatchHook(jvmName, descriptor, type, parameters, ordinals) {
    override val isInjectBased: Boolean = type == null
}

class LiteralHook(
    jvmName: String,
    descriptor: InvokableDescriptor,
    parameters: List<HookParameter>,
    type: KSType,
    val literal: Literal,
    ordinals: List<Int>,
) : PatchHook(jvmName, descriptor, type, parameters, ordinals) {
    val typeName: IrTypeName = type.asIrTypeName()
}

sealed interface Literal {
    fun getType(types: KSTypes): KSType
}

class ZeroLiteral(val conditions: List<ZeroCondition>) : IntLiteral(0)
open class IntLiteral(val value: Int) : Literal {
    override fun getType(types: KSTypes): KSType = types.int
}

class FloatLiteral(val value: Float) : Literal {
    override fun getType(types: KSTypes): KSType = types.float
}

class LongLiteral(val value: Long) : Literal {
    override fun getType(types: KSTypes): KSType = types.long
}

class DoubleLiteral(val value: Double) : Literal {
    override fun getType(types: KSTypes): KSType = types.double
}

class StringLiteral(val value: String) : Literal {
    override fun getType(types: KSTypes): KSType = types.string
}

class ClassLiteral(typeClassDeclaration: KSClassDeclaration) : Literal {
    override fun getType(types: KSTypes): KSType = types.any
    val typeClassName: IrClassName = typeClassDeclaration.asIrClassName()
}

object NullLiteral : Literal {
    override fun getType(types: KSTypes): KSType = types.nothing
}

class FieldGetHook(
    jvmName: String,
    descriptor: InvokableDescriptor,
    type: KSType,
    override val targetDescriptor: FieldDescriptor,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
) : PatchHook(jvmName, descriptor, type, parameters, ordinals), HookWithTarget {
    val typeName: IrTypeName = type.asIrTypeName()
}

class FieldSetHook(
    jvmName: String,
    descriptor: InvokableDescriptor,
    type: KSType,
    override val targetDescriptor: FieldDescriptor,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
) : PatchHook(jvmName, descriptor, type, parameters, ordinals), HookWithTarget {
    val typeName: IrTypeName = type.asIrTypeName()
}

class ArrayHook(
    jvmName: String,
    descriptor: InvokableDescriptor,
    type: KSType,
    val componentType: KSType,
    val targetDescriptor: FieldDescriptor,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
    val op: Op,
) : PatchHook(jvmName, descriptor, type, parameters, ordinals) {
    val typeName: IrTypeName = type.asIrTypeName()
    val componentTypeName: IrTypeName = componentType.asIrTypeName()
}

class CallHook(
    jvmName: String,
    descriptor: InvokableDescriptor,
    returnType: KSType?,
    parameters: List<HookParameter>,
    override val targetDescriptor: InvokableDescriptor,
    ordinals: List<Int>,
) : PatchHook(jvmName, descriptor, returnType, parameters, ordinals), HookWithTarget

sealed interface DomainLocal
class NamedLocal(val name: String) : DomainLocal
class PositionalLocal(val ordinal: Int) : DomainLocal

sealed interface HookParameter

sealed interface HookOriginParameter : HookParameter
object HookOriginValueParameter : HookOriginParameter

sealed class HookOriginDescriptorWrapperParameter(open val descriptor: Descriptor) : HookOriginParameter

class HookOriginBodyDescriptorWrapperParameter(
    override val descriptor: InvokableDescriptor
) : HookOriginDescriptorWrapperParameter(descriptor)

class HookOriginFieldGetDescriptorWrapperParameter(
    override val descriptor: FieldDescriptor
) : HookOriginDescriptorWrapperParameter(descriptor)

class HookOriginFieldSetDescriptorWrapperParameter(
    override val descriptor: FieldDescriptor
) : HookOriginDescriptorWrapperParameter(descriptor)

class HookOriginArrayGetDescriptorWrapperParameter(
    override val descriptor: FieldDescriptor,
    arrayComponentType: KSType,
) : HookOriginDescriptorWrapperParameter(descriptor) {
    val arrayComponentTypeName: IrTypeName = arrayComponentType.asIrTypeName()
}

object HookOriginInstanceofWrapperParameter : HookOriginParameter

class HookOriginArraySetDescriptorWrapperParameter(
    override val descriptor: FieldDescriptor,
    arrayComponentType: KSType,
) : HookOriginDescriptorWrapperParameter(descriptor) {
    val arrayComponentTypeName: IrTypeName = arrayComponentType.asIrTypeName()
}

class HookOriginCallDescriptorWrapperParameter(override val descriptor: InvokableDescriptor) :
    HookOriginDescriptorWrapperParameter(descriptor)

class HookCancelDescriptorWrapperParameter(
    override val descriptor: InvokableDescriptor
) : HookOriginDescriptorWrapperParameter(descriptor)

object HookOrdinalParameter : HookParameter

sealed class HookLocalParameter(
    val name: String,
    type: KSType,
    val isVar: Boolean,
) : HookParameter {
    val typeName: IrTypeName = type.asIrTypeName()
}

class HookParamLocalParameter(
    name: String,
    type: KSType,
    val index: Int,
    isVar: Boolean,
) : HookLocalParameter(name, type, isVar)

class HookBodyLocalParameter(
    name: String,
    type: KSType,
    val local: DomainLocal,
    isVar: Boolean,
) : HookLocalParameter(name, type, isVar)

class HookShareLocalParameter(
    name: String,
    type: KSType,
    val key: String,
    val isExported: Boolean,
) : HookLocalParameter(name, type, true)

class FunctionParameter(val name: String, type: KSType) {
    val typeName: IrTypeName = type.asIrTypeName()
}

class FunctionTypeParameter(val name: String?, val type: KSType) {
    val typeName: IrTypeName = type.asIrTypeName()
}

private fun KSType.asIrTypeName(): IrTypeName =
    toTypeName().asIrTypeName()

private fun KSClassDeclaration.asIrClassName(): IrClassName =
    toClassName().asIrClassName()
