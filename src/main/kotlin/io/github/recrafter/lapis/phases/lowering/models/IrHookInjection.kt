package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.extensions.kp.KPBoolean
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.models.common.IrMixinAnnotation
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed interface IrInjection : IrReturnable {
    val jvmName: String
    val isStatic: Boolean
    override val returnTypeName: IrTypeName?
}

class IrNativeInjection(
    override val jvmName: String,
    val hookExtensionReceiverTypeName: IrTypeName?,
    val mixinAnnotations: List<IrMixinAnnotation>,
    override val isStatic: Boolean,
    val parameters: List<IrNativeInjectionParameter>,
    override val returnTypeName: IrTypeName?
) : IrInjection

class IrNativeInjectionParameter(
    val name: String,
    val typeName: IrTypeName,
    val mixinAnnotations: List<IrMixinAnnotation>,
)

sealed class IrHookInjection(
    override val jvmName: String,
    val methodMixinReference: String,
    override val returnTypeName: IrTypeName?,
    val parameters: List<IrInjectionParameter>,
    val hookArguments: List<IrHookArgument>,
    override val isStatic: Boolean,
    val ordinal: Int?,
) : IrInjection

sealed interface IrTargetInjection {
    val targetMixinReference: String
    val isStaticTarget: Boolean
}

sealed interface IrInjectHookInjection

class IrMethodHeadHookInjection(
    jvmName: String,
    methodMixinReference: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    isStatic: Boolean,
) : IrHookInjection(
    jvmName,
    methodMixinReference,
    null,
    parameters,
    hookArguments,
    isStatic,
    null,
), IrInjectHookInjection

class IrConstructorHeadHookInjection(
    jvmName: String,
    methodMixinReference: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val atArgs: List<Pair<String, String>>,
    isStatic: Boolean,
) : IrHookInjection(
    jvmName,
    methodMixinReference,
    null,
    parameters,
    hookArguments,
    isStatic,
    null,
), IrInjectHookInjection

class IrWrapMethodHookInjection(
    jvmName: String,
    methodMixinReference: String,
    override val isStaticTarget: Boolean,
    returnTypeName: IrTypeName?,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    isStatic: Boolean,
) : IrHookInjection(
    jvmName,
    methodMixinReference,
    returnTypeName,
    parameters,
    hookArguments,
    isStatic,
    null,
), IrTargetInjection {
    override val targetMixinReference: String = methodMixinReference
}

class IrReturnHookInjection(
    jvmName: String,
    methodMixinReference: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    ordinal: Int?,
    val isTail: Boolean,
    isStatic: Boolean,
) : IrHookInjection(
    jvmName,
    methodMixinReference,
    null,
    parameters,
    hookArguments,
    isStatic,
    ordinal,
), IrInjectHookInjection

class IrModifyVariableHookInjection(
    jvmName: String,
    methodMixinReference: String,
    returnTypeName: IrTypeName?,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val local: IrLocal,
    val op: Op,
    ordinal: Int?,
    isStatic: Boolean,
) : IrHookInjection(
    jvmName,
    methodMixinReference,
    returnTypeName,
    parameters,
    hookArguments,
    isStatic,
    ordinal,
)

class IrModifyReturnValueHookInjection(
    jvmName: String,
    methodMixinReference: String,
    returnTypeName: IrTypeName?,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    ordinal: Int?,
    isStatic: Boolean,
) : IrHookInjection(
    jvmName,
    methodMixinReference,
    returnTypeName,
    parameters,
    hookArguments,
    isStatic,
    ordinal,
)

class IrWrapOperationHookInjection(
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
) : IrHookInjection(
    jvmName,
    methodMixinReference,
    returnTypeName,
    parameters,
    hookArguments,
    isStatic,
    ordinal,
), IrTargetInjection

class IrModifyExpressionValueHookInjection(
    jvmName: String,
    methodMixinReference: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val constantTypeName: IrTypeName,
    val atArgs: List<Pair<String, String>>,
    ordinal: Int?,
    isStatic: Boolean,
) : IrHookInjection(
    jvmName,
    methodMixinReference,
    constantTypeName,
    parameters,
    hookArguments,
    isStatic,
    ordinal,
)

class IrFieldGetHookInjection(
    jvmName: String,
    methodMixinReference: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    override val targetMixinReference: String,
    override val isStaticTarget: Boolean,
    ordinal: Int?,
    fieldTypeName: IrTypeName,
    isStatic: Boolean,
) : IrHookInjection(
    jvmName,
    methodMixinReference,
    fieldTypeName,
    parameters,
    hookArguments,
    isStatic,
    ordinal,
), IrTargetInjection

class IrFieldSetHookInjection(
    jvmName: String,
    methodMixinReference: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    override val targetMixinReference: String,
    override val isStaticTarget: Boolean,
    ordinal: Int?,
    isStatic: Boolean,
) : IrHookInjection(
    jvmName,
    methodMixinReference,
    null,
    parameters,
    hookArguments,
    isStatic,
    ordinal,
), IrTargetInjection

class IrArrayHookInjection(
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
    val atArgs: List<Pair<String, String>>,
) : IrHookInjection(
    jvmName,
    methodMixinReference,
    returnTypeName = if (op == Op.Set) null else componentTypeName,
    parameters,
    hookArguments,
    isStatic,
    ordinal,
), IrTargetInjection

class IrInstanceofHookInjection(
    jvmName: String,
    methodMixinReference: String,
    val className: IrClassName,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    ordinal: Int?,
    isStatic: Boolean,
) : IrHookInjection(
    jvmName,
    methodMixinReference,
    returnTypeName = KPBoolean.asIrTypeName(),
    parameters,
    hookArguments,
    isStatic,
    ordinal,
)
