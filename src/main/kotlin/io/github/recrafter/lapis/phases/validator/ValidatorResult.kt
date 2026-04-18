package io.github.recrafter.lapis.phases.validator

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.recrafter.lapis.annotations.ConstructorHeadPhase
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.annotations.Side
import io.github.recrafter.lapis.annotations.ZeroCondition
import io.github.recrafter.lapis.phases.lowering.asIr
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
    val targetClassDeclaration: KSClassDeclaration?,
    targetBinaryName: String,

    val makePublic: Boolean,
    val removeFinal: Boolean,
    val descriptors: List<Descriptor>,
) {
    val className: IrClassName = classDeclaration.asIr()
    val targetClassName: IrClassName = targetClassDeclaration?.asIr() ?: IrClassName.fromBinaryName(targetBinaryName)
    val containingFile: KSFile? = source.containingFile
}

sealed class Descriptor(
    val name: String,
    val targetName: String,
    classDeclaration: KSClassDeclaration,
    receiverType: KSType,
    val parameters: List<FunctionTypeParameter>,
    val returnType: KSType?,
    val isStatic: Boolean,
    val makePublic: Boolean,
    val removeFinal: Boolean,
) {
    val className: IrClassName = classDeclaration.asIr()
    val receiverTypeName: IrTypeName = receiverType.asIr()
    open val returnTypeName: IrTypeName? = returnType?.asIr()
}

sealed class InvokableDescriptor(
    name: String,
    targetName: String,
    classDeclaration: KSClassDeclaration,
    receiverType: KSType,
    parameters: List<FunctionTypeParameter>,
    returnType: KSType?,
    isStatic: Boolean,
    makePublic: Boolean,
    removeFinal: Boolean,
) : Descriptor(
    name,
    targetName,
    classDeclaration,
    receiverType,
    parameters,
    returnType,
    isStatic,
    makePublic,
    removeFinal
)

class ConstructorDescriptor(
    name: String,
    classDeclaration: KSClassDeclaration,
    returnType: KSType,
    parameters: List<FunctionTypeParameter>,
    makePublic: Boolean,
) : InvokableDescriptor(
    name, "", classDeclaration, returnType, parameters, returnType, true, makePublic, false
)

open class MethodDescriptor(
    name: String,
    targetName: String,
    classDeclaration: KSClassDeclaration,
    receiverType: KSType,
    returnType: KSType?,
    parameters: List<FunctionTypeParameter>,
    isStatic: Boolean,
    makePublic: Boolean,
    removeFinal: Boolean,
) : InvokableDescriptor(
    name,
    targetName,
    classDeclaration,
    receiverType,
    parameters,
    returnType,
    isStatic,
    makePublic,
    removeFinal
)

class FieldDescriptor(
    name: String,
    targetName: String,
    classDeclaration: KSClassDeclaration,
    receiverType: KSType,
    val fieldType: KSType,
    val arrayComponentType: KSType?,
    isStatic: Boolean,
    makePublic: Boolean,
    removeFinal: Boolean,
) : Descriptor(
    name,
    targetName,
    classDeclaration,
    receiverType,
    emptyList(),
    fieldType,
    isStatic,
    makePublic,
    removeFinal
) {
    val fieldTypeName: IrTypeName = fieldType.asIr()
}

class Patch(
    source: KSNode,

    val name: String,
    val side: Side,

    classDeclaration: KSClassDeclaration,
    targetClassDeclaration: KSClassDeclaration,

    val sharedProperties: List<SharedProperty>,
    val sharedFunctions: List<SharedFunction>,

    val hooks: List<DomainHook>,
) {
    val className: IrClassName = classDeclaration.asIr()
    val targetClassName: IrClassName = targetClassDeclaration.asIr()
    val containingFile: KSFile? = source.containingFile
}

class SharedProperty(
    val name: String,
    type: KSType,
    val isMutable: Boolean,
) {
    val typeName: IrTypeName = type.asIr()
}

class SharedFunction(
    val name: String,
    val parameters: List<FunctionParameter>,
    returnType: KSType?,
) {
    val returnTypeName: IrTypeName? = returnType?.asIr()
}

sealed class DomainHook(
    val name: String,
    val descriptor: Descriptor,
    returnType: KSType?,
    val parameters: List<HookParameter>,
    val ordinals: List<Int>,
) {
    open val returnTypeName: IrTypeName? = returnType?.asIr()

    open val isInjectBased: Boolean = false
}

sealed interface HookWithTarget {
    val targetDescriptor: Descriptor
}

class MethodHeadHook(
    name: String,
    descriptor: MethodDescriptor,
    parameters: List<HookParameter>,
) : DomainHook(name, descriptor, null, parameters, emptyList()) {
    override val isInjectBased: Boolean = true
}

class ConstructorHeadHook(
    name: String,
    descriptor: ConstructorDescriptor,
    parameters: List<HookParameter>,
    val phase: ConstructorHeadPhase,
) : DomainHook(name, descriptor, null, parameters, emptyList()) {
    override val isInjectBased: Boolean = true
}

