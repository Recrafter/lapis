package io.github.recrafter.lapis.layers.validator

import com.google.devtools.ksp.containingFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.recrafter.lapis.annotations.Side
import io.github.recrafter.lapis.annotations.Zero
import io.github.recrafter.lapis.extensions.common.defaultValue
import io.github.recrafter.lapis.extensions.ksp.*
import io.github.recrafter.lapis.layers.lowering.asIr
import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName
import org.spongepowered.asm.mixin.injection.At

class ValidatorResult(
    val schemas: List<Schema>,
    val patches: List<Patch>,
)

class Schema(
    source: KSPSymbol,

    classDecl: KSPClassDecl,
    targetClassDecl: KSPClassDecl,
    val hasAccess: Boolean,
    val isMarkedAsFinal: Boolean,
    val descriptors: List<Desc>,
) {
    val className: IrClassName = classDecl.asIr()
    val targetClassName: IrClassName = targetClassDecl.asIr()
    val containingFile: KSPFile? = source.containingFile?.warmUp()
}

sealed class Desc(
    val name: String,
    val targetName: String,
    classDecl: KSPClassDecl,
    receiverType: KSPType,
    val parameters: List<FunctionTypeParameter>,
    val returnType: KSPType?,
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
    classDecl: KSPClassDecl,
    receiverType: KSPType,
    parameters: List<FunctionTypeParameter>,
    returnType: KSPType?,
    isStatic: Boolean,
    makePublic: Boolean,
    removeFinal: Boolean,
) : Desc(name, targetName, classDecl, receiverType, parameters, returnType, isStatic, makePublic, removeFinal)

class ConstructorDesc(
    name: String,
    classDecl: KSPClassDecl,
    returnType: KSPType,
    parameters: List<FunctionTypeParameter>,
    makePublic: Boolean,
    removeFinal: Boolean,
) : InvokableDesc(
    name, "", classDecl, returnType, parameters, returnType, true, makePublic, removeFinal
)

open class MethodDesc(
    name: String,
    targetName: String,
    classDecl: KSPClassDecl,
    receiverType: KSPType,
    returnType: KSPType?,
    parameters: List<FunctionTypeParameter>,
    isStatic: Boolean,
    makePublic: Boolean,
    removeFinal: Boolean,
) : InvokableDesc(name, targetName, classDecl, receiverType, parameters, returnType, isStatic, makePublic, removeFinal)

class FieldDesc(
    name: String,
    targetName: String,
    classDecl: KSPClassDecl,
    receiverType: KSPType,
    val fieldType: KSPType,
    isStatic: Boolean,
    makePublic: Boolean,
    removeFinal: Boolean,
) : Desc(name, targetName, classDecl, receiverType, emptyList(), fieldType, isStatic, makePublic, removeFinal) {
    val fieldTypeName: IrTypeName = fieldType.asIr()
}

class Patch(
    source: KSPSymbol,

    val name: String,
    val side: Side,

    classDecl: KSPClassDecl,
    targetClassDecl: KSPClassDecl,

    val sharedProperties: List<SharedProperty>,
    val sharedFunctions: List<SharedFunction>,

    val hooks: List<HookModel>,
) {
    val className: IrClassName = classDecl.asIr()
    val targetClassName: IrClassName = targetClassDecl.asIr()
    val containingFile: KSPFile? = source.containingFile?.warmUp()
}

class SharedProperty(
    val name: String,
    type: KSPType,
    val isMutable: Boolean,
) {
    val typeName: IrTypeName = type.asIr()
}

class SharedFunction(
    val name: String,
    val parameters: List<FunctionParameter>,
    returnType: KSPType?,
) {
    val returnTypeName: IrTypeName? = returnType?.asIr()
}

sealed class HookModel(
    val name: String,
    val desc: Desc,
    returnType: KSPType?,
    val parameters: List<HookParameter>,
    val ordinals: List<Int>,
) {
    val returnTypeName: IrTypeName? = returnType?.asIr()
}

sealed interface HookWithTarget {
    val targetDesc: Desc
}

class BodyHook(
    name: String,
    override val targetDesc: MethodDesc,
    returnType: KSPType?,
    parameters: List<HookParameter>,
) : HookModel(name, targetDesc, returnType, parameters, listOf(At::ordinal.defaultValue)), HookWithTarget

class LiteralHook(
    name: String,
    desc: InvokableDesc,
    parameters: List<HookParameter>,
    type: KSPType,
    val literal: Literal,
    ordinals: List<Int>,
) : HookModel(name, desc, type, parameters, ordinals) {
    val typeName: IrTypeName = type.asIr()
}

class CallHook(
    name: String,
    desc: InvokableDesc,
    returnType: KSPType?,
    parameters: List<HookParameter>,
    override val targetDesc: InvokableDesc,
    ordinals: List<Int>,
) : HookModel(name, desc, returnType, parameters, ordinals), HookWithTarget

sealed interface Literal
class ZeroLiteral(val conditions: List<Zero.Condition>) : Literal
class IntLiteral(val value: Int) : Literal
class FloatLiteral(val value: Float) : Literal
class LongLiteral(val value: Long) : Literal
class DoubleLiteral(val value: Double) : Literal
class StringLiteral(val value: String) : Literal
class ClassLiteral(classDecl: KSPClassDecl) : Literal {
    val className: IrClassName = classDecl.asIr()
}

object NullLiteral : Literal

class FieldGetHook(
    name: String,
    desc: InvokableDesc,
    type: KSPType,
    override val targetDesc: FieldDesc,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
) : HookModel(name, desc, type, parameters, ordinals), HookWithTarget {
    val typeName: IrTypeName = type.asIr()
}

class FieldWriteHook(
    name: String,
    desc: InvokableDesc,
    type: KSPType,
    override val targetDesc: FieldDesc,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
) : HookModel(name, desc, type, parameters, ordinals), HookWithTarget {
    val typeName: IrTypeName = type.asIr()
}

sealed interface HookParameter

sealed interface HookOriginParameter : HookParameter
class HookOriginValueParameter : HookOriginParameter

sealed class HookOriginDescParameter(open val desc: Desc) : HookOriginParameter
class HookOriginCallParameter(override val desc: InvokableDesc) : HookOriginDescParameter(desc)

class HookCancelParameter(val desc: InvokableDesc) : HookParameter
class HookParamParameter(val name: String) : HookParameter
class HookLocalParameter(val name: String, type: KSPType, val ordinal: Int) : HookParameter {
    val typeName: IrTypeName = type.asIr()
}

class FunctionParameter(val name: String, type: KSPType) {
    val typeName: IrTypeName = type.asIr()
}

class FunctionTypeParameter(val name: String?, type: KSPType) {
    val typeName: IrTypeName = type.asIr()
}

private fun KSPType.asIr(): IrTypeName =
    toTypeName().asIr()

private fun KSPClassDecl.asIr(): IrClassName =
    toClassName().asIr()
