package io.github.recrafter.lapis.layers.validator

import com.google.devtools.ksp.containingFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.recrafter.lapis.annotations.ConstructorHeadPhase
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.annotations.Side
import io.github.recrafter.lapis.annotations.ZeroCondition
import io.github.recrafter.lapis.extensions.ks.KSClassDecl
import io.github.recrafter.lapis.extensions.ks.KSFile
import io.github.recrafter.lapis.extensions.ks.KSSymbol
import io.github.recrafter.lapis.extensions.ks.KSType
import io.github.recrafter.lapis.layers.lowering.asIr
import io.github.recrafter.lapis.layers.lowering.asIrTypeName
import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName
import kotlin.reflect.KClass

class ValidatorResult(
    val schemas: List<Schema>,
    val patches: List<Patch>,
)

class Schema(
    source: KSSymbol,

    classDecl: KSClassDecl,
    val targetClassDecl: KSClassDecl?,
    targetBinaryName: String,

    val hasAccess: Boolean,
    val isMarkedAsFinal: Boolean,
    val descriptors: List<Desc>,
) {
    val className: IrClassName = classDecl.asIr()
    val targetClassName: IrClassName = targetClassDecl?.asIr() ?: IrClassName.fromBinaryName(targetBinaryName)
    val containingFile: KSFile? = source.containingFile
}

sealed class Desc(
    val name: String,
    val targetName: String,
    classDecl: KSClassDecl,
    receiverType: KSType,
    val parameters: List<FunctionTypeParameter>,
    val returnType: KSType?,
    val isStatic: Boolean,
    val makePublic: Boolean,
    val removeFinal: Boolean,
) {
    val className: IrClassName = classDecl.asIr()
    val receiverTypeName: IrTypeName = receiverType.asIr()
    open val returnTypeName: IrTypeName? = returnType?.asIr()
}

sealed class InvokableDesc(
    name: String,
    targetName: String,
    classDecl: KSClassDecl,
    receiverType: KSType,
    parameters: List<FunctionTypeParameter>,
    returnType: KSType?,
    isStatic: Boolean,
    makePublic: Boolean,
    removeFinal: Boolean,
) : Desc(name, targetName, classDecl, receiverType, parameters, returnType, isStatic, makePublic, removeFinal)

class ConstructorDesc(
    name: String,
    classDecl: KSClassDecl,
    returnType: KSType,
    parameters: List<FunctionTypeParameter>,
    makePublic: Boolean,
    removeFinal: Boolean,
) : InvokableDesc(
    name, "", classDecl, returnType, parameters, returnType, true, makePublic, removeFinal
)

open class MethodDesc(
    name: String,
    targetName: String,
    classDecl: KSClassDecl,
    receiverType: KSType,
    returnType: KSType?,
    parameters: List<FunctionTypeParameter>,
    isStatic: Boolean,
    makePublic: Boolean,
    removeFinal: Boolean,
) : InvokableDesc(name, targetName, classDecl, receiverType, parameters, returnType, isStatic, makePublic, removeFinal)

class FieldDesc(
    name: String,
    targetName: String,
    classDecl: KSClassDecl,
    receiverType: KSType,
    val fieldType: KSType,
    val arrayComponentType: KSType?,
    isStatic: Boolean,
    makePublic: Boolean,
    removeFinal: Boolean,
) : Desc(name, targetName, classDecl, receiverType, emptyList(), fieldType, isStatic, makePublic, removeFinal) {
    val fieldTypeName: IrTypeName = fieldType.asIr()
}

class Patch(
    source: KSSymbol,

    val name: String,
    val side: Side,

    classDecl: KSClassDecl,
    targetClassDecl: KSClassDecl,

    val sharedProperties: List<SharedProperty>,
    val sharedFunctions: List<SharedFunction>,

    val hooks: List<DomainHook>,
) {
    val className: IrClassName = classDecl.asIr()
    val targetClassName: IrClassName = targetClassDecl.asIr()
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
    val desc: Desc,
    returnType: KSType?,
    val parameters: List<HookParameter>,
    val ordinals: List<Int>,
) {
    open val returnTypeName: IrTypeName? = returnType?.asIr()

    open val isInjectBased: Boolean = false
}

sealed interface HookWithTarget {
    val targetDesc: Desc
}

class MethodHeadHook(
    name: String,
    desc: MethodDesc,
    parameters: List<HookParameter>,
) : DomainHook(name, desc, null, parameters, emptyList()) {
    override val isInjectBased: Boolean = true
}

class ConstructorHeadHook(
    name: String,
    desc: ConstructorDesc,
    parameters: List<HookParameter>,
    val phase: ConstructorHeadPhase,
) : DomainHook(name, desc, null, parameters, emptyList()) {
    override val isInjectBased: Boolean = true
}

class BodyHook(
    name: String,
    override val targetDesc: MethodDesc,
    returnType: KSType?,
    parameters: List<HookParameter>,
) : DomainHook(name, targetDesc, returnType, parameters, emptyList()), HookWithTarget

