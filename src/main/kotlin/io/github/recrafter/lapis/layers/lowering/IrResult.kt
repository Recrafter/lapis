package io.github.recrafter.lapis.layers.lowering

import io.github.recrafter.lapis.annotations.enums.LapisPatchSide
import io.github.recrafter.lapis.extensions.ksp.KspSymbol
import io.github.recrafter.lapis.layers.validator.KspSourceHolder

class IrResult(
    val descriptors: List<IrDescriptor>,
    val mixins: List<IrMixin>,
)

class IrDescriptor(
    override val source: KspSymbol,

    val contextImpl: IrDescriptorContextImpl,
    val targetImpl: IrDescriptorTargetImpl,
) : KspSourceHolder()

class IrDescriptorContextImpl(
    val type: IrClassName,
    val superType: IrParameterizedTypeName,
    val parameters: List<IrParameter>,
    val returnType: IrTypeName?,
)

class IrDescriptorTargetImpl(
    val type: IrClassName,
    val superType: IrClassName,
    val receiverType: IrTypeName?,
    val parameters: List<IrParameter>,
    val returnType: IrTypeName?,
)

class IrMixin(
    override val source: KspSymbol,

    val type: IrClassName,
    val side: LapisPatchSide,

    val patchType: IrClassName,
    val patchImplType: IrClassName,
    val targetType: IrClassName,

    val extension: IrExtension?,
    val accessor: IrAccessor?,
    val injections: List<IrInjection>,

    val innerMixins: List<IrMixin>,
) : KspSourceHolder() {

    fun flattenTree(): List<IrMixin> =
        listOf(this) + innerMixins.flatMap { it.flattenTree() }
}

class IrExtension(
    val type: IrClassName,
    val kinds: List<IrExtensionKind>,
)

sealed class IrExtensionKind(
    val name: String,
    val internalName: String,
    val parameters: List<IrParameter>,
    val returnType: IrTypeName?,
)

class IrFieldGetterExtension(
    name: String,
    internalName: String,
    val type: IrTypeName,
) : IrExtensionKind(name, internalName, emptyList(), type)

class IrFieldSetterExtension(
    name: String,
    internalName: String,
    val type: IrTypeName,
) : IrExtensionKind(name, internalName, listOf(IrParameter("newValue", type)), null)

class IrMethodExtension(
    name: String,
    internalName: String,
    parameters: List<IrParameter>,
    returnType: IrTypeName?,
) : IrExtensionKind(name, internalName, parameters, returnType)

class IrAccessor(
    val type: IrClassName,
    val kinds: List<IrAccessorKind>,
)

sealed class IrAccessorKind(
    override val source: KspSymbol,

    val name: String,
    val internalName: String,
    val vanillaName: String,
    val parameters: List<IrParameter>,
    val returnType: IrTypeName?,
    val isStatic: Boolean,
) : KspSourceHolder()

class IrFieldGetterAccessor(
    override val source: KspSymbol,

    val type: IrTypeName,
    name: String,
    internalName: String,
    vanillaName: String,
    isStatic: Boolean,
) : IrAccessorKind(source, name, internalName, vanillaName, emptyList(), type, isStatic)

class IrFieldSetterAccessor(
    override val source: KspSymbol,

    val type: IrTypeName,
    name: String,
    internalName: String,
    vanillaName: String,
    isStatic: Boolean,
) : IrAccessorKind(source, name, internalName, vanillaName, listOf(IrParameter("newValue", type)), null, isStatic)

open class IrMethodAccessor(
    override val source: KspSymbol,

    name: String,
    internalName: String,
    vanillaName: String,
    parameters: List<IrParameter>,
    returnType: IrTypeName?,
    isStatic: Boolean,
) : IrAccessorKind(source, name, internalName, vanillaName, parameters, returnType, isStatic)

class IrConstructorAccessor(
    override val source: KspSymbol,

    name: String,
    internalName: String,
    parameters: List<IrParameter>,
    val classType: IrTypeName,
) : IrMethodAccessor(source, name, internalName, "<init>", parameters, classType, true)

sealed class IrInjection(
    override val source: KspSymbol,

    val name: String,
    val hookName: String,
    val method: String,
    val returnType: IrTypeName?,
    val parameters: List<IrInjectionParameter>,
    val hookArguments: List<IrHookArgument>,
) : KspSourceHolder()

class IrWrapMethodInjection(
    override val source: KspSymbol,

    name: String,
    hookName: String,
    method: String,
    val isStatic: Boolean,
    returnType: IrTypeName?,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
) : IrInjection(source, name, hookName, method, returnType, parameters, hookArguments)

class IrWrapOperationInjection(
    override val source: KspSymbol,

    name: String,
    hookName: String,
    method: String,
    returnType: IrTypeName?,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val target: String,
    val isStatic: Boolean,
    val ordinal: Int?,
) : IrInjection(source, name, hookName, method, returnType, parameters, hookArguments)

class IrModifyConstantValueInjection(
    override val source: KspSymbol,

    name: String,
    hookName: String,
    method: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val literalType: IrTypeName,
    val literalTypeName: String,
    val literalValue: String,
    val ordinal: Int?,
) : IrInjection(source, name, hookName, method, literalType, parameters, hookArguments)

sealed interface IrInjectionParameter {
    val priority: Int
    val subPriority: Int get() = 0
}

class IrInjectionReceiverParameter(val type: IrTypeName) : IrInjectionParameter {
    override val priority: Int = 0
}

class IrInjectionArgumentParameter(val name: String, val type: IrTypeName) : IrInjectionParameter {
    override val priority: Int = 1
}

class IrInjectionOperationParameter(val returnType: IrTypeName?) : IrInjectionParameter {
    override val priority: Int = 2
}

class IrInjectionLiteralParameter(val type: IrTypeName) : IrInjectionParameter {
    override val priority: Int = 3
}

class IrInjectionCallbackParameter(val returnType: IrTypeName?) : IrInjectionParameter {
    override val priority: Int = 4
}

class IrInjectionSignatureLocalParameter(
    val name: String,
    val type: IrTypeName,
    val index: Int
) : IrInjectionParameter {
    override val priority: Int = 5
    override val subPriority: Int = index
}

class IrInjectionBodyLocalParameter(
    val name: String,
    val type: IrTypeName,
    val ordinal: Int
) : IrInjectionParameter {
    override val priority: Int = 6
    override val subPriority: Int = ordinal
}

sealed interface IrHookArgument
class IrHookContextArgument(val descriptor: IrDescriptorContextImpl) : IrHookArgument
class IrHookTargetArgument(val descriptor: IrDescriptorTargetImpl) : IrHookArgument
object IrHookLiteralArgument : IrHookArgument
class IrHookOrdinalArgument(val ordinal: Int) : IrHookArgument
class IrHookLocalArgument(val parameterName: String) : IrHookArgument