class BodyHook(
    name: String,
    override val targetDescriptor: MethodDescriptor,
    returnType: KSType?,
    parameters: List<HookParameter>,
) : DomainHook(name, targetDescriptor, returnType, parameters, emptyList()), HookWithTarget

class TailHook(
    name: String,
    descriptor: InvokableDescriptor,
    parameters: List<HookParameter>,
) : DomainHook(name, descriptor, null, parameters, emptyList()) {
    override val isInjectBased: Boolean = true
}

class LocalHook(
    name: String,
    descriptor: InvokableDescriptor,
    type: KSType,
    parameters: List<HookParameter>,
    ordinals: List<Int>,
    val local: DomainLocal,
    val isSet: Boolean,
) : DomainHook(name, descriptor, type, parameters, ordinals) {
    val typeName: IrTypeName = type.asIr()
}

class InstanceofHook(
    name: String,
    descriptor: InvokableDescriptor,
    classDeclaration: KSClassDeclaration,
    returnType: KSType,
    parameters: List<HookParameter>,
    ordinals: List<Int>,
) : DomainHook(name, descriptor, returnType, parameters, ordinals) {
    val className: IrClassName = classDeclaration.asIr()
}

class ReturnHook(
    name: String,
    descriptor: InvokableDescriptor,
    type: KSType?,
    parameters: List<HookParameter>,
    ordinals: List<Int>,
) : DomainHook(name, descriptor, type, parameters, ordinals) {
    override val isInjectBased: Boolean = type == null
}

class LiteralHook(
    name: String,
    descriptor: InvokableDescriptor,
    parameters: List<HookParameter>,
    type: KSType,
    val literal: Literal,
    ordinals: List<Int>,
) : DomainHook(name, descriptor, type, parameters, ordinals) {
    val typeName: IrTypeName = type.asIr()
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
    val className: IrClassName = classDeclaration.asIr()
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
) : DomainHook(name, descriptor, type, parameters, ordinals), HookWithTarget {
    val typeName: IrTypeName = type.asIr()
}

class FieldSetHook(
    name: String,
    descriptor: InvokableDescriptor,
    type: KSType,
    override val targetDescriptor: FieldDescriptor,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
) : DomainHook(name, descriptor, type, parameters, ordinals), HookWithTarget {
    val typeName: IrTypeName = type.asIr()
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
) : DomainHook(name, descriptor, type, parameters, ordinals) {
    val typeName: IrTypeName = type.asIr()
    val componentTypeName: IrTypeName = componentType.asIr()
}

class CallHook(
    name: String,
    descriptor: InvokableDescriptor,
    returnType: KSType?,
    parameters: List<HookParameter>,
    override val targetDescriptor: InvokableDescriptor,
    ordinals: List<Int>,
) : DomainHook(name, descriptor, returnType, parameters, ordinals), HookWithTarget

sealed interface DomainLocal
class NamedLocal(val name: String) : DomainLocal
class PositionalLocal(val ordinal: Int) : DomainLocal

sealed interface HookParameter

sealed interface HookOriginParameter : HookParameter
object HookOriginValueParameter : HookOriginParameter

sealed class HookOriginDescriptorParameter(open val descriptor: Descriptor) : HookOriginParameter

class HookOriginDescriptorBodyParameter(
    override val descriptor: InvokableDescriptor
) : HookOriginDescriptorParameter(descriptor)

class HookOriginDescriptorFieldGetParameter(
    override val descriptor: FieldDescriptor
) : HookOriginDescriptorParameter(descriptor)

class HookOriginDescriptorFieldSetParameter(
    override val descriptor: FieldDescriptor
) : HookOriginDescriptorParameter(descriptor)

class HookOriginDescriptorArrayGetParameter(
    override val descriptor: FieldDescriptor,
    arrayComponentType: KSType
) : HookOriginDescriptorParameter(descriptor) {
    val arrayComponentTypeName: IrTypeName = arrayComponentType.asIr()
}

object HookOriginInstanceofParameter : HookOriginParameter

class HookOriginDescriptorArraySetParameter(
    override val descriptor: FieldDescriptor,
    arrayComponentType: KSType
) : HookOriginDescriptorParameter(descriptor) {
    val arrayComponentTypeName: IrTypeName = arrayComponentType.asIr()
}

class HookOriginDescriptorCallParameter(override val descriptor: InvokableDescriptor) :
    HookOriginDescriptorParameter(descriptor)

class HookCancelParameter(val descriptor: InvokableDescriptor) : HookParameter
object HookOrdinalParameter : HookParameter

sealed class HookLocalParameter(
    val name: String,
    type: KSType,
    val isVar: Boolean,
) : HookParameter {
    val typeName: IrTypeName = type.asIr()
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

class FunctionParameter(val name: String, type: KSType) {
    val typeName: IrTypeName = type.asIr()
}

class FunctionTypeParameter(val name: String?, val type: KSType) {
    val typeName: IrTypeName = type.asIr()
}

private fun KSType.asIr(): IrTypeName =
    toTypeName().asIrTypeName()

private fun KSClassDeclaration.asIr(): IrClassName =
    toClassName().asIr()