class TailHook(
    name: String,
    desc: InvokableDesc,
    parameters: List<HookParameter>,
) : DomainHook(name, desc, null, parameters, emptyList()) {
    override val isInjectBased: Boolean = true
}

class LocalHook(
    name: String,
    desc: InvokableDesc,
    type: KSType,
    parameters: List<HookParameter>,
    ordinals: List<Int>,
    val local: DomainLocal,
    val isSet: Boolean,
) : DomainHook(name, desc, type, parameters, ordinals) {
    val typeName: IrTypeName = type.asIr()
}

// instanceof

class ReturnHook(
    name: String,
    desc: InvokableDesc,
    type: KSType?,
    parameters: List<HookParameter>,
    ordinals: List<Int>,
) : DomainHook(name, desc, type, parameters, ordinals) {
    override val isInjectBased: Boolean = type == null
}

class LiteralHook(
    name: String,
    desc: InvokableDesc,
    parameters: List<HookParameter>,
    type: KSType,
    val literal: Literal,
    ordinals: List<Int>,
) : DomainHook(name, desc, type, parameters, ordinals) {
    val typeName: IrTypeName = type.asIr()
}

sealed interface Literal {
    val kClass: KClass<*>?
}

class ZeroLiteral(val conditions: List<ZeroCondition>) : IntLiteral(0)
open class IntLiteral(val value: Int) : Literal {
    override val kClass: KClass<*> = Int::class
}

class FloatLiteral(val value: Float) : Literal {
    override val kClass: KClass<*> = Float::class
}

class LongLiteral(val value: Long) : Literal {
    override val kClass: KClass<*> = Long::class
}

class DoubleLiteral(val value: Double) : Literal {
    override val kClass: KClass<*> = Double::class
}

class StringLiteral(val value: String) : Literal {
    override val kClass: KClass<*> = String::class
}

class ClassLiteral(classDecl: KSClassDecl) : Literal {
    override val kClass: KClass<*> = KClass::class
    val className: IrClassName = classDecl.asIr()
}

object NullLiteral : Literal {
    override val kClass: KClass<*>? = null
}

class FieldGetHook(
    name: String,
    desc: InvokableDesc,
    type: KSType,
    override val targetDesc: FieldDesc,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
) : DomainHook(name, desc, type, parameters, ordinals), HookWithTarget {
    val typeName: IrTypeName = type.asIr()
}

class FieldSetHook(
    name: String,
    desc: InvokableDesc,
    type: KSType,
    override val targetDesc: FieldDesc,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
) : DomainHook(name, desc, type, parameters, ordinals), HookWithTarget {
    val typeName: IrTypeName = type.asIr()
}

class ArrayHook(
    name: String,
    desc: InvokableDesc,
    type: KSType,
    val componentType: KSType,
    val targetDesc: FieldDesc,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
    val op: Op,
) : DomainHook(name, desc, type, parameters, ordinals) {
    val typeName: IrTypeName = type.asIr()
    val componentTypeName: IrTypeName = componentType.asIr()
}

class CallHook(
    name: String,
    desc: InvokableDesc,
    returnType: KSType?,
    parameters: List<HookParameter>,
    override val targetDesc: InvokableDesc,
    ordinals: List<Int>,
) : DomainHook(name, desc, returnType, parameters, ordinals), HookWithTarget

sealed interface DomainLocal
class NamedLocal(val name: String) : DomainLocal
class PositionalLocal(val ordinal: Int) : DomainLocal

sealed interface HookParameter

sealed interface HookOriginParameter : HookParameter
class HookOriginValueParameter : HookOriginParameter

sealed class HookOriginDescParameter(open val desc: Desc) : HookOriginParameter
class HookOriginDescBodyParameter(override val desc: InvokableDesc) : HookOriginDescParameter(desc)
class HookOriginDescFieldGetParameter(override val desc: FieldDesc) : HookOriginDescParameter(desc)
class HookOriginDescFieldSetParameter(override val desc: FieldDesc) : HookOriginDescParameter(desc)
class HookOriginDescArrayGetParameter(
    override val desc: FieldDesc,
    arrayComponentType: KSType
) : HookOriginDescParameter(desc) {
    val arrayComponentTypeName: IrTypeName = arrayComponentType.asIr()
}

class HookOriginDescCallParameter(override val desc: InvokableDesc) : HookOriginDescParameter(desc)

class HookCancelParameter(val desc: InvokableDesc) : HookParameter
object HookOrdinalParameter : HookParameter
class HookParamParameter(val name: String, val index: Int) : HookParameter
class HookLocalParameter(val name: String, type: KSType, val local: DomainLocal) : HookParameter {
    val typeName: IrTypeName = type.asIr()
}

class FunctionParameter(val name: String, type: KSType) {
    val typeName: IrTypeName = type.asIr()
}

class FunctionTypeParameter(val name: String?, val type: KSType) {
    val typeName: IrTypeName = type.asIr()
}

private fun KSType.asIr(): IrTypeName =
    toTypeName().asIrTypeName()

private fun KSClassDecl.asIr(): IrClassName =
    toClassName().asIr()
