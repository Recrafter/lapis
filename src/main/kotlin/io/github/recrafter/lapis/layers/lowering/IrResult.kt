package io.github.recrafter.lapis.layers.lowering

import io.github.recrafter.lapis.annotations.Side
import io.github.recrafter.lapis.extensions.capitalize
import io.github.recrafter.lapis.extensions.common.defaultValue
import io.github.recrafter.lapis.extensions.ksp.KSPFile
import io.github.recrafter.lapis.layers.generator.withInternalPrefix
import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import io.github.recrafter.lapis.layers.lowering.types.IrGenericTypeName
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName
import org.spongepowered.asm.mixin.injection.At

class IrResult(
    val schemas: List<IrSchema>,
    val mixins: List<IrMixin>,
)

class IrSchema(
    val containingFile: KSPFile?,

    val makePublic: Boolean,
    val removeFinal: Boolean,
    val className: IrClassName,
    val targetClassName: IrClassName,
    val descriptors: List<IrDesc>,
)

sealed class IrDesc(
    val makePublic: Boolean,
    val removeFinal: Boolean,
)

sealed class IrInvokableDesc(
    makePublic: Boolean,
    removeFinal: Boolean,
    val callWrapper: IrDescCallWrapper,
    val cancelWrapper: IrDescCancelWrapper,
    val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDesc(makePublic, removeFinal)

class IrConstructorDesc(
    makePublic: Boolean,
    callWrapper: IrDescCallWrapper,
    cancelWrapper: IrDescCancelWrapper,
    parameters: List<IrFunctionTypeParameter>,
    returnTypeName: IrTypeName,
) : IrInvokableDesc(makePublic, false, callWrapper, cancelWrapper, parameters, returnTypeName)

class IrMethodDesc(
    makePublic: Boolean,
    removeFinal: Boolean,
    val name: String,
    val targetName: String,
    callWrapper: IrDescCallWrapper,
    cancelWrapper: IrDescCancelWrapper,
    parameters: List<IrFunctionTypeParameter>,
    returnTypeName: IrTypeName?,
) : IrInvokableDesc(makePublic, removeFinal, callWrapper, cancelWrapper, parameters, returnTypeName)

class IrFieldDesc(
    makePublic: Boolean,
    removeFinal: Boolean,
    val name: String,
    val targetName: String,
    val fieldGetWrapper: IrDescFieldGetWrapper,
    val fieldWriteWrapper: IrDescFieldWriteWrapper,
    val typeName: IrTypeName,
) : IrDesc(makePublic, removeFinal)

sealed class IrDescWrapper(
    val className: IrClassName,
    val superClassTypeName: IrGenericTypeName,
    val receiverTypeName: IrTypeName?,
)

class IrDescCallWrapper(
    className: IrClassName,
    superClassTypeName: IrGenericTypeName,
    receiverTypeName: IrTypeName?,
    val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescWrapper(className, superClassTypeName, receiverTypeName)

class IrDescCancelWrapper(
    className: IrClassName,
    superClassTypeName: IrGenericTypeName,
    val parameters: List<IrFunctionTypeParameter>,
    val returnTypeName: IrTypeName?,
) : IrDescWrapper(className, superClassTypeName, null)

class IrDescFieldGetWrapper(
    className: IrClassName,
    superClassTypeName: IrGenericTypeName,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescWrapper(className, superClassTypeName, receiverTypeName)

class IrDescFieldWriteWrapper(
    className: IrClassName,
    superClassTypeName: IrGenericTypeName,
    receiverTypeName: IrTypeName?,
    val fieldTypeName: IrTypeName,
) : IrDescWrapper(className, superClassTypeName, receiverTypeName)

class IrMixin(
    val containingFile: KSPFile?,

    val className: IrClassName,
    val patchClassName: IrClassName,
    val patchImplClassName: IrClassName,
    val targetClassName: IrClassName,

    val side: Side,
    val extension: IrExtension?,
    val injections: List<IrInjection>,
) {
    fun isNotEmpty(): Boolean =
        extension != null || injections.isNotEmpty()
}

class IrExtension(
    val className: IrClassName,
    val kinds: List<IrExtensionKind>,
)

sealed class IrExtensionKind(
    val name: String,
    val parameters: List<IrParameter>,
    val returnTypeName: IrTypeName?,
) {
    abstract fun getInternalName(modId: String): String
}

class IrFieldGetterExtension(
    name: String,
    val typeName: IrTypeName,
) : IrExtensionKind(name, emptyList(), typeName) {

    override fun getInternalName(modId: String): String =
        ("get" + name.capitalize()).withInternalPrefix(modId)
}

class IrFieldSetterExtension(
    name: String,
    val typeName: IrTypeName,
) : IrExtensionKind(name, listOf(IrSetterParameter(typeName)), null) {

    override fun getInternalName(modId: String): String =
        ("set" + name.capitalize()).withInternalPrefix(modId)
}

class IrMethodExtension(
    name: String,
    parameters: List<IrParameter>,
    returnTypeName: IrTypeName?,
) : IrExtensionKind(name, parameters, returnTypeName) {

    override fun getInternalName(modId: String): String =
        name.withInternalPrefix(modId)
}

sealed class IrInjection(
    val name: String,
    val methodMixinRef: String,
    val returnTypeName: IrTypeName?,
    val parameters: List<IrInjectionParameter>,
    val hookArguments: List<IrHookArgument>,
    val ordinal: Int,
)

class IrWrapMethodInjection(
    name: String,
    methodMixinRef: String,
    val isStatic: Boolean,
    returnTypeName: IrTypeName?,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
) : IrInjection(name, methodMixinRef, returnTypeName, parameters, hookArguments, At::ordinal.defaultValue)

class IrWrapOperationInjection(
    name: String,
    methodMixinRef: String,
    returnTypeName: IrTypeName?,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val targetMixinRef: String,
    val isStatic: Boolean,
    ordinal: Int,
) : IrInjection(name, methodMixinRef, returnTypeName, parameters, hookArguments, ordinal)

class IrModifyExpressionValueInjection(
    name: String,
    methodMixinRef: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val constantTypeName: IrTypeName,
    val args: List<Pair<String, String>>,
    ordinal: Int,
) : IrInjection(name, methodMixinRef, constantTypeName, parameters, hookArguments, ordinal)

class IrFieldGetInjection(
    name: String,
    methodMixinRef: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val targetMixinRef: String,
    val isStatic: Boolean,
    ordinal: Int,
    fieldTypeName: IrTypeName,
) : IrInjection(name, methodMixinRef, fieldTypeName, parameters, hookArguments, ordinal)

class IrFieldWriteInjection(
    name: String,
    methodMixinRef: String,
    parameters: List<IrInjectionParameter>,
    hookArguments: List<IrHookArgument>,
    val targetMixinRef: String,
    val isStatic: Boolean,
    ordinal: Int,
) : IrInjection(name, methodMixinRef, null, parameters, hookArguments, ordinal)

sealed interface IrInjectionParameter {
    val priority: Int
    val subPriority: Int get() = 0
}

class IrInjectionReceiverParameter(
    val typeName: IrTypeName,
    override val priority: Int = 0
) : IrInjectionParameter

class IrInjectionArgumentParameter(
    val name: String?,
    val index: Int,
    val typeName: IrTypeName,
    override val priority: Int = 2
) : IrInjectionParameter

class IrInjectionOperationParameter(
    val returnTypeName: IrTypeName?,
    override val priority: Int = 3
) : IrInjectionParameter

class IrInjectionValueParameter(
    val typeName: IrTypeName,
    override val priority: Int = 4
) : IrInjectionParameter

class IrInjectionCallbackParameter(
    val returnTypeName: IrTypeName?,
    override val priority: Int = 5
) : IrInjectionParameter

class IrInjectionParamParameter(
    val name: String?,
    val index: Int,
    val typeName: IrTypeName,
    val localIndex: Int,
    override val priority: Int = 6,
    override val subPriority: Int = localIndex
) : IrInjectionParameter

class IrInjectionLocalParameter(
    val name: String,
    val typeName: IrTypeName,
    val ordinal: Int,
    override val priority: Int = 7,
    override val subPriority: Int = ordinal
) : IrInjectionParameter

sealed interface IrHookArgument

sealed interface IrHookOriginArgument : IrHookArgument
class IrHookOriginValueArgument : IrHookOriginArgument

sealed class IrHookOriginDescWrapperArgument(open val wrapper: IrDescWrapper) : IrHookOriginArgument
class IrHookOriginDescCallWrapperArgument(override val wrapper: IrDescCallWrapper) :
    IrHookOriginDescWrapperArgument(wrapper)

class IrHookCancelArgument(val wrapper: IrDescCancelWrapper) : IrHookArgument
class IrHookParamArgument(val name: String) : IrHookArgument
class IrHookLocalArgument(val name: String, val ordinal: Int) : IrHookArgument

open class IrParameter(val name: String, val typeName: IrTypeName)
class IrSetterParameter(typeName: IrTypeName) : IrParameter("newValue", typeName)

open class IrFunctionTypeParameter(val name: String?, val typeName: IrTypeName)
