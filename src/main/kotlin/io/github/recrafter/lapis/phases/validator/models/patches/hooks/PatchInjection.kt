package io.github.recrafter.lapis.phases.validator.models.patches.hooks

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.annotations.ConstructorHeadPhase
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import io.github.recrafter.lapis.phases.validator.models.common.MixinAnnotation
import io.github.recrafter.lapis.phases.validator.models.schemas.*

sealed interface PatchInjection {
    val jvmName: String
    val isStatic: Boolean
}

class PatchNativeInjection(
    override val jvmName: String,
    val mixinAnnotations: List<MixinAnnotation>,
    override val isStatic: Boolean,
    val parameters: List<PatchNativeInjectionParameter>,
    val returnType: KSType?,
) : PatchInjection

class PatchNativeInjectionParameter(
    val name: String,
    val type: KSType,
    val mixinAnnotations: List<MixinAnnotation>,
)

sealed class PatchHook(
    override val jvmName: String,
    val methodDescriptor: Descriptor,
    returnType: KSType?,
    val parameters: List<HookParameter>,
    val ordinals: Set<Int>,
) : PatchInjection {
    open val returnTypeName: IrTypeName? = returnType?.asIrTypeName()
    open val isInjectBased: Boolean = false
    override val isStatic: Boolean = methodDescriptor.isStatic
}

sealed interface HookWithTarget {
    val targetDescriptor: Descriptor
}

class MethodHeadHook(
    jvmName: String,
    methodDescriptor: MethodDescriptor,
    parameters: List<HookParameter>,
) : PatchHook(jvmName, methodDescriptor, null, parameters, emptySet()) {
    override val isInjectBased: Boolean = true
}

class ConstructorHeadHook(
    jvmName: String,
    methodDescriptor: ConstructorDescriptor,
    parameters: List<HookParameter>,
    val phase: ConstructorHeadPhase,
) : PatchHook(jvmName, methodDescriptor, null, parameters, emptySet()) {
    override val isInjectBased: Boolean = true
}

class BodyHook(
    jvmName: String,
    methodDescriptor: MethodDescriptor,
    returnType: KSType?,
    parameters: List<HookParameter>,
) : PatchHook(jvmName, methodDescriptor, returnType, parameters, emptySet()), HookWithTarget {
    override val targetDescriptor: Descriptor = methodDescriptor
}

class TailHook(
    jvmName: String,
    methodDescriptor: InvokableDescriptor,
    parameters: List<HookParameter>,
) : PatchHook(jvmName, methodDescriptor, null, parameters, emptySet()) {
    override val isInjectBased: Boolean = true
}

class LocalHook(
    jvmName: String,
    methodDescriptor: InvokableDescriptor,
    type: KSType,
    parameters: List<HookParameter>,
    ordinals: Set<Int>,
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
    ordinals: Set<Int>,
) : PatchHook(jvmName, methodDescriptor, returnType, parameters, ordinals) {
    val typeClassName: IrClassName = typeClassDeclaration.asIrClassName()
}

class ReturnHook(
    jvmName: String,
    methodDescriptor: InvokableDescriptor,
    type: KSType?,
    parameters: List<HookParameter>,
    ordinals: Set<Int>,
) : PatchHook(jvmName, methodDescriptor, type, parameters, ordinals) {
    override val isInjectBased: Boolean = type == null
}

class LiteralHook(
    jvmName: String,
    methodDescriptor: InvokableDescriptor,
    parameters: List<HookParameter>,
    type: KSType,
    val literal: HookLiteral,
    ordinals: Set<Int>,
) : PatchHook(jvmName, methodDescriptor, type, parameters, ordinals) {
    val typeName: IrTypeName = type.asIrTypeName()
}

class FieldGetHook(
    jvmName: String,
    methodDescriptor: InvokableDescriptor,
    type: KSType,
    override val targetDescriptor: FieldDescriptor,
    ordinals: Set<Int>,
    parameters: List<HookParameter>,
) : PatchHook(jvmName, methodDescriptor, type, parameters, ordinals), HookWithTarget {
    val typeName: IrTypeName = type.asIrTypeName()
}

class FieldSetHook(
    jvmName: String,
    methodDescriptor: InvokableDescriptor,
    type: KSType,
    override val targetDescriptor: FieldDescriptor,
    ordinals: Set<Int>,
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
    ordinals: Set<Int>,
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
    ordinals: Set<Int>,
) : PatchHook(jvmName, methodDescriptor, returnType, parameters, ordinals), HookWithTarget
