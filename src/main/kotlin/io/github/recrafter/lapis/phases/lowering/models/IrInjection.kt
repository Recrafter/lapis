package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.extensions.kp.KPBoolean
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed class IrInjection(
    val jvmName: String,
    val methodMixinReference: String,
    val returnTypeName: IrTypeName?,
    val parameters: List<IrInjectionParameter>,
    val hookArguments: List<IrHookArgument>,
    val isStatic: Boolean,
    val ordinal: Int?,
)

sealed interface IrTargetInjection {
    val targetMixinReference: String
    val isStaticTarget: Boolean
}

sealed interface IrInjectInjection

class IrMethodHeadInjection(
    jvmName: String,
    methodMixinReference: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    isStatic: Boolean,
) : IrInjection(jvmName, methodMixinReference, null, parameters, hookArguments, isStatic, null), IrInjectInjection

class IrConstructorHeadInjection(
    jvmName: String,
    methodMixinReference: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val atArgs: List<Pair<String, String>>,
    isStatic: Boolean,
) : IrInjection(jvmName, methodMixinReference, null, parameters, hookArguments, isStatic, null), IrInjectInjection

class IrWrapMethodInjection(
    jvmName: String,
    methodMixinReference: String,
    override val isStaticTarget: Boolean,
    returnTypeName: IrTypeName?,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    isStatic: Boolean,
) : IrInjection(jvmName, methodMixinReference, returnTypeName, parameters, hookArguments, isStatic, null),
    IrTargetInjection {
    override val targetMixinReference: String = methodMixinReference
}

class IrReturnInjection(
    jvmName: String,
    methodMixinReference: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    ordinal: Int?,
    val isTail: Boolean,
    isStatic: Boolean,
) : IrInjection(jvmName, methodMixinReference, null, parameters, hookArguments, isStatic, ordinal), IrInjectInjection

class IrModifyVariableInjection(
    jvmName: String,
    methodMixinReference: String,
    returnTypeName: IrTypeName?,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val local: IrLocal,
    val op: Op,
    ordinal: Int?,
    isStatic: Boolean,
) : IrInjection(jvmName, methodMixinReference, returnTypeName, parameters, hookArguments, isStatic, ordinal)

class IrModifyReturnValueInjection(
    jvmName: String,
    methodMixinReference: String,
    returnTypeName: IrTypeName?,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    ordinal: Int?,
    isStatic: Boolean,
) : IrInjection(jvmName, methodMixinReference, returnTypeName, parameters, hookArguments, isStatic, ordinal)

class IrWrapOperationInjection(
    jvmName: String,
    methodMixinReference: String,
    returnTypeName: IrTypeName?,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    override val targetMixinReference: String,
    override val isStaticTarget: Boolean,
    val isConstructorCall: Boolean,
    ordinal: Int?,
    isStatic: Boolean,
) : IrInjection(jvmName, methodMixinReference, returnTypeName, parameters, hookArguments, isStatic, ordinal),
    IrTargetInjection

class IrModifyExpressionValueInjection(
    jvmName: String,
    methodMixinReference: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val constantTypeName: IrTypeName,
    val atArgs: List<Pair<String, String>>,
    ordinal: Int?,
    isStatic: Boolean,
) : IrInjection(jvmName, methodMixinReference, constantTypeName, parameters, hookArguments, isStatic, ordinal)

class IrFieldGetInjection(
    jvmName: String,
    methodMixinReference: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    override val targetMixinReference: String,
    override val isStaticTarget: Boolean,
    ordinal: Int?,
    fieldTypeName: IrTypeName,
    isStatic: Boolean,
) : IrInjection(jvmName, methodMixinReference, fieldTypeName, parameters, hookArguments, isStatic, ordinal),
    IrTargetInjection

class IrFieldSetInjection(
    jvmName: String,
    methodMixinReference: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    override val targetMixinReference: String,
    override val isStaticTarget: Boolean,
    ordinal: Int?,
    isStatic: Boolean,
) : IrInjection(jvmName, methodMixinReference, null, parameters, hookArguments, isStatic, ordinal), IrTargetInjection

class IrArrayInjection(
    jvmName: String,
    methodMixinReference: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    override val targetMixinReference: String,
    override val isStaticTarget: Boolean,
    ordinal: Int?,
    componentTypeName: IrTypeName,
    isStatic: Boolean,
    val op: Op,
) : IrInjection(
    jvmName, methodMixinReference,
    returnTypeName = if (op == Op.Set) null else componentTypeName,
    parameters, hookArguments, isStatic, ordinal
), IrTargetInjection

class IrInstanceofInjection(
    jvmName: String,
    methodMixinReference: String,
    val className: IrClassName,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    ordinal: Int?,
    isStatic: Boolean,
) : IrInjection(
    jvmName, methodMixinReference,
    returnTypeName = KPBoolean.asIrTypeName(),
    parameters, hookArguments, isStatic, ordinal
)
