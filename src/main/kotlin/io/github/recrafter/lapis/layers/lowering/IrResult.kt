package io.github.recrafter.lapis.layers.lowering

import io.github.recrafter.lapis.annotations.enums.LapisPatchSide
import io.github.recrafter.lapis.extensions.capitalize
import io.github.recrafter.lapis.extensions.common.defaultValue
import io.github.recrafter.lapis.extensions.ksp.KSPFile
import io.github.recrafter.lapis.layers.generator.withInternalPrefix
import io.github.recrafter.lapis.layers.lowering.types.IrClassType
import io.github.recrafter.lapis.layers.lowering.types.IrGenericType
import io.github.recrafter.lapis.layers.lowering.types.IrType
import org.spongepowered.asm.mixin.injection.At

class IrResult(
    val descriptors: List<IrDescriptor>,
    val mixins: List<IrMixin>,
)

open class IrParameter(val name: String, val type: IrType)
class IrSetterParameter(type: IrType) : IrParameter("newValue", type)

sealed class IrDescriptor(
    val containingFile: KSPFile?,
    val classType: IrClassType,
)

class IrInvokableDescriptor(
    containingFile: KSPFile?,
    classType: IrClassType,
    val callable: IrDescriptorCallable?,
    val context: IrDescriptorContext?,
) : IrDescriptor(containingFile, classType)

class IrFieldDescriptor(
    containingFile: KSPFile?,
    classType: IrClassType,
    val getter: IrDescriptorGetter?,
    val setter: IrDescriptorSetter?,
) : IrDescriptor(containingFile, classType)

sealed class IrDescriptorWrapper(
    val classType: IrClassType,
    val superClassType: IrGenericType,
    val receiverType: IrType?,
    val parameters: List<IrParameter>,
    val returnType: IrType?,
)

class IrDescriptorCallable(
    classType: IrClassType,
    superClassType: IrGenericType,
    receiverType: IrType?,
    parameters: List<IrParameter>,
    returnType: IrType?,
) : IrDescriptorWrapper(classType, superClassType, receiverType, parameters, returnType)

class IrDescriptorContext(
    classType: IrClassType,
    superClassType: IrGenericType,
    parameters: List<IrParameter>,
    returnType: IrType?,
) : IrDescriptorWrapper(classType, superClassType, null, parameters, returnType)

class IrDescriptorGetter(
    classType: IrClassType,
    superClassType: IrGenericType,
    receiverType: IrType?,
    val type: IrType,
) : IrDescriptorWrapper(classType, superClassType, receiverType, emptyList(), type)

class IrDescriptorSetter(
    classType: IrClassType,
    superClassType: IrGenericType,
    receiverType: IrType?,
    val type: IrType,
) : IrDescriptorWrapper(classType, superClassType, receiverType, emptyList(), type)

class IrMixin(
    val containingFile: KSPFile?,

    val classType: IrClassType,
    val patchClassType: IrClassType,
    val patchImplClassType: IrClassType,
    val targetClassType: IrClassType,

    val side: LapisPatchSide,
    val extension: IrExtension?,
    val accessor: IrAccessor?,
    val injections: List<IrInjection>,

    val innerMixins: List<IrMixin>,
) {
    fun isNotEmpty(): Boolean =
        extension != null || injections.isNotEmpty()

    fun flattenTree(): List<IrMixin> =
        listOf(this) + innerMixins.flatMap { it.flattenTree() }
}

class IrExtension(
    val classType: IrClassType,
    val kinds: List<IrExtensionKind>,
)

sealed class IrExtensionKind(
    val name: String,
    val parameters: List<IrParameter>,
    val returnType: IrType?,
) {
    abstract fun getInternalName(modId: String): String
}

class IrFieldGetterExtension(
    name: String,
    val type: IrType,
) : IrExtensionKind(name, emptyList(), type) {

    override fun getInternalName(modId: String): String =
        ("get" + name.capitalize()).withInternalPrefix(modId)
}

class IrFieldSetterExtension(
    name: String,
    val type: IrType,
) : IrExtensionKind(name, listOf(IrSetterParameter(type)), null) {

    override fun getInternalName(modId: String): String =
        ("set" + name.capitalize()).withInternalPrefix(modId)
}

class IrMethodExtension(
    name: String,
    parameters: List<IrParameter>,
    returnType: IrType?,
) : IrExtensionKind(name, parameters, returnType) {

    override fun getInternalName(modId: String): String =
        name.withInternalPrefix(modId)
}

class IrAccessor(
    val classType: IrClassType,
    val kinds: List<IrAccessorKind>,
)

