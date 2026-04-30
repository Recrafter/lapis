package io.github.recrafter.lapis.phases.lowering

import com.squareup.kotlinpoet.asTypeName
import io.github.recrafter.lapis.LapisLogger
import io.github.recrafter.lapis.Options
import io.github.recrafter.lapis.annotations.ConstructorHeadPhase
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ks.isInterface
import io.github.recrafter.lapis.phases.builtins.Builtins
import io.github.recrafter.lapis.phases.builtins.DescriptorWrapperBuiltin
import io.github.recrafter.lapis.phases.builtins.LocalVarImplBuiltin
import io.github.recrafter.lapis.phases.common.getMixinRef
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
    private val patches: MutableList<IrPatch> = mutableListOf()

    fun lower(validatorResult: ValidatorResult): IrResult {
        patches += validatorResult.patches.map(::lowerPatch)
        return IrResult(
            schemas = validatorResult.schemas.map { schema ->
                IrSchema(
                    originatingFile = schema.containingFile,

                    makePublic = schema.makePublic,
                    removeFinal = schema.removeFinal,
                    className = schema.className,
                    ownerJvmClassName = schema.originJvmClassName,
                    descriptors = schema.descriptors.map(::lowerDescriptor),
                )
            },
            patches = patches,
        )
    }

    private fun lowerDescriptor(descriptor: Descriptor): IrDescriptor =
        when (descriptor) {
            is InvokableDescriptor -> {
                if (descriptor is ConstructorDescriptor) {
                    IrConstructorDescriptor(
                        makePublic = descriptor.makePublic,
                        callWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                        parameters = descriptor.parameters.map { it.asIrFunctionTypeParameter() },
                        returnTypeName = descriptor.className,
                    )
                } else {
                    IrMethodDescriptor(
                        makePublic = descriptor.makePublic,
                        removeFinal = descriptor.removeFinal,
                        name = descriptor.name,
                        mappingName = descriptor.mappingName,
                        bodyWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                        callWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                        cancelWrapperImpl = findCancelDescriptorWrapperImpl(descriptor.className),
                        parameters = descriptor.parameters.map { it.asIrFunctionTypeParameter() },
                        returnTypeName = descriptor.returnTypeName,
                    )
                }
            }

            is FieldDescriptor -> {
                IrFieldDescriptor(
                    makePublic = descriptor.makePublic,
                    removeFinal = descriptor.removeFinal,
                    name = descriptor.name,
                    mappingName = descriptor.mappingName,
                    fieldGetWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                    fieldSetWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                    arrayGetWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                    arraySetWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                    typeName = descriptor.fieldTypeName,
                )
            }
        }

    private fun lowerPatch(patch: Patch): IrPatch {
        val constructorArguments = patch.constructorParameters.map(::lowerPatchConstructorArgument)
        return IrPatch(
            originatingFile = patch.containingFile,

            side = patch.side,
            isObject = patch.isObject,
            className = patch.className,
            constructorArguments = constructorArguments,
            impl = lowerPatchImpl(patch, constructorArguments),
            mixin = lowerMixin(patch),
            extension = lowerExtension(patch),
        )
    }

    private fun lowerPatchConstructorArgument(parameter: PatchConstructorParameter): IrPatchConstructorArgument =
        when (parameter) {
            is PatchConstructorOriginParameter -> IrPatchConstructorOriginArgument
        }

    private fun lowerPatchImpl(patch: Patch, constructorArguments: List<IrPatchConstructorArgument>): IrPatchImpl? =
        if (patch.hasStaticHooksOnly) null
        else IrPatchImpl(
            className = IrClassName.of(
                options.generatedPackageName,
                "Impl".withQualifiedNamePrefix(patch.className)
            ),
            constructorParameters = buildList {
                if (constructorArguments.any { it is IrPatchConstructorOriginArgument }) {
                    add(IrPatchImplConstructorInstanceParameter)
                }
            },
            initStrategy = patch.initStrategy,
        )

    private fun lowerMixin(patch: Patch): IrMixin =
        IrMixin(
            className = IrClassName.of(
                options.mixinPackageName,
                "Mixin".withQualifiedNamePrefix(patch.className)
            ),
            instanceTypeName = patch.schema.originTypeName,
            isInterfaceInstance = patch.schema.originClassDeclaration.isInterface,
            targetInternalName = patch.schema.originJvmClassName.internalName,
            injections = patch.hooks.flatMap(::lowerInjections),
        )

    private fun lowerExtension(patch: Patch): IrExtension? {
        if (patch.sharedProperties.isEmpty() && patch.sharedFunctions.isEmpty()) {
            return null
        }
        return IrExtension(
            className = IrClassName.of(
                options.generatedPackageName,
                "Extension".withQualifiedNamePrefix(patch.className)
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
                        parameters = function.parameters.map { it.asIrParameter() },
                        returnTypeName = function.returnTypeName,
                    )
                })
            },
        )
    }

    private fun lowerInjections(hook: PatchHook): List<IrInjection> {
        val parameters = buildList {
            when {
                hook.isInjectBased -> {
                    addAll(hook.descriptor.parameters.mapIndexed { index, parameter ->
                        IrInjectionArgumentParameter(parameter.name, index, parameter.typeName)
                    })
                    add(
                        IrInjectionCallbackParameter(
                            if (hook.descriptor is ConstructorDescriptor) null
                            else hook.descriptor.returnTypeName
                        )
                    )
                }

                hook is HookWithTarget -> {
                    if (hook !is BodyHook && !hook.targetDescriptor.isStatic) {
                        add(
                            IrInjectionReceiverParameter(
                                hook.targetDescriptor.receiverTypeName,
                                isCoerce = hook.targetDescriptor.inaccessibleReceiverJvmClassName != null,
                            )
                        )
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
                    add(IrInjectionValueParameter(Object::class.asIrTypeName()))
                    add(IrInjectionOperationParameter(KPBoolean.asIrClassName()))
                }
            }
            if (!hook.isInjectBased && hook.parameters.any { it is HookCancelParameter }) {
                add(IrInjectionCallbackParameter(hook.descriptor.returnTypeName))
            }
            addAll(
                hook.parameters.mapNotNull { lowerInjectionLocalParameter(hook, it) }.sortedWith(
                    compareBy<IrInjectionLocalParameter> { parameter ->
                        when (parameter) {
                            is IrInjectionParamLocalParameter -> 0
                            is IrInjectionBodyLocalParameter -> if (parameter.local is IrNamedLocal) 1 else 2
                            is IrInjectionShareParameter -> 3
                        }
                    }.thenBy { parameter ->
                        (parameter as? IrInjectionParamLocalParameter)?.localIndex
                    }.thenBy { parameter ->
                        ((parameter as? IrInjectionBodyLocalParameter)?.local as? IrPositionalLocal)?.ordinal
                    }.thenBy { parameter ->
                        (parameter as? IrInjectionShareParameter)?.key
                    }
                )
            )
        }
        val hookArguments = hook.parameters.map(::lowerHookArgument)
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
                    op = hook.op,
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
                            val expandZeroConditions = literal.conditions.map {
                                Constant.Condition.entries[it.ordinal]
                            }
                            buildList {
                                add("intValue" to "0")
                                if (expandZeroConditions.isNotEmpty()) {
                                    add("expandZeroConditions" to expandZeroConditions.joinToString(","))
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

    private fun lowerInjectionLocalParameter(hook: PatchHook, parameter: HookParameter): IrInjectionLocalParameter? =
        when (parameter) {
            is HookParamLocalParameter -> {
                if (hook.isInjectBased) {
                    return null
                }
                val initialSlot = if (hook.descriptor.isStatic) 0 else 1
                val descriptorParameter = hook.descriptor.parameters[parameter.index]
                val slotOffset = hook.descriptor.parameters.take(parameter.index).sumOf {
                    if (it.typeName.is64bit) 2
                    else 1
                }
                IrInjectionParamLocalParameter(
                    name = descriptorParameter.name ?: parameter.index.toString(),
                    typeName = descriptorParameter.typeName,
                    varImplBuiltin = lowerHookLocalVarBuiltin(parameter),
                    localIndex = initialSlot + slotOffset,
                )
            }

            is HookBodyLocalParameter -> IrInjectionBodyLocalParameter(
                name = parameter.name,
                typeName = parameter.typeName,
                varImplBuiltin = lowerHookLocalVarBuiltin(parameter),
                local = when (val local = parameter.local) {
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
            )

            is HookShareLocalParameter -> IrInjectionShareParameter(
                name = parameter.name,
                typeName = parameter.typeName,
                varImplBuiltin = LocalVarImplBuiltin.of(parameter.typeName),
                key = parameter.key,
                isExported = parameter.isExported,
            )

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

            is HookOriginDescriptorBodyWrapperParameter -> {
                val descriptor = parameter.descriptor
                val impl = IrDescriptorBodyWrapperImpl(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescriptorWrapperBuiltin.Body.name.withQualifiedNamePrefix(descriptor.className)
                    ),
                    descriptorClassName = descriptor.className,
                    wrapperBuiltinClassName = builtins[DescriptorWrapperBuiltin.Body],
                    parameters = descriptor.parameters.map { it.asIrFunctionTypeParameter() },
                    returnTypeName = descriptor.returnTypeName,
                )
                IrHookOriginDescriptorBodyWrapperImplArgument(impl)
            }

            is HookOriginDescriptorFieldGetWrapperParameter -> {
                val descriptor = parameter.descriptor
                val impl = IrDescriptorFieldGetWrapperImpl(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescriptorWrapperBuiltin.FieldGet.name.withQualifiedNamePrefix(descriptor.className)
                    ),
                    descriptorClassName = descriptor.className,
                    wrapperBuiltinClassName = builtins[DescriptorWrapperBuiltin.FieldGet],
                    receiverTypeName = if (descriptor.isStatic) null else descriptor.receiverTypeName,
                    fieldTypeName = descriptor.fieldTypeName,
                )
                IrHookOriginDescriptorFieldGetWrapperImplArgument(impl)
            }

            is HookOriginDescriptorFieldSetWrapperParameter -> {
                val descriptor = parameter.descriptor
                val impl = IrDescriptorFieldSetWrapperImpl(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescriptorWrapperBuiltin.FieldSet.name.withQualifiedNamePrefix(descriptor.className)
                    ),
                    descriptorClassName = descriptor.className,
                    wrapperBuiltinClassName = builtins[DescriptorWrapperBuiltin.FieldSet],
                    receiverTypeName = if (descriptor.isStatic) null else descriptor.receiverTypeName,
                    fieldTypeName = descriptor.fieldTypeName,
                )
                IrHookOriginDescriptorFieldSetWrapperImplArgument(impl)
            }

            is HookOriginDescriptorArrayGetWrapperParameter -> {
                val descriptor = parameter.descriptor
                val impl = IrDescriptorArrayGetWrapperImpl(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescriptorWrapperBuiltin.ArrayGet.name.withQualifiedNamePrefix(descriptor.className)
                    ),
                    descriptorClassName = descriptor.className,
                    wrapperBuiltinClassName = builtins[DescriptorWrapperBuiltin.ArrayGet],
                    arrayTypeName = descriptor.fieldTypeName,
                    arrayComponentTypeName = parameter.arrayComponentTypeName,
                )
                IrHookOriginDescriptorArrayGetWrapperImplArgument(impl)
            }

            is HookOriginDescriptorArraySetWrapperParameter -> {
                val descriptor = parameter.descriptor
                val impl = IrDescriptorArraySetWrapperImpl(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescriptorWrapperBuiltin.ArraySet.name.withQualifiedNamePrefix(descriptor.className)
                    ),
                    descriptorClassName = descriptor.className,
                    wrapperBuiltinClassName = builtins[DescriptorWrapperBuiltin.ArraySet],
                    arrayTypeName = descriptor.fieldTypeName,
                    arrayComponentTypeName = parameter.arrayComponentTypeName,
                )
                IrHookOriginDescriptorArraySetWrapperImplArgument(impl)
            }

            is HookOriginDescriptorCallWrapperParameter -> {
                val descriptor = parameter.descriptor
                val impl = IrDescriptorCallWrapperImpl(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescriptorWrapperBuiltin.Call.name.withQualifiedNamePrefix(descriptor.className)
                    ),
                    descriptorClassName = descriptor.className,
                    wrapperBuiltinClassName = builtins[DescriptorWrapperBuiltin.Call],
                    receiverTypeName = if (descriptor.isStatic) null else descriptor.receiverTypeName,
                    parameters = descriptor.parameters.map { it.asIrFunctionTypeParameter() },
                    returnTypeName = descriptor.returnTypeName,
                )
                IrHookOriginDescriptorCallWrapperImplArgument(impl)
            }

            is HookCancelParameter -> {
                val descriptor = parameter.descriptor
                val impl = IrDescriptorCancelWrapperImpl(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescriptorWrapperBuiltin.Cancel.name.withQualifiedNamePrefix(descriptor.className)
                    ),
                    descriptorClassName = descriptor.className,
                    wrapperBuiltinClassName = builtins[DescriptorWrapperBuiltin.Cancel],
                    parameters = descriptor.parameters.map { it.asIrFunctionTypeParameter() },
                    returnTypeName = if (descriptor is MethodDescriptor) descriptor.returnTypeName else null
                )
                IrHookCancelArgument(impl)
            }

            is HookOriginInstanceofParameter -> IrHookOriginInstanceofArgument

            is HookOrdinalParameter -> IrHookOrdinalArgument
            is HookLocalParameter -> IrHookLocalArgument(
                name = parameter.name,
                isBody = parameter is HookBodyLocalParameter,
                isShare = parameter is HookShareLocalParameter,
                varBuiltin = lowerHookLocalVarBuiltin(parameter),
            )
        }

    private fun lowerHookLocalVarBuiltin(parameter: HookLocalParameter): LocalVarImplBuiltin? =
        if (parameter.isVar) LocalVarImplBuiltin.of(parameter.typeName)
        else null

    private inline fun <reified T : IrDescriptorWrapperImpl> findOriginDescriptorWrapperImpl(
        descriptorClassName: IrClassName
    ): T? =
        patches.asSequence()
            .flatMap { it.mixin.injections }
            .flatMap { it.hookArguments }
            .filterIsInstance<IrHookOriginDescriptorWrapperImplArgument<*>>()
            .map { it.wrapperImpl }
            .filterIsInstance<T>()
            .find { it.descriptorClassName == descriptorClassName }

    private fun findCancelDescriptorWrapperImpl(descriptorClassName: IrClassName): IrDescriptorCancelWrapperImpl? =
        patches.asSequence()
            .flatMap { it.mixin.injections }
            .flatMap { it.hookArguments }
            .filterIsInstance<IrHookCancelArgument>()
            .map { it.wrapperImpl }
            .find { it.descriptorClassName == descriptorClassName }
}

