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

class Schema(
    source: KSNode,

    classDeclaration: KSClassDeclaration,
    val originJvmClassName: JvmClassName,
    val originClassDeclaration: KSClassDeclaration,

    val isAccessible: Boolean,

    val makePublic: Boolean,
    val removeFinal: Boolean,
    val descriptors: List<Descriptor>,
) {
    val className: IrClassName = classDeclaration.asIrClassName()
    val originTypeName: IrTypeName = originClassDeclaration.starProjectedType.asIrTypeName()
    val containingFile: KSFile? = source.containingFile
}

sealed class Descriptor(
    val name: String,
    val mappingName: String,
    classDeclaration: KSClassDeclaration,
    receiverType: KSType,
    val inaccessibleReceiverJvmClassName: JvmClassName?,
    val parameters: List<FunctionTypeParameter>,
    val returnType: KSType?,
    val isStatic: Boolean,
    val makePublic: Boolean,
    val removeFinal: Boolean,
) {
    val className: IrClassName = classDeclaration.asIrClassName()
    val receiverTypeName: IrTypeName = receiverType.asIrTypeName()
    val returnTypeName: IrTypeName? = returnType?.asIrTypeName()
}

sealed class InvokableDescriptor(
    name: String,
    mappingName: String,
    classDeclaration: KSClassDeclaration,
    receiverType: KSType,
    inaccessibleReceiverJvmClassName: JvmClassName?,
    parameters: List<FunctionTypeParameter>,
    returnType: KSType?,
    isStatic: Boolean,
    makePublic: Boolean,
    removeFinal: Boolean,
) : Descriptor(
    name,
    mappingName,
    classDeclaration,
    receiverType,
    inaccessibleReceiverJvmClassName,
    parameters,
    returnType,
    isStatic,
    makePublic,
    removeFinal,
)

class ConstructorDescriptor(
    name: String,
    classDeclaration: KSClassDeclaration,
    returnType: KSType,
    parameters: List<FunctionTypeParameter>,
    makePublic: Boolean,
) : InvokableDescriptor(
    name, "", classDeclaration, returnType, null, parameters, returnType, false, makePublic, false,
)

open class MethodDescriptor(
    name: String,
    mappingName: String,
    classDeclaration: KSClassDeclaration,
    receiverType: KSType,
    inaccessibleReceiverJvmClassName: JvmClassName?,
    returnType: KSType?,
    parameters: List<FunctionTypeParameter>,
    isStatic: Boolean,
    makePublic: Boolean,
    removeFinal: Boolean,
) : InvokableDescriptor(
    name,
    mappingName,
    classDeclaration,
    receiverType,
    inaccessibleReceiverJvmClassName,
    parameters,
    returnType,
    isStatic,
    makePublic,
    removeFinal,
)

class FieldDescriptor(
    name: String,
    mappingName: String,
    classDeclaration: KSClassDeclaration,
    receiverType: KSType,
    inaccessibleReceiverJvmClassName: JvmClassName?,
    val fieldType: KSType,
    val arrayComponentType: KSType?,
    isStatic: Boolean,
    makePublic: Boolean,
    removeFinal: Boolean,
) : Descriptor(
    name,
    mappingName,
    classDeclaration,
    receiverType,
    inaccessibleReceiverJvmClassName,
    emptyList(),
    fieldType,
    isStatic,
    makePublic,
    removeFinal,
) {
    val fieldTypeName: IrTypeName = fieldType.asIrTypeName()
}

class Patch(
    source: KSNode,

    val name: String,
    val side: Side,
    val initStrategy: InitStrategy,
    val isObject: Boolean,
    val hasStaticHooksOnly: Boolean,

    classDeclaration: KSClassDeclaration,

    val schema: Schema,

    val constructorParameters: List<PatchConstructorParameter>,
    val sharedProperties: List<PatchSharedProperty>,
    val sharedFunctions: List<PatchSharedFunction>,

    val hooks: List<PatchHook>,
) {
    val className: IrClassName = classDeclaration.asIrClassName()
    val containingFile: KSFile? = source.containingFile
}

sealed interface PatchConstructorParameter
object PatchConstructorOriginParameter : PatchConstructorParameter

class PatchSharedProperty(
    val name: String,
    type: KSType,
    val isMutable: Boolean,
) {
    val typeName: IrTypeName = type.asIrTypeName()
}

class PatchSharedFunction(
    val name: String,
    val parameters: List<FunctionParameter>,
    returnType: KSType?,
) {
    val returnTypeName: IrTypeName? = returnType?.asIrTypeName()
}

sealed class PatchHook(
    val name: String,
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
    name: String,
    descriptor: MethodDescriptor,
    parameters: List<HookParameter>,
) : PatchHook(name, descriptor, null, parameters, emptyList()) {
    override val isInjectBased: Boolean = true
}

class ConstructorHeadHook(
    name: String,
    descriptor: ConstructorDescriptor,
    parameters: List<HookParameter>,
    val phase: ConstructorHeadPhase,
) : PatchHook(name, descriptor, null, parameters, emptyList()) {
    override val isInjectBased: Boolean = true
}