sealed class IrAccessorKind(
    val name: String,
    val targetName: String,
    val parameters: List<IrParameter>,
    val returnType: IrType?,
    val isStatic: Boolean,
) {
    abstract val internalName: String
}

class IrFieldGetterAccessor(
    val type: IrType,
    name: String,
    targetName: String,
    isStatic: Boolean,
) : IrAccessorKind(name, targetName, emptyList(), type, isStatic) {
    override val internalName = "get" + name.capitalize()
}

class IrFieldSetterAccessor(
    val type: IrType,
    name: String,
    targetName: String,
    isStatic: Boolean,
) : IrAccessorKind(name, targetName, listOf(IrSetterParameter(type)), null, isStatic) {
    override val internalName = "set" + name.capitalize()
}

open class IrMethodAccessor(
    name: String,
    targetName: String,
    parameters: List<IrParameter>,
    returnType: IrType?,
    isStatic: Boolean,
) : IrAccessorKind(name, targetName, parameters, returnType, isStatic) {
    override val internalName: String = "invoke" + name.capitalize()
}

class IrConstructorAccessor(
    name: String,
    parameters: List<IrParameter>,
    val classType: IrType,
) : IrMethodAccessor(name, "", parameters, classType, true) {
    override val internalName: String = "invoke" + name.capitalize()
}

sealed class IrInjection(
    val name: String,
    val method: String,
    val returnType: IrType?,
    val parameters: List<IrInjectionParameter>,
    val hookArguments: List<IrHookArgument>,
    val ordinal: Int,
) {
    val internalName: String = buildString {
        append(name)
        if (ordinal != At::ordinal.defaultValue) {
            append("_ordinal$ordinal")
        }
    }
}

class IrWrapMethodInjection(
    name: String,
    method: String,
    val isStatic: Boolean,
    returnType: IrType?,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
) : IrInjection(name, method, returnType, parameters, hookArguments, At::ordinal.defaultValue)

class IrWrapOperationInjection(
    name: String,
    method: String,
    returnType: IrType?,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val target: String,
    val isStatic: Boolean,
    ordinal: Int,
) : IrInjection(name, method, returnType, parameters, hookArguments, ordinal)

class IrModifyConstantValueInjection(
    name: String,
    method: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val constantType: IrType,
    val constantTypeName: String,
    val constantValue: String,
    ordinal: Int,
) : IrInjection(name, method, constantType, parameters, hookArguments, ordinal)

class IrFieldGetInjection(
    name: String,
    method: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val target: String,
    val isStatic: Boolean,
    ordinal: Int,
    val fieldType: IrType,
) : IrInjection(name, method, fieldType, parameters, hookArguments, ordinal)

sealed interface IrInjectionParameter {
    val priority: Int
    val subPriority: Int get() = 0
}

class IrInjectionReceiverParameter(
    val type: IrType,
    override val priority: Int = 0
) : IrInjectionParameter

class IrInjectionArgumentParameter(
    val name: String,
    val type: IrType,
    override val priority: Int = 1
) : IrInjectionParameter

class IrInjectionOperationParameter(
    val returnType: IrType?,
    override val priority: Int = 2
) : IrInjectionParameter

class IrInjectionLiteralParameter(
    val type: IrType,
    override val priority: Int = 3
) : IrInjectionParameter

class IrInjectionCallbackParameter(
    val returnType: IrType?,
    override val priority: Int = 4
) : IrInjectionParameter

class IrInjectionParameterParameter(
    val name: String,
    val type: IrType,
    val index: Int,
    override val priority: Int = 5,
    override val subPriority: Int = index
) : IrInjectionParameter

class IrInjectionLocalParameter(
    val name: String,
    val type: IrType,
    val ordinal: Int,
    override val priority: Int = 6,
    override val subPriority: Int = ordinal
) : IrInjectionParameter

sealed interface IrHookArgument

sealed class IrHookTargetArgument(open val descriptor: IrDescriptorWrapper) : IrHookArgument
class IrHookCallableTargetArgument(override val descriptor: IrDescriptorCallable) : IrHookTargetArgument(descriptor)
class IrHookGetterTargetArgument(override val descriptor: IrDescriptorGetter) : IrHookTargetArgument(descriptor)
class IrHookSetterTargetArgument(override val descriptor: IrDescriptorSetter) : IrHookTargetArgument(descriptor)

class IrHookContextArgument(val descriptor: IrDescriptorContext) : IrHookArgument
object IrHookLiteralArgument : IrHookArgument
class IrHookOrdinalArgument(val ordinal: Int) : IrHookArgument
class IrHookLocalArgument(val parameterName: String) : IrHookArgument
