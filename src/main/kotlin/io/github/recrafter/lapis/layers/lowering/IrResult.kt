package io.github.recrafter.lapis.layers.lowering

import io.github.recrafter.lapis.Side
import io.github.recrafter.lapis.extensions.capitalize
import io.github.recrafter.lapis.extensions.common.defaultValue
import io.github.recrafter.lapis.extensions.ksp.KSPFile
import io.github.recrafter.lapis.layers.generator.withInternalPrefix
import io.github.recrafter.lapis.layers.lowering.types.IrClassType
import io.github.recrafter.lapis.layers.lowering.types.IrGenericType
import io.github.recrafter.lapis.layers.lowering.types.IrType
import org.spongepowered.asm.mixin.injection.At

class IrResult(
    val schemas: List<IrSchema>,
    val mixins: List<IrMixin>,
)

class IrSchema(
    val containingFile: KSPFile?,

    val needAccess: Boolean,
    val needRemoveFinal: Boolean,
    val classType: IrClassType,
    val targetClassType: IrClassType,
    val descriptors: List<IrDescriptor>,
)

sealed class IrDescriptor(
    val needAccess: Boolean,
    val needRemoveFinal: Boolean,
)

sealed class IrInvokableDescriptor(
    needAccess: Boolean,
    needRemoveFinal: Boolean,
    val callable: IrDescriptorCallable?,
    val context: IrDescriptorContext?,
    val parameters: List<IrFunctionTypeParameter>,
    val returnType: IrType?,
) : IrDescriptor(needAccess, needRemoveFinal)

class IrConstructorDescriptor(
    needAccess: Boolean,
    callable: IrDescriptorCallable?,
    context: IrDescriptorContext?,
    parameters: List<IrFunctionTypeParameter>,
    val classType: IrType,
) : IrInvokableDescriptor(needAccess, false, callable, context, parameters, classType)

class IrMethodDescriptor(
    needAccess: Boolean,
    needRemoveFinal: Boolean,
    val name: String,
    val targetName: String,
    callable: IrDescriptorCallable?,
    context: IrDescriptorContext?,
    parameters: List<IrFunctionTypeParameter>,
    returnType: IrType?,
) : IrInvokableDescriptor(needAccess, needRemoveFinal, callable, context, parameters, returnType)

class IrFieldDescriptor(
    needAccess: Boolean,
    needRemoveFinal: Boolean,
    val name: String,
    val targetName: String,
    val getter: IrDescriptorGetter?,
    val setter: IrDescriptorSetter?,
    val type: IrType,
) : IrDescriptor(needAccess, needRemoveFinal)

sealed class IrDescriptorWrapper(
    val classType: IrClassType,
    val superClassType: IrGenericType,
    val receiverType: IrType?,
)

class IrDescriptorCallable(
    classType: IrClassType,
    superClassType: IrGenericType,
    receiverType: IrType?,
) : IrDescriptorWrapper(classType, superClassType, receiverType)

class IrDescriptorContext(
    classType: IrClassType,
    superClassType: IrGenericType,
) : IrDescriptorWrapper(classType, superClassType, null)

class IrDescriptorGetter(
    classType: IrClassType,
    superClassType: IrGenericType,
    receiverType: IrType?,
) : IrDescriptorWrapper(classType, superClassType, receiverType)

class IrDescriptorSetter(
    classType: IrClassType,
    superClassType: IrGenericType,
    receiverType: IrType?,
) : IrDescriptorWrapper(classType, superClassType, receiverType)

class IrMixin(
    val containingFile: KSPFile?,

    val classType: IrClassType,
    val patchClassType: IrClassType,
    val patchImplClassType: IrClassType,
    val targetClassType: IrClassType,

    val side: Side,
    val extension: IrExtension?,
    val injections: List<IrInjection>,
) {
    fun isNotEmpty(): Boolean =
        extension != null || injections.isNotEmpty()
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

sealed class IrInjection(
    val name: String,
    val method: String,
    val returnType: IrType?,
    val parameters: List<IrInjectionParameter>,
    val hookArguments: List<IrHookArgument>,
    val ordinal: Int,
)

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
    fieldType: IrType,
) : IrInjection(name, method, fieldType, parameters, hookArguments, ordinal)

class IrFieldSetInjection(
    name: String,
    method: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val target: String,
    val isStatic: Boolean,
    ordinal: Int,
) : IrInjection(name, method, null, parameters, hookArguments, ordinal)

sealed interface IrInjectionParameter {
    val priority: Int
    val subPriority: Int get() = 0
}

class IrInjectionReceiverParameter(
    val type: IrType,
    override val priority: Int = 0
) : IrInjectionParameter

class IrInjectionSetterValueParameter(
    val type: IrType,
    override val priority: Int = 1
) : IrInjectionParameter

class IrInjectionArgumentParameter(
    val name: String?,
    val index: Int,
    val type: IrType,
    override val priority: Int = 2
) : IrInjectionParameter

class IrInjectionOperationParameter(
    val returnType: IrType?,
    override val priority: Int = 3
) : IrInjectionParameter

class IrInjectionLiteralParameter(
    val type: IrType,
    override val priority: Int = 4
) : IrInjectionParameter

class IrInjectionCallbackParameter(
    val returnType: IrType?,
    override val priority: Int = 5
) : IrInjectionParameter

class IrInjectionParameterParameter(
    val name: String?,
    val index: Int,
    val type: IrType,
    val localIndex: Int,
    override val priority: Int = 6,
    override val subPriority: Int = localIndex
) : IrInjectionParameter

class IrInjectionLocalParameter(
    val name: String,
    val type: IrType,
    val ordinal: Int,
    override val priority: Int = 7,
    override val subPriority: Int = ordinal
) : IrInjectionParameter

sealed interface IrHookArgument

sealed class IrHookTargetArgument(open val wrapper: IrDescriptorWrapper) : IrHookArgument
class IrHookCallableTargetArgument(
    val descriptor: IrInvokableDescriptor,
    override val wrapper: IrDescriptorCallable
) : IrHookTargetArgument(wrapper)

class IrHookGetterTargetArgument(override val wrapper: IrDescriptorGetter) : IrHookTargetArgument(wrapper)
class IrHookSetterTargetArgument(override val wrapper: IrDescriptorSetter) : IrHookTargetArgument(wrapper)

class IrHookContextArgument(val descriptor: IrInvokableDescriptor, val wrapper: IrDescriptorContext) : IrHookArgument
object IrHookLiteralArgument : IrHookArgument
class IrHookOrdinalArgument(val ordinal: Int) : IrHookArgument
class IrHookLocalArgument(val parameterName: String) : IrHookArgument

open class IrParameter(val name: String, val type: IrType)
class IrSetterParameter(type: IrType) : IrParameter("newValue", type)

open class IrFunctionTypeParameter(val name: String?, val type: IrType)