fun FunctionParameter.asIrParameter(): IrParameter =
    IrParameter(name, typeName)

fun FunctionTypeParameter.asIrFunctionTypeParameter(): IrFunctionTypeParameter =
    IrFunctionTypeParameter(name, typeName)

fun KClass<*>.asIrTypeName(): IrTypeName =
    asTypeName().asIrTypeName()

fun KClass<*>.asIrParameterizedTypeName(
    vararg argumentTypeNames: IrTypeName = arrayOf(KPStar.asIrWildcardTypeName())
): IrParameterizedTypeName =
    asIrTypeName().parameterizedBy(*argumentTypeNames)

fun KPTypeName.asIrTypeName(): IrTypeName =
    IrTypeName(this)

fun KPClassName.asIrClassName(): IrClassName =
    IrClassName(this)

fun KPParameterizedTypeName.asIrParameterizedTypeName(): IrParameterizedTypeName =
    IrParameterizedTypeName(this)

fun KPWildcardTypeName.asIrWildcardTypeName(): IrWildcardTypeName =
    IrWildcardTypeName(this)

fun KPTypeVariableName.asIrTypeVariableName(): IrTypeVariableName =
    IrTypeVariableName(this)

fun KPLambdaTypeName.asIrLambdaTypeName(): IrLambdaTypeName =
    IrLambdaTypeName(this)

fun KPDynamic.asIrDynamic(): IrDynamic =
    IrDynamic(this)

private fun String.withQualifiedNamePrefix(className: IrClassName): String =
    className.qualifiedName.replace('.', '_') + "_$this"
