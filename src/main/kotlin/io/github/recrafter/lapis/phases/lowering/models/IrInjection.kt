package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.extensions.kp.KPBoolean
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed class IrInjection(
    val name: String,
    val methodMixinRef: String,
    val returnTypeName: IrTypeName?,
    val parameters: List<IrInjectionParameter>,
    val hookArguments: List<IrHookArgument>,
    val isStatic: Boolean,
    val ordinal: Int?,
)

sealed interface IrTargetInjection {
    val targetMixinRef: String
    val isStaticTarget: Boolean
}

sealed interface IrInjectInjection

class IrMethodHeadInjection(
    name: String,
    methodMixinRef: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    isStatic: Boolean,
) : IrInjection(name, methodMixinRef, null, parameters, hookArguments, isStatic, null), IrInjectInjection

class IrConstructorHeadInjection(
    name: String,
    methodMixinRef: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val atArgs: List<Pair<String, String>>,
    isStatic: Boolean,
) : IrInjection(name, methodMixinRef, null, parameters, hookArguments, isStatic, null), IrInjectInjection

class IrWrapMethodInjection(
    name: String,
    methodMixinRef: String,
    override val isStaticTarget: Boolean,
    returnTypeName: IrTypeName?,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    isStatic: Boolean,
) : IrInjection(name, methodMixinRef, returnTypeName, parameters, hookArguments, isStatic, null), IrTargetInjection {
    override val targetMixinRef: String = methodMixinRef
}

class IrReturnInjection(
    name: String,
    methodMixinRef: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    ordinal: Int?,
    val isTail: Boolean,
    isStatic: Boolean,
) : IrInjection(name, methodMixinRef, null, parameters, hookArguments, isStatic, ordinal), IrInjectInjection

class IrModifyVariableInjection(
    name: String,
    methodMixinRef: String,
    returnTypeName: IrTypeName?,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val local: IrLocal,
    val isSet: Boolean,
    ordinal: Int?,
    isStatic: Boolean,
) : IrInjection(name, methodMixinRef, returnTypeName, parameters, hookArguments, isStatic, ordinal)

class IrModifyReturnValueInjection(
    name: String,
    methodMixinRef: String,
    returnTypeName: IrTypeName?,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    ordinal: Int?,
    isStatic: Boolean,
) : IrInjection(name, methodMixinRef, returnTypeName, parameters, hookArguments, isStatic, ordinal)

class IrWrapOperationInjection(
    name: String,
    methodMixinRef: String,
    returnTypeName: IrTypeName?,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    override val targetMixinRef: String,
    override val isStaticTarget: Boolean,
    val isConstructorCall: Boolean,
    ordinal: Int?,
    isStatic: Boolean,
) : IrInjection(name, methodMixinRef, returnTypeName, parameters, hookArguments, isStatic, ordinal), IrTargetInjection

class IrModifyExpressionValueInjection(
    name: String,
    methodMixinRef: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val constantTypeName: IrTypeName,
    val atArgs: List<Pair<String, String>>,
    ordinal: Int?,
    isStatic: Boolean,
) : IrInjection(name, methodMixinRef, constantTypeName, parameters, hookArguments, isStatic, ordinal)

class IrFieldGetInjection(
    name: String,
    methodMixinRef: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    override val targetMixinRef: String,
    override val isStaticTarget: Boolean,
    ordinal: Int?,
    fieldTypeName: IrTypeName,
    isStatic: Boolean,
) : IrInjection(name, methodMixinRef, fieldTypeName, parameters, hookArguments, isStatic, ordinal), IrTargetInjection

class IrFieldSetInjection(
    name: String,
    methodMixinRef: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    override val targetMixinRef: String,
    override val isStaticTarget: Boolean,
    ordinal: Int?,
    isStatic: Boolean,
) : IrInjection(name, methodMixinRef, null, parameters, hookArguments, isStatic, ordinal), IrTargetInjection

class IrArrayInjection(
    name: String,
    methodMixinRef: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    override val targetMixinRef: String,
    override val isStaticTarget: Boolean,
    ordinal: Int?,
    componentTypeName: IrTypeName,
    isStatic: Boolean,
    val op: Op,
) : IrInjection(
    name, methodMixinRef,
    returnTypeName = if (op == Op.Set) null else componentTypeName,
    parameters, hookArguments, isStatic, ordinal
), IrTargetInjection

class IrInstanceofInjection(
    name: String,
    methodMixinRef: String,
    val className: IrClassName,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    ordinal: Int?,
    isStatic: Boolean,
) : IrInjection(
    name, methodMixinRef,
    returnTypeName = KPBoolean.asIrTypeName(),
    parameters, hookArguments, isStatic, ordinal
)
