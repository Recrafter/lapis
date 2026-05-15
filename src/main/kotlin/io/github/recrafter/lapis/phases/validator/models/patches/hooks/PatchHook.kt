package io.github.recrafter.lapis.phases.validator.models.patches.hooks

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.annotations.ConstructorHeadPhase
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import io.github.recrafter.lapis.phases.validator.models.schemas.*

sealed class PatchHook(
    val jvmName: String,
    val methodDescriptor: Descriptor,
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
    methodDescriptor: MethodDescriptor,
    parameters: List<HookParameter>,
) : PatchHook(jvmName, methodDescriptor, null, parameters, emptyList()) {
    override val isInjectBased: Boolean = true
}

class ConstructorHeadHook(
    jvmName: String,
    methodDescriptor: ConstructorDescriptor,
    parameters: List<HookParameter>,
    val phase: ConstructorHeadPhase,
) : PatchHook(jvmName, methodDescriptor, null, parameters, emptyList()) {
    override val isInjectBased: Boolean = true
}

class BodyHook(
    jvmName: String,
    methodDescriptor: MethodDescriptor,
    returnType: KSType?,
    parameters: List<HookParameter>,
) : PatchHook(jvmName, methodDescriptor, returnType, parameters, emptyList()), HookWithTarget {
    override val targetDescriptor: Descriptor = methodDescriptor
}

class TailHook(
    jvmName: String,
    methodDescriptor: InvokableDescriptor,
    parameters: List<HookParameter>,
) : PatchHook(jvmName, methodDescriptor, null, parameters, emptyList()) {
    override val isInjectBased: Boolean = true
}

class LocalHook(
    jvmName: String,
    methodDescriptor: InvokableDescriptor,
    type: KSType,
    parameters: List<HookParameter>,
    ordinals: List<Int>,
    val local: HookLocal,
    val op: Op,
) : PatchHook(jvmName, methodDescriptor, type, parameters, ordinals) {
    val typeName: IrTypeName = type.asIrTypeName()
}

class InstanceofHook(
    jvmName: String,
    methodDescriptor: InvokableDescriptor,
    typeClassDeclaration: KSClassDeclaration,
    returnType: KSType,
    parameters: List<HookParameter>,
    ordinals: List<Int>,
) : PatchHook(jvmName, methodDescriptor, returnType, parameters, ordinals) {
    val typeClassName: IrClassName = typeClassDeclaration.asIrClassName()
}

class ReturnHook(
    jvmName: String,
    methodDescriptor: InvokableDescriptor,
    type: KSType?,
    parameters: List<HookParameter>,
    ordinals: List<Int>,
) : PatchHook(jvmName, methodDescriptor, type, parameters, ordinals) {
    override val isInjectBased: Boolean = type == null
}

class LiteralHook(
    jvmName: String,
    methodDescriptor: InvokableDescriptor,
    parameters: List<HookParameter>,
    type: KSType,
    val literal: HookLiteral,
    ordinals: List<Int>,
) : PatchHook(jvmName, methodDescriptor, type, parameters, ordinals) {
    val typeName: IrTypeName = type.asIrTypeName()
}

class FieldGetHook(
    jvmName: String,
    methodDescriptor: InvokableDescriptor,
    type: KSType,
    override val targetDescriptor: FieldDescriptor,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
) : PatchHook(jvmName, methodDescriptor, type, parameters, ordinals), HookWithTarget {
    val typeName: IrTypeName = type.asIrTypeName()
}

class FieldSetHook(
    jvmName: String,
    methodDescriptor: InvokableDescriptor,
    type: KSType,
    override val targetDescriptor: FieldDescriptor,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
) : PatchHook(jvmName, methodDescriptor, type, parameters, ordinals), HookWithTarget {
    val typeName: IrTypeName = type.asIrTypeName()
}

class ArrayHook(
    jvmName: String,
    methodDescriptor: InvokableDescriptor,
    type: KSType,
    val componentType: KSType,
    val targetDescriptor: FieldDescriptor,
    ordinals: List<Int>,
    parameters: List<HookParameter>,
    val op: Op,
) : PatchHook(jvmName, methodDescriptor, type, parameters, ordinals) {
    val typeName: IrTypeName = type.asIrTypeName()
    val componentTypeName: IrTypeName = componentType.asIrTypeName()
}

class CallHook(
    jvmName: String,
    methodDescriptor: InvokableDescriptor,
    returnType: KSType?,
    parameters: List<HookParameter>,
    override val targetDescriptor: InvokableDescriptor,
    ordinals: List<Int>,
) : PatchHook(jvmName, methodDescriptor, returnType, parameters, ordinals), HookWithTarget