class BodyHook(
    name: String,
    override val targetDescriptor: MethodDescriptor,
    returnType: KSType?,
    parameters: List<HookParameter>,
) : PatchHook(name, targetDescriptor, returnType, parameters, emptyList()), HookWithTarget

class TailHook(
    name: String,
    descriptor: InvokableDescriptor,
    parameters: List<HookParameter>,
) : PatchHook(name, descriptor, null, parameters, emptyList()) {
    override val isInjectBased: Boolean = true
}

class LocalHook(
    name: String,
    descriptor: InvokableDescriptor,
    type: KSType,
    parameters: List<HookParameter>,
    ordinals: List<Int>,
    val local: DomainLocal,
    val op: Op,
) : PatchHook(name, descriptor, type, parameters, ordinals) {
    val typeName: IrTypeName = type.asIrTypeName()
}

class InstanceofHook(
    name: String,
    descriptor: InvokableDescriptor,
    classDeclaration: KSClassDeclaration,
    returnType: KSType,
    parameters: List<HookParameter>,
    ordinals: List<Int>,
) : PatchHook(name, descriptor, returnType, parameters, ordinals) {
    val className: IrClassName = classDeclaration.asIrClassName()
}

class ReturnHook(
    name: String,
    descriptor: InvokableDescriptor,
    type: KSType?,
    parameters: List<HookParameter>,
    ordinals: List<Int>,
) : PatchHook(name, descriptor, type, parameters, ordinals) {
    override val isInjectBased: Boolean = type == null
}

class LiteralHook(
    name: String,
    descriptor: InvokableDescriptor,
    parameters: List<HookParameter>,
    type: KSType,
    val literal: Literal,
    ordinals: List<Int>,
) : PatchHook(name, descriptor, type, parameters, ordinals) {
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

class ClassLiteral(classDeclaration: KSClassDeclaration) : Literal {
    override fun getType(types: KSTypes): KSType = types.any
    val className: IrClassName = classDeclaration.asIrClassName()
}

object NullLiteral : Literal {
    override fun getType(types: KSTypes): KSType = types.nothing
}

class FieldGetHook(
    name: String,
    descriptor: InvokableDescriptor,
    type: KSType,
    override val targetDescriptor: FieldDescriptor,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
) : PatchHook(name, descriptor, type, parameters, ordinals), HookWithTarget {
    val typeName: IrTypeName = type.asIrTypeName()
}

class FieldSetHook(
    name: String,
    descriptor: InvokableDescriptor,
    type: KSType,
    override val targetDescriptor: FieldDescriptor,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
) : PatchHook(name, descriptor, type, parameters, ordinals), HookWithTarget {
    val typeName: IrTypeName = type.asIrTypeName()
}

class ArrayHook(
    name: String,
    descriptor: InvokableDescriptor,
    type: KSType,
    val componentType: KSType,
    val targetDescriptor: FieldDescriptor,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
    val op: Op,
) : PatchHook(name, descriptor, type, parameters, ordinals) {
    val typeName: IrTypeName = type.asIrTypeName()
    val componentTypeName: IrTypeName = componentType.asIrTypeName()
}

class CallHook(
    name: String,
    descriptor: InvokableDescriptor,
    returnType: KSType?,
    parameters: List<HookParameter>,
    override val targetDescriptor: InvokableDescriptor,
    ordinals: List<Int>,
) : PatchHook(name, descriptor, returnType, parameters, ordinals), HookWithTarget

sealed interface DomainLocal
class NamedLocal(val name: String) : DomainLocal
class PositionalLocal(val ordinal: Int) : DomainLocal

sealed interface HookParameter

sealed interface HookOriginParameter : HookParameter
object HookOriginValueParameter : HookOriginParameter

sealed class HookOriginDescriptorWrapperParameter(open val descriptor: Descriptor) : HookOriginParameter

class HookOriginDescriptorBodyWrapperParameter(
    override val descriptor: InvokableDescriptor
) : HookOriginDescriptorWrapperParameter(descriptor)

class HookOriginDescriptorFieldGetWrapperParameter(
    override val descriptor: FieldDescriptor
) : HookOriginDescriptorWrapperParameter(descriptor)

class HookOriginDescriptorFieldSetWrapperParameter(
    override val descriptor: FieldDescriptor
) : HookOriginDescriptorWrapperParameter(descriptor)

class HookOriginDescriptorArrayGetWrapperParameter(
    override val descriptor: FieldDescriptor,
    arrayComponentType: KSType
) : HookOriginDescriptorWrapperParameter(descriptor) {
    val arrayComponentTypeName: IrTypeName = arrayComponentType.asIrTypeName()
}

object HookOriginInstanceofParameter : HookOriginParameter

class HookOriginDescriptorArraySetWrapperParameter(
    override val descriptor: FieldDescriptor,
    arrayComponentType: KSType
) : HookOriginDescriptorWrapperParameter(descriptor) {
    val arrayComponentTypeName: IrTypeName = arrayComponentType.asIrTypeName()
}

class HookOriginDescriptorCallWrapperParameter(override val descriptor: InvokableDescriptor) :
    HookOriginDescriptorWrapperParameter(descriptor)

class HookCancelParameter(val descriptor: InvokableDescriptor) : HookParameter
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
    isVar: Boolean
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
