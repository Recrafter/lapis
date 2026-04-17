package io.github.recrafter.lapis.phases.lowering

import com.squareup.kotlinpoet.asClassName
import io.github.recrafter.lapis.LapisLogger
import io.github.recrafter.lapis.Options
import io.github.recrafter.lapis.annotations.ConstructorHeadPhase
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.phases.builtins.Builtins
import io.github.recrafter.lapis.phases.builtins.DescriptorBuiltin
import io.github.recrafter.lapis.phases.lowering.models.*
import io.github.recrafter.lapis.phases.lowering.types.*
import io.github.recrafter.lapis.phases.validator.*
import org.spongepowered.asm.mixin.injection.Constant
import kotlin.reflect.KClass

class MixinLowering(
    private val options: Options,
    private val builtins: Builtins,
    @Suppress("unused") private val logger: LapisLogger,
) {
    private val mixins: MutableList<IrMixin> = mutableListOf()

    fun lower(validatorResult: ValidatorResult): IrResult {
        mixins += validatorResult.patches.map { lowerMixin(it) }
        return IrResult(
            schemas = validatorResult.schemas.map { schema ->
                IrSchema(
                    containingFile = schema.containingFile,

                    makePublic = schema.makePublic,
                    removeFinal = schema.removeFinal,
                    className = schema.className,
                    targetClassName = schema.targetClassName,
                    descriptors = schema.descriptors.map { lowerDescriptor(it) },
                )
            },
            mixins = mixins,
        )
    }

    private fun lowerDescriptor(descriptor: Descriptor): IrDescriptor =
        when (descriptor) {
            is InvokableDescriptor -> {
                if (descriptor is ConstructorDescriptor) {
                    IrConstructorDescriptor(
                        makePublic = descriptor.makePublic,
                        callWrapper = findOriginDescriptorWrapper(descriptor.className),
                        cancelWrapper = findCancelDescriptorWrapper(descriptor.className),
                        parameters = descriptor.parameters.map { it.asIr() },
                        returnTypeName = descriptor.className,
                    )
                } else {
                    IrMethodDescriptor(
                        makePublic = descriptor.makePublic,
                        removeFinal = descriptor.removeFinal,
                        name = descriptor.name,
                        targetName = descriptor.targetName,
                        bodyWrapper = findOriginDescriptorWrapper(descriptor.className),
                        callWrapper = findOriginDescriptorWrapper(descriptor.className),
                        cancelWrapper = findCancelDescriptorWrapper(descriptor.className),
                        parameters = descriptor.parameters.map { it.asIr() },
                        returnTypeName = descriptor.returnTypeName,
                    )
                }
            }

            is FieldDescriptor -> {
                IrFieldDescriptor(
                    makePublic = descriptor.makePublic,
                    removeFinal = descriptor.removeFinal,
                    name = descriptor.name,
                    targetName = descriptor.targetName,
                    fieldGetWrapper = findOriginDescriptorWrapper(descriptor.className),
                    fieldSetWrapper = findOriginDescriptorWrapper(descriptor.className),
                    arrayGetWrapper = findOriginDescriptorWrapper(descriptor.className),
                    arraySetWrapper = findOriginDescriptorWrapper(descriptor.className),
                    typeName = descriptor.fieldTypeName,
                )
            }
        }

    private fun lowerMixin(patch: Patch): IrMixin =
        IrMixin(
            containingFile = patch.containingFile,

            className = IrClassName.of(
                options.mixinPackageName,
                "Mixin".withQualifiedNamePrefix(patch.className)
            ),
            patchClassName = patch.className,
            patchImplClassName = IrClassName.of(
                options.generatedPackageName,
                "Impl".withQualifiedNamePrefix(patch.className)
            ),
            targetClassName = patch.targetClassName,

            side = patch.side,
            extension = lowerExtension(patch, patch.className),
            injections = patch.hooks.flatMap { lowerInjections(it) },
        )

    private fun lowerExtension(patch: Patch, patchClassName: IrClassName): IrExtension? {
        if (patch.sharedProperties.isEmpty() && patch.sharedFunctions.isEmpty()) {
            return null
        }
        return IrExtension(
            className = IrClassName.of(
                options.generatedPackageName,
                "Extension".withQualifiedNamePrefix(patchClassName)
            ),
            kinds = buildList {
                patch.sharedProperties.forEach { property ->
                    add(
                        IrPropertyGetterExtension(
                            name = property.name,
                            typeName = property.typeName,
                        )
                    )
                    if (property.isMutable) {
                        add(
                            IrPropertySetterExtension(
                                name = property.name,
                                typeName = property.typeName,
                            )
                        )
                    }
                }
                addAll(patch.sharedFunctions.map { function ->
                    IrFunctionCallExtension(
                        name = function.name,
                        parameters = function.parameters.asIr(),
                        returnTypeName = function.returnTypeName,
                    )
                })
            },
        )
    }

    private fun lowerInjections(hook: DomainHook): List<IrInjection> {
        val parameters = buildList {
            when {
                hook.isInjectBased -> {
                    addAll(hook.descriptor.parameters.mapIndexed { index, parameter ->
                        IrInjectionArgumentParameter(parameter.name, index, parameter.typeName)
                    })
                    add(
                        IrInjectionCallbackParameter(
                            if (hook is ConstructorHeadHook) null
                            else hook.descriptor.returnTypeName
                        )
                    )
                }

                hook is HookWithTarget -> {
                    if (hook !is BodyHook && !hook.targetDescriptor.isStatic) {
                        add(IrInjectionReceiverParameter(hook.targetDescriptor.receiverTypeName))
                    }
                    if (hook is FieldSetHook) {
                        add(IrInjectionArgumentParameter("value", 0, hook.typeName))
                    }
                    addAll(hook.targetDescriptor.parameters.mapIndexed { index, parameter ->
                        IrInjectionArgumentParameter(parameter.name, index, parameter.typeName)
                    })
                    add(
                        IrInjectionOperationParameter(
                            if (hook is FieldSetHook) IrTypeName.VOID
                            else hook.targetDescriptor.returnTypeName
                        )
                    )
                }

                hook is LiteralHook -> {
                    add(IrInjectionValueParameter(hook.typeName))
                }

                hook is ArrayHook -> {
                    add(IrInjectionArgumentParameter("array", 0, hook.typeName))
                    add(IrInjectionArgumentParameter("index", 1, KPInt.asIrTypeName()))
                    if (hook.op == Op.Set) {
                        add(IrInjectionArgumentParameter("value", 2, hook.componentTypeName))
                    }
                }

                hook is ReturnHook && !hook.isInjectBased -> {
                    val returnTypeName = hook.returnTypeName ?: lapisError("Return type not found")
                    add(IrInjectionValueParameter(returnTypeName))
                }

                hook is LocalHook -> {
                    add(IrInjectionValueParameter(hook.typeName))
                }

                hook is InstanceofHook -> {
                    add(IrInjectionValueParameter(Object::class.asIr()))
                    add(IrInjectionOperationParameter(KPBoolean.asIr()))
                }
            }
            if (!hook.isInjectBased && hook.parameters.any { it is HookCancelParameter }) {
                add(IrInjectionCallbackParameter(hook.descriptor.returnTypeName))
            }
            addAll(
                hook.parameters.mapNotNull { lowerInjectionLocalBasedParameter(hook, it) }.sortedWith(
                    compareBy<IrInjectionLocalBasedParameter> { parameter ->
                        when (parameter) {
                            is IrInjectionParamParameter -> 0
                            is IrInjectionLocalParameter -> if (parameter.local is IrNamedLocal) 1 else 2
                        }
                    }.thenBy { parameter ->
                        (parameter as? IrInjectionParamParameter)?.localIndex
                    }.thenBy { parameter ->
                        ((parameter as? IrInjectionLocalParameter)?.local as? IrPositionalLocal)?.ordinal
                    }
                )
            )
        }
        val hookArguments = hook.parameters.map { lowerHookArgument(it) }
        return hook.ordinals.ifEmpty { listOf(null) }.map { ordinal ->
            when (hook) {
                is MethodHeadHook -> IrMethodHeadInjection(
                    name = hook.name,
                    methodMixinRef = hook.descriptor.getMixinRef(),
                    parameters = parameters,
                    hookArguments = hookArguments,
                    isStatic = hook.descriptor.isStatic,
                )

                is ConstructorHeadHook -> IrConstructorHeadInjection(
                    name = hook.name,
                    methodMixinRef = hook.descriptor.getMixinRef(),
                    parameters = parameters,
                    hookArguments = hookArguments,
                    atArgs = listOf(
                        "enforce" to when (hook.phase) {
                            ConstructorHeadPhase.PreBody -> "PRE_BODY"
                            ConstructorHeadPhase.PostDelegate -> "POST_DELEGATE"
                            ConstructorHeadPhase.PostInit -> "POST_INIT"
                        }
                    ),
                    isStatic = hook.descriptor.isStatic,
                )

                is BodyHook -> IrWrapMethodInjection(
                    name = hook.name,
                    methodMixinRef = hook.targetDescriptor.getMixinRef(),
                    isStaticTarget = hook.targetDescriptor.isStatic,
                    returnTypeName = hook.returnTypeName,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    isStatic = hook.descriptor.isStatic,
                )

                is TailHook -> IrReturnInjection(
                    name = hook.name,
                    methodMixinRef = hook.descriptor.getMixinRef(),
                    parameters = parameters,
                    hookArguments = hookArguments,
                    ordinal = null,
                    isTail = true,
                    isStatic = hook.descriptor.isStatic,
                )

                is LocalHook -> IrModifyVariableInjection(
                    name = hook.name,
                    methodMixinRef = hook.descriptor.getMixinRef(),
                    returnTypeName = hook.returnTypeName,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    local = lowerLocal(hook.local, hook.descriptor, hook.typeName),
                    isSet = hook.isSet,
                    ordinal = ordinal,
                    isStatic = hook.descriptor.isStatic,
                )

                is InstanceofHook -> IrInstanceofInjection(
                    name = hook.name,
                    methodMixinRef = hook.descriptor.getMixinRef(),
                    className = hook.className,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    ordinal = ordinal,
                    isStatic = hook.descriptor.isStatic,
                )

                is ReturnHook -> {
                    if (hook.isInjectBased) {
                        IrReturnInjection(
                            name = hook.name,
                            methodMixinRef = hook.descriptor.getMixinRef(),
                            parameters = parameters,
                            hookArguments = hookArguments,
                            ordinal = ordinal,
                            isTail = false,
                            isStatic = hook.descriptor.isStatic,
                        )
                    } else {
                        IrModifyReturnValueInjection(
                            name = hook.name,
                            methodMixinRef = hook.descriptor.getMixinRef(),
                            returnTypeName = hook.returnTypeName,
                            parameters = parameters,
                            hookArguments = hookArguments,
                            ordinal = ordinal,
                            isStatic = hook.descriptor.isStatic,
                        )
                    }
                }

                is LiteralHook -> {
                    val args = when (val literal = hook.literal) {
                        is ZeroLiteral -> {
                            val mixinConditions = literal.conditions.map {
                                Constant.Condition.entries[it.ordinal]
                            }
                            buildList {
                                add("intValue" to "0")
                                if (mixinConditions.isNotEmpty()) {
                                    add("expandZeroConditions" to mixinConditions.joinToString(","))
                                }
                            }
                        }

                        is IntLiteral -> listOf("intValue" to literal.value.toString())
                        is FloatLiteral -> listOf("floatValue" to literal.value.toString())
                        is LongLiteral -> listOf("longValue" to literal.value.toString())
                        is DoubleLiteral -> listOf("doubleValue" to literal.value.toString())
                        is StringLiteral -> listOf("stringValue" to literal.value)
                        is ClassLiteral -> listOf("classValue" to literal.className.internalName)
                        NullLiteral -> listOf("nullValue" to "true")
                    }
                    IrModifyExpressionValueInjection(
                        name = hook.name,
                        methodMixinRef = hook.descriptor.getMixinRef(),
                        parameters = parameters,
                        hookArguments = hookArguments,
                        constantTypeName = hook.typeName,
                        atArgs = args,
                        ordinal = ordinal,
                        isStatic = hook.descriptor.isStatic,
                    )
                }

                is FieldGetHook -> IrFieldGetInjection(
                    name = hook.name,
                    methodMixinRef = hook.descriptor.getMixinRef(),
                    parameters = parameters,
                    hookArguments = hookArguments,
                    targetMixinRef = hook.targetDescriptor.getMixinRef(isTarget = true),
                    isStaticTarget = hook.targetDescriptor.isStatic,
                    fieldTypeName = hook.typeName,
                    ordinal = ordinal,
                    isStatic = hook.descriptor.isStatic,
                )

                is FieldSetHook -> IrFieldSetInjection(
                    name = hook.name,
                    methodMixinRef = hook.descriptor.getMixinRef(),
                    parameters = parameters,
                    hookArguments = hookArguments,
                    targetMixinRef = hook.targetDescriptor.getMixinRef(isTarget = true),
                    isStaticTarget = hook.targetDescriptor.isStatic,
                    ordinal = ordinal,
                    isStatic = hook.descriptor.isStatic,
                )

                is ArrayHook -> IrArrayInjection(
                    name = hook.name,
                    methodMixinRef = hook.descriptor.getMixinRef(),
                    parameters = parameters,
                    hookArguments = hookArguments,
                    targetMixinRef = hook.targetDescriptor.getMixinRef(isTarget = true),
                    isStaticTarget = hook.targetDescriptor.isStatic,
                    ordinal = ordinal,
                    componentTypeName = hook.componentTypeName,
                    isStatic = hook.descriptor.isStatic,
                    op = hook.op,
                )

                is CallHook -> IrWrapOperationInjection(
                    name = hook.name,
                    methodMixinRef = hook.descriptor.getMixinRef(),
                    returnTypeName = hook.returnTypeName,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    targetMixinRef = hook.targetDescriptor.getMixinRef(isTarget = true),
                    isStaticTarget = hook.targetDescriptor.isStatic,
                    isConstructorCall = hook.targetDescriptor is ConstructorDescriptor,
                    ordinal = ordinal,
                    isStatic = hook.descriptor.isStatic,
                )
            }
        }
    }

    private fun lowerInjectionLocalBasedParameter(
        hook: DomainHook,
        parameter: HookParameter
    ): IrInjectionLocalBasedParameter? =
        when (parameter) {
            is HookParamParameter -> {
                if (hook.isInjectBased) {
                    return null
                }
                val initialSlot = if (hook.descriptor.isStatic) 0 else 1
                val descParameter = hook.descriptor.parameters[parameter.index]
                val slotOffset = hook.descriptor.parameters.take(parameter.index).sumOf {
                    if (it.typeName.is64bit) 2
                    else 1
                }
                IrInjectionParamParameter(
                    name = descParameter.name,
                    index = parameter.index,
                    typeName = descParameter.typeName,
                    localIndex = initialSlot + slotOffset,
                )
            }

            is HookLocalParameter -> {
                val irLocal = when (val local = parameter.local) {
                    is NamedLocal -> IrNamedLocal(local.name)
                    is PositionalLocal -> {
                        val paramsOffset = buildList {
                            if (!hook.descriptor.isStatic) {
                                add(hook.descriptor.receiverTypeName)
                            }
                            addAll(hook.descriptor.parameters.map { it.typeName })
                        }.count { it == parameter.typeName }
                        IrPositionalLocal(paramsOffset + local.ordinal)
                    }
                }
                IrInjectionLocalParameter(parameter.name, parameter.typeName, irLocal)
            }

            else -> null
        }

    private fun lowerLocal(local: DomainLocal, descriptor: Descriptor, typeName: IrTypeName) =
        when (local) {
            is NamedLocal -> IrNamedLocal(local.name)
            is PositionalLocal -> {
                val paramsOffset = buildList {
                    if (!descriptor.isStatic) {
                        add(descriptor.receiverTypeName)
                    }
                    addAll(descriptor.parameters.map { it.typeName })
                }.count { it == typeName }
                IrPositionalLocal(paramsOffset + local.ordinal)
            }
        }

    private fun lowerHookArgument(parameter: HookParameter): IrHookArgument =
        when (parameter) {
            is HookOriginValueParameter -> IrHookOriginValueArgument

            is HookOriginDescriptorBodyParameter -> {
                val descriptor = parameter.descriptor
                val wrapper = IrDescriptorBodyWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescriptorBuiltin.Body.name.withQualifiedNamePrefix(descriptor.className)
                    ),
                    descriptorClassName = descriptor.className,
                    builtin = builtins[DescriptorBuiltin.Body],
                    parameters = descriptor.parameters.map { it.asIr() },
                    returnTypeName = descriptor.returnTypeName,
                )
                IrHookOriginDescriptorBodyWrapperArgument(wrapper)
            }

            is HookOriginDescriptorFieldGetParameter -> {
                val descriptor = parameter.descriptor
                val wrapper = IrDescriptorFieldGetWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescriptorBuiltin.FieldGet.name.withQualifiedNamePrefix(descriptor.className)
                    ),
                    descriptorClassName = descriptor.className,
                    builtin = builtins[DescriptorBuiltin.FieldGet],
                    receiverTypeName = if (descriptor.isStatic) null else descriptor.receiverTypeName,
                    fieldTypeName = descriptor.fieldTypeName,
                )
                IrHookOriginDescriptorFieldGetWrapperArgument(wrapper)
            }

            is HookOriginDescriptorFieldSetParameter -> {
                val descriptor = parameter.descriptor
                val wrapper = IrDescriptorFieldSetWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescriptorBuiltin.FieldSet.name.withQualifiedNamePrefix(descriptor.className)
                    ),
                    descriptorClassName = descriptor.className,
                    builtin = builtins[DescriptorBuiltin.FieldSet],
                    receiverTypeName = if (descriptor.isStatic) null else descriptor.receiverTypeName,
                    fieldTypeName = descriptor.fieldTypeName,
                )
                IrHookOriginDescriptorFieldSetWrapperArgument(wrapper)
            }

            is HookOriginDescriptorArrayGetParameter -> {
                val descriptor = parameter.descriptor
                val wrapper = IrDescriptorArrayGetWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescriptorBuiltin.ArrayGet.name.withQualifiedNamePrefix(descriptor.className)
                    ),
                    descriptorClassName = descriptor.className,
                    builtin = builtins[DescriptorBuiltin.ArrayGet],
                    arrayTypeName = descriptor.fieldTypeName,
                    arrayComponentTypeName = parameter.arrayComponentTypeName,
                )
                IrHookOriginDescriptorArrayGetWrapperArgument(wrapper)
            }

            is HookOriginDescriptorArraySetParameter -> {
                val descriptor = parameter.descriptor
                val wrapper = IrDescriptorArraySetWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescriptorBuiltin.ArraySet.name.withQualifiedNamePrefix(descriptor.className)
                    ),
                    descriptorClassName = descriptor.className,
                    builtin = builtins[DescriptorBuiltin.ArraySet],
                    arrayTypeName = descriptor.fieldTypeName,
                    arrayComponentTypeName = parameter.arrayComponentTypeName,
                )
                IrHookOriginDescriptorArraySetWrapperArgument(wrapper)
            }

            is HookOriginDescriptorCallParameter -> {
                val descriptor = parameter.descriptor
                val wrapper = IrDescriptorCallWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescriptorBuiltin.Call.name.withQualifiedNamePrefix(descriptor.className)
                    ),
                    descriptorClassName = descriptor.className,
                    builtin = builtins[DescriptorBuiltin.Call],
                    receiverTypeName = if (descriptor.isStatic) null else descriptor.receiverTypeName,
                    parameters = descriptor.parameters.map { it.asIr() },
                    returnTypeName = descriptor.returnTypeName,
                )
                IrHookOriginDescriptorCallWrapperArgument(wrapper)
            }

            is HookCancelParameter -> {
                val descriptor = parameter.descriptor
                val wrapper = IrDescriptorCancelWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescriptorBuiltin.Cancel.name.withQualifiedNamePrefix(descriptor.className)
                    ),
                    descriptorClassName = descriptor.className,
                    builtin = builtins[DescriptorBuiltin.Cancel],
                    parameters = descriptor.parameters.map { it.asIr() },
                    returnTypeName = if (descriptor is MethodDescriptor) descriptor.returnTypeName else null
                )
                IrHookCancelArgument(wrapper)
            }

            is HookOriginInstanceofParameter -> IrHookOriginInstanceofArgument

            is HookOrdinalParameter -> IrHookOrdinalArgument
            is HookParamParameter -> IrHookParamArgument(parameter.name)
            is HookLocalParameter -> IrHookLocalArgument(parameter.name)
        }

    private inline fun <reified W : IrDescriptorWrapper> findOriginDescriptorWrapper(
        descriptorClassName: IrClassName
    ): W? =
        mixins.asSequence()
            .flatMap { it.injections }
            .flatMap { it.hookArguments }
            .filterIsInstance<IrHookOriginDescriptorWrapperArgument<*>>()
            .map { it.wrapper }
            .filterIsInstance<W>()
            .find { it.descriptorClassName == descriptorClassName }

    private fun findCancelDescriptorWrapper(descriptorClassName: IrClassName): IrDescriptorCancelWrapper? =
        mixins.asSequence()
            .flatMap { it.injections }
            .flatMap { it.hookArguments }
            .filterIsInstance<IrHookCancelArgument>()
            .map { it.wrapper }
            .find { it.descriptorClassName == descriptorClassName }
}

fun List<FunctionParameter>.asIr(): List<IrParameter> =
    map { parameter -> IrParameter(parameter.name, parameter.typeName) }

fun FunctionTypeParameter.asIr(): IrFunctionTypeParameter =
    IrFunctionTypeParameter(name, typeName)

fun KClass<*>.asIr(): IrClassName =
    asClassName().asIr()

fun KPTypeName.asIrTypeName(): IrTypeName =
    IrTypeName(this)

fun KPClassName.asIr(): IrClassName =
    IrClassName(this)

fun KPParameterizedTypeName.asIr(): IrParameterizedTypeName =
    IrParameterizedTypeName(this)

fun KPWildcardTypeName.asIr(): IrWildcardTypeName =
    IrWildcardTypeName(this)

fun KPTypeVariableName.asIr(): IrTypeVariableName =
    IrTypeVariableName(this)

fun KPLambdaTypeName.asIr(): IrLambdaTypeName =
    IrLambdaTypeName(this)

fun KPDynamic.asIr(): IrDynamic =
    IrDynamic(this)

private fun String.withQualifiedNamePrefix(className: IrClassName): String =
    className.qualifiedName.replace('.', '_') + "_$this"
