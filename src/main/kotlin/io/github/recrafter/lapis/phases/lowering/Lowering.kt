package io.github.recrafter.lapis.phases.lowering

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.recrafter.lapis.annotations.ConstructorHeadPhase
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.common.binaryName
import io.github.recrafter.lapis.common.getMixinReference
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.jp.JPModifier
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ks.isInterface
import io.github.recrafter.lapis.extensions.withInternalPrefix
import io.github.recrafter.lapis.logging.Logger
import io.github.recrafter.lapis.phases.bootstrap.Options
import io.github.recrafter.lapis.phases.builtins.LocalVarImplBuiltin
import io.github.recrafter.lapis.phases.lowering.models.*
import io.github.recrafter.lapis.phases.lowering.types.*
import io.github.recrafter.lapis.phases.validator.models.ValidatorResult
import io.github.recrafter.lapis.phases.validator.models.common.SourceFile
import io.github.recrafter.lapis.phases.validator.models.patches.*
import io.github.recrafter.lapis.phases.validator.models.patches.hooks.*
import io.github.recrafter.lapis.phases.validator.models.schemas.*
import org.spongepowered.asm.mixin.injection.Constant
import kotlin.reflect.KClass

class Lowering(
    private val options: Options,
    @Suppress("unused") private val logger: Logger,
) {
    private val patches: MutableList<IrPatch> = mutableListOf()

    fun lower(result: ValidatorResult): IrResult {
        val mixinSourcePackageLCP = findMixinSourcePackageLCP(result.schemas + result.patches)
        patches += result.patches.map { lowerPatch(it, mixinSourcePackageLCP) }
        return IrResult(
            schemas = result.schemas.map { schema ->
                IrSchema(
                    className = schema.className,
                    descriptors = schema.descriptors.map(::lowerDescriptor),
                    tweakAccessor = lowerTweakAccessor(schema),
                    mixinAccessor = lowerMixinAccessor(schema, mixinSourcePackageLCP),
                )
            },
            patches = patches,
        )
    }

    private fun lowerDescriptor(descriptor: Descriptor): IrDescriptor =
        when (descriptor) {
            is FieldDescriptor -> IrFieldDescriptor(
                name = descriptor.name,
                fieldGetWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                fieldSetWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                arrayGetWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                arraySetWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                typeName = descriptor.fieldTypeName,
            )

            is MethodDescriptor -> IrMethodDescriptor(
                name = descriptor.name,
                bodyWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                callWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                cancelWrapperImpl = findCancelDescriptorWrapperImpl(descriptor.className),
                parameters = descriptor.functionTypeParameters.map { it.asIrFunctionTypeParameter() },
                returnTypeName = descriptor.returnTypeName,
            )

            is ConstructorDescriptor -> IrConstructorDescriptor(
                callWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                parameters = descriptor.functionTypeParameters.map { it.asIrFunctionTypeParameter() },
                returnTypeName = descriptor.className,
            )
        }

    private fun lowerTweakAccessor(schema: Schema): IrTweakAccessor? {
        val descriptors = schema.descriptors.mapNotNull {
            if (it.accessRequest !is TweakAccessRequest) {
                return@mapNotNull null
            }
            it to it.accessRequest
        }
        if (schema.accessRequest == null && descriptors.isEmpty()) {
            return null
        }
        val entries = mutableListOf<IrTweakAccessorEntry>()
        if (schema.accessRequest is TweakAccessRequest) {
            entries += IrTweakAccessorClassEntry(
                removeFinal = schema.accessRequest.shouldRemoveFinal,
            )
        }
        descriptors.forEach { (descriptor, accessRequest) ->
            entries += when (descriptor) {
                is InvokableDescriptor -> {
                    val isConstructor = descriptor is ConstructorDescriptor
                    IrTweakAccessorMethodEntry(
                        name = descriptor.binaryName,
                        parameterTypes = descriptor.functionTypeParameters.map { it.typeName },
                        returnTypeName = if (isConstructor) null else descriptor.returnTypeName,
                        removeFinal = accessRequest.shouldRemoveFinal,
                    )
                }

                is FieldDescriptor -> {
                    IrTweakAccessorFieldEntry(
                        name = descriptor.mappingName,
                        typeName = descriptor.fieldTypeName,
                        removeFinal = accessRequest.shouldRemoveFinal,
                    )
                }
            }
        }
        return IrTweakAccessor(
            originatingFiles = listOfNotNull(schema.containingFile),
            ownerJvmClassName = schema.originJvmClassName,
            entries = entries,
        )
    }

    private fun lowerMixinAccessor(schema: Schema, sourcePackageLCP: String): IrMixinAccessor? {
        val descriptors = schema.descriptors.mapNotNull {
            if (it.accessRequest !is MixinAccessRequest) {
                return@mapNotNull null
            }
            it to it.accessRequest
        }
        if (descriptors.isEmpty()) {
            return null
        }
        val members = mutableListOf<IrMixinAccessorMember>()
        descriptors.forEach { (descriptor, accessRequest) ->
            if (descriptor is FieldDescriptor && accessRequest is MixinFieldAccessRequest) {
                members += IrMixinAccessorFieldMember(
                    name = descriptor.name,
                    mappingName = descriptor.mappingName,
                    typeName = descriptor.fieldTypeName,
                    isStatic = descriptor.isStatic,
                    removeFinal = accessRequest.shouldRemoveFinal,
                    ops = accessRequest.ops,
                    descriptorClassName = descriptor.className,
                )
            } else if (descriptor is InvokableDescriptor && accessRequest is MixinInvokableAccessRequest) {
                members += IrMixinAccessorMethodMember(
                    name = descriptor.name,
                    mappingName = descriptor.binaryName,
                    parameters = accessRequest.parameters,
                    returnTypeName = descriptor.returnTypeName,
                    isStatic = descriptor is ConstructorDescriptor || descriptor.isStatic,
                    descriptorClassName = descriptor.className,
                )
            }
        }
        return IrMixinAccessor(
            originatingFiles = listOfNotNull(schema.containingFile),
            className = resolveMixinRelatedClassName(schema.className, sourcePackageLCP, "Accessor"),
            side = schema.side,
            targetInternalName = schema.originJvmClassName.internalName,
            instanceTypeName = schema.originTypeName,
            isAccessibleSchema = schema.isAccessible,
            members = members,
        )
    }

    private fun lowerPatch(patch: Patch, mixinSourcePackageLCP: String): IrPatch {
        val constructorArguments = patch.constructorParameters.map(::lowerPatchConstructorArgument)
        return IrPatch(
            className = patch.className,
            constructorArguments = constructorArguments,
            impl = lowerPatchImpl(patch, constructorArguments),
            mixin = lowerMixin(patch, mixinSourcePackageLCP),
        )
    }

    private fun lowerPatchConstructorArgument(parameter: PatchConstructorParameter): IrPatchConstructorArgument =
        when (parameter) {
            is PatchConstructorOriginParameter -> IrPatchConstructorOriginArgument
        }

    private fun lowerPatchImpl(patch: Patch, constructorArguments: List<IrPatchConstructorArgument>): IrPatchImpl? =
        if (patch.isImplRequired) {
            IrPatchImpl(
                originatingFiles = listOfNotNull(patch.containingFile),
                className = patch.className.derived("Impl"),
                constructorParameters = buildList {
                    if (constructorArguments.any { it is IrPatchConstructorOriginArgument }) {
                        add(IrPatchImplConstructorInstanceParameter)
                    }
                    if (patch.shadowSources.isNotEmpty()) {
                        add(IrPatchImplConstructorInternalBridgeParameter)
                    }
                },
                initStrategy = patch.initStrategy,
            )
        } else null

    private fun lowerMixin(patch: Patch, sourcePackageLCP: String): IrMixin =
        IrMixin(
            originatingFiles = listOfNotNull(patch.containingFile),
            className = resolveMixinRelatedClassName(patch.className, sourcePackageLCP, "Mixin"),
            targetInstanceTypeName = patch.schema.originTypeName,
            isInterfaceTarget = patch.schema.originClassDeclaration.isInterface,
            targetInternalName = patch.schema.originJvmClassName.internalName,
            side = patch.side,
            injections = patch.hooks.flatMap(::lowerInjections),
            externalBridge = lowerMixinExternalBridge(patch),
            internalBridge = lowerMixinInternalBridge(patch),
        )

    private fun lowerMixinExternalBridge(patch: Patch): IrMixinExternalBridge? =
        if (patch.extensionSources.isNotEmpty()) {
            IrMixinExternalBridge(
                originatingFiles = listOfNotNull(patch.containingFile),
                className = patch.className.derived("ExternalBridge"),
                entries = patch.extensionSources.map(::lowerMixinExternalBridgeEntry),
            )
        } else null

    private fun lowerMixinInternalBridge(patch: Patch): IrMixinInternalBridge? =
        if (patch.shadowSources.isNotEmpty()) {
            IrMixinInternalBridge(
                originatingFiles = listOfNotNull(patch.containingFile),
                className = patch.className.derived("InternalBridge"),
                entries = patch.shadowSources.map(::lowerMixinInternalBridgeEntry),
            )
        } else null

    private fun lowerMixinExternalBridgeEntry(source: PatchExtensionSource): IrMixinExternalBridgeEntry =
        when (source) {
            is ExtensionProperty -> with(source) {
                IrMixinExternalBridgePropertyEntry(
                    typeName = typeName,
                    sourceName = name,
                    sourceGetterJvmName = getterJvmName,
                    sourceSetterJvmName = setterJvmName,
                    getterName = getterJvmName.withModIdPrefix(),
                    setterName = source.setterJvmName?.withModIdPrefix(),
                )
            }

            is ExtensionFunction -> with(source) {
                IrMixinExternalBridgeFunctionEntry(
                    sourceName = name,
                    sourceJvmName = jvmName,
                    name = source.jvmName.withModIdPrefix(),
                    parameters = parameters.map { it.asIrParameter() },
                    returnTypeName = returnTypeName,
                )
            }
        }

    private fun lowerMixinInternalBridgeEntry(source: PatchShadowSource): IrMixinInternalBridgeEntry =
        when (source) {
            is ShadowProperty -> with(source) {
                IrMixinInternalBridgeShadowPropertyEntry(
                    typeName = typeName,
                    sourceName = name,
                    sourceGetterJvmName = getterJvmName,
                    sourceSetterJvmName = setterJvmName,
                    getterName = getterJvmName.withModIdPrefix(),
                    setterName = source.setterJvmName?.withModIdPrefix(),
                    mappingName = mappingName,
                    modifiers = modifiers,
                    isFinal = JPModifier.FINAL in modifiers,
                )
            }

            is ShadowFunction -> with(source) {
                IrMixinInternalBridgeShadowFunctionEntry(
                    sourceName = name,
                    sourceJvmName = jvmName,
                    name = source.jvmName.withModIdPrefix(),
                    parameters = parameters.map { it.asIrParameter() },
                    returnTypeName = returnTypeName,
                    mappingName = mappingName,
                    modifiers = modifiers,
                )
            }
        }

    private fun lowerInjections(hook: PatchHook): List<IrInjection> {
        val parameters = buildList {
            when {
                hook.isInjectBased -> {
                    addAll(hook.methodDescriptor.functionTypeParameters.mapIndexed { index, parameter ->
                        val name = parameter.name ?: index.toString()
                        IrInjectionArgumentParameter(name, parameter.typeName)
                    })
                    add(
                        IrInjectionCallbackParameter(
                            if (hook.methodDescriptor is ConstructorDescriptor) null
                            else hook.methodDescriptor.returnTypeName
                        )
                    )
                }

                hook is HookWithTarget -> {
                    if (hook !is BodyHook && !hook.targetDescriptor.isStatic) {
                        add(
                            IrInjectionReceiverParameter(
                                typeName = hook.targetDescriptor.receiverTypeName,
                                isCoerce = hook.targetDescriptor.inaccessibleReceiverJvmClassName != null,
                            )
                        )
                    }
                    if (hook is FieldSetHook) {
                        add(IrInjectionArgumentParameter("value", hook.typeName))
                    }
                    addAll(hook.targetDescriptor.functionTypeParameters.mapIndexed { index, parameter ->
                        val name = parameter.name ?: index.toString()
                        IrInjectionArgumentParameter(name, parameter.typeName)
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
                    add(IrInjectionArgumentParameter("array", hook.typeName))
                    add(IrInjectionArgumentParameter("index", KPInt.asIrTypeName()))
                    if (hook.op == Op.Set) {
                        add(IrInjectionArgumentParameter("value", hook.componentTypeName))
                    }
                }

                hook is ReturnHook && !hook.isInjectBased -> {
                    val returnTypeName = hook.returnTypeName ?: lapisError("Return type cannot be null")
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
            if (!hook.isInjectBased && hook.parameters.any { it is HookCancelDescriptorWrapperParameter }) {
                add(IrInjectionCallbackParameter(hook.methodDescriptor.returnTypeName))
            }
            addAll(hook.parameters.mapNotNull { lowerInjectionLocalParameter(it, hook) })
        }
        val hookArguments = hook.parameters.map(::lowerHookArgument)
        return hook.ordinals.ifEmpty { listOf(null) }.map { ordinal ->
            val methodMixinReference = hook.methodDescriptor.getMixinReference()
            when (hook) {
                is MethodHeadHook -> IrMethodHeadInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = methodMixinReference,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    isStatic = hook.methodDescriptor.isStatic,
                )

                is ConstructorHeadHook -> IrConstructorHeadInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = methodMixinReference,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    atArgs = listOf(
                        "enforce" to when (hook.phase) {
                            ConstructorHeadPhase.PreBody -> "PRE_BODY"
                            ConstructorHeadPhase.PostDelegate -> "POST_DELEGATE"
                            ConstructorHeadPhase.PostInit -> "POST_INIT"
                        }
                    ),
                    isStatic = hook.methodDescriptor.isStatic,
                )

                is BodyHook -> IrWrapMethodInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = methodMixinReference,
                    isStaticTarget = hook.targetDescriptor.isStatic,
                    returnTypeName = hook.returnTypeName,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    isStatic = hook.methodDescriptor.isStatic,
                )

                is TailHook -> IrReturnInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = methodMixinReference,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    ordinal = null,
                    isTail = true,
                    isStatic = hook.methodDescriptor.isStatic,
                )

                is LocalHook -> IrModifyVariableInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = methodMixinReference,
                    returnTypeName = hook.returnTypeName,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    local = lowerLocal(hook.local, hook.methodDescriptor, hook.typeName),
                    op = hook.op,
                    ordinal = ordinal,
                    isStatic = hook.methodDescriptor.isStatic,
                )

                is InstanceofHook -> IrInstanceofInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = methodMixinReference,
                    className = hook.typeClassName,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    ordinal = ordinal,
                    isStatic = hook.methodDescriptor.isStatic,
                )

                is ReturnHook -> {
                    if (hook.isInjectBased) {
                        IrReturnInjection(
                            jvmName = hook.jvmName,
                            methodMixinReference = methodMixinReference,
                            parameters = parameters,
                            hookArguments = hookArguments,
                            ordinal = ordinal,
                            isTail = false,
                            isStatic = hook.methodDescriptor.isStatic,
                        )
                    } else {
                        IrModifyReturnValueInjection(
                            jvmName = hook.jvmName,
                            methodMixinReference = methodMixinReference,
                            returnTypeName = hook.returnTypeName,
                            parameters = parameters,
                            hookArguments = hookArguments,
                            ordinal = ordinal,
                            isStatic = hook.methodDescriptor.isStatic,
                        )
                    }
                }

                is LiteralHook -> {
                    val args = when (val literal = hook.literal) {
                        is ZeroHookLiteral -> {
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

                        is IntHookLiteral -> listOf("intValue" to literal.value.toString())
                        is FloatHookLiteral -> listOf("floatValue" to literal.value.toString())
                        is LongHookLiteral -> listOf("longValue" to literal.value.toString())
                        is DoubleHookLiteral -> listOf("doubleValue" to literal.value.toString())
                        is StringHookLiteral -> listOf("stringValue" to literal.value)
                        is ClassHookLiteral -> listOf("classValue" to literal.typeClassName.internalName)
                        NullHookLiteral -> listOf("nullValue" to "true")
                    }
                    IrModifyExpressionValueInjection(
                        jvmName = hook.jvmName,
                        methodMixinReference = methodMixinReference,
                        parameters = parameters,
                        hookArguments = hookArguments,
                        constantTypeName = hook.typeName,
                        atArgs = args,
                        ordinal = ordinal,
                        isStatic = hook.methodDescriptor.isStatic,
                    )
                }

                is FieldGetHook -> IrFieldGetInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = methodMixinReference,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    targetMixinReference = hook.targetDescriptor.getMixinReference(isTarget = true),
                    isStaticTarget = hook.targetDescriptor.isStatic,
                    fieldTypeName = hook.typeName,
                    ordinal = ordinal,
                    isStatic = hook.methodDescriptor.isStatic,
                )

                is FieldSetHook -> IrFieldSetInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = methodMixinReference,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    targetMixinReference = hook.targetDescriptor.getMixinReference(isTarget = true),
                    isStaticTarget = hook.targetDescriptor.isStatic,
                    ordinal = ordinal,
                    isStatic = hook.methodDescriptor.isStatic,
                )

                is ArrayHook -> IrArrayInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = methodMixinReference,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    targetMixinReference = hook.targetDescriptor.getMixinReference(isTarget = true),
                    isStaticTarget = hook.targetDescriptor.isStatic,
                    ordinal = ordinal,
                    componentTypeName = hook.componentTypeName,
                    isStatic = hook.methodDescriptor.isStatic,
                    op = hook.op,
                    atArgs = listOf("array" to hook.op.name.lowercase()),
                )

                is CallHook -> IrWrapOperationInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = methodMixinReference,
                    returnTypeName = hook.returnTypeName,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    targetMixinReference = hook.targetDescriptor.getMixinReference(isTarget = true),
                    isStaticTarget = hook.targetDescriptor.isStatic,
                    isConstructorCall = hook.targetDescriptor is ConstructorDescriptor,
                    ordinal = ordinal,
                    isStatic = hook.methodDescriptor.isStatic,
                )
            }
        }
    }

    private fun lowerInjectionLocalParameter(parameter: HookParameter, hook: PatchHook): IrInjectionLocalParameter? =
        when (parameter) {
            is HookParamLocalParameter -> {
                if (hook.isInjectBased) {
                    return null
                }
                val initialSlot = if (hook.methodDescriptor.isStatic) 0 else 1
                val descriptorParameter = hook.methodDescriptor.functionTypeParameters[parameter.index]
                val slotOffset = hook.methodDescriptor.functionTypeParameters.take(parameter.index).sumOf {
                    if (it.typeName.is64bit) 2
                    else 1
                }
                IrInjectionParamLocalParameter(
                    name = descriptorParameter.name ?: parameter.index.toString(),
                    typeName = descriptorParameter.typeName,
                    varImplBuiltin = lowerLocalVarBuiltin(parameter),
                    localIndex = initialSlot + slotOffset,
                )
            }

            is HookBodyLocalParameter -> IrInjectionBodyLocalParameter(
                name = parameter.name,
                typeName = parameter.typeName,
                varImplBuiltin = lowerLocalVarBuiltin(parameter),
                local = when (val local = parameter.local) {
                    is NamedLocal -> IrNamedLocal(local.name)
                    is PositionalLocal -> {
                        val paramsOffset = buildList {
                            if (!hook.methodDescriptor.isStatic) {
                                add(hook.methodDescriptor.receiverTypeName)
                            }
                            addAll(hook.methodDescriptor.functionTypeParameters.map { it.typeName })
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
                namespace = if (parameter.isExported) options.modId else null,
            )

            else -> null
        }

    private fun lowerLocal(local: HookLocal, descriptor: Descriptor, typeName: IrTypeName): IrLocal =
        when (local) {
            is NamedLocal -> IrNamedLocal(local.name)
            is PositionalLocal -> {
                val paramsOffset = buildList {
                    if (!descriptor.isStatic) {
                        add(descriptor.receiverTypeName)
                    }
                    addAll(descriptor.functionTypeParameters.map { it.typeName })
                }.count { it == typeName }
                IrPositionalLocal(paramsOffset + local.ordinal)
            }
        }

    private fun lowerHookArgument(parameter: HookParameter): IrHookArgument =
        when (parameter) {
            is HookOriginValueParameter -> IrHookOriginValueArgument
            is HookOriginDescriptorWrapperParameter -> lowerHookOriginDescriptorWrapperParameter(parameter)
            is HookCancelDescriptorWrapperParameter -> IrHookCancelDescriptorWrapperImplArgument(
                IrCancelDescriptorWrapperImpl(
                    originatingFiles = listOfNotNull(parameter.descriptor.containingFile),
                    descriptorClassName = parameter.descriptor.className,
                    parameters = parameter.descriptor.functionTypeParameters.map { it.asIrFunctionTypeParameter() },
                    returnTypeName = parameter.descriptor.returnTypeName,
                )
            )

            is HookOriginInstanceofWrapperParameter -> IrHookOriginInstanceofWrapperImplArgument
            is HookOrdinalParameter -> IrHookOrdinalArgument
            is HookLocalParameter -> IrHookLocalArgument(
                name = parameter.name,
                isBody = parameter is HookBodyLocalParameter,
                isShare = parameter is HookShareLocalParameter,
                varBuiltin = lowerLocalVarBuiltin(parameter),
            )
        }

    private fun lowerHookOriginDescriptorWrapperParameter(
        parameter: HookOriginDescriptorWrapperParameter
    ): IrHookArgument {
        val descriptor = parameter.descriptor
        val originatingFiles = listOfNotNull(descriptor.containingFile)
        return when (parameter) {
            is HookOriginFieldGetDescriptorWrapperParameter -> IrHookOriginFieldGetDescriptorWrapperImplArgument(
                IrFieldGetDescriptorWrapperImpl(
                    originatingFiles = originatingFiles,
                    descriptorClassName = descriptor.className,
                    receiverTypeName = if (descriptor.isStatic) null else descriptor.receiverTypeName,
                    fieldTypeName = parameter.descriptor.fieldTypeName,
                )
            )

            is HookOriginFieldSetDescriptorWrapperParameter -> IrHookOriginFieldSetDescriptorWrapperImplArgument(
                IrFieldSetDescriptorWrapperImpl(
                    originatingFiles = originatingFiles,
                    descriptorClassName = descriptor.className,
                    receiverTypeName = if (descriptor.isStatic) null else descriptor.receiverTypeName,
                    fieldTypeName = parameter.descriptor.fieldTypeName,
                )
            )

            is HookOriginArrayGetDescriptorWrapperParameter -> IrHookOriginArrayGetDescriptorWrapperImplArgument(
                IrArrayGetDescriptorWrapperImpl(
                    originatingFiles = originatingFiles,
                    descriptorClassName = descriptor.className,
                    typeName = parameter.descriptor.fieldTypeName,
                    componentTypeName = parameter.arrayComponentTypeName,
                )
            )

            is HookOriginArraySetDescriptorWrapperParameter -> IrHookOriginArraySetDescriptorWrapperImplArgument(
                IrArraySetDescriptorWrapperImpl(
                    originatingFiles = originatingFiles,
                    descriptorClassName = descriptor.className,
                    typeName = parameter.descriptor.fieldTypeName,
                    componentTypeName = parameter.arrayComponentTypeName,
                )
            )

            is HookOriginBodyDescriptorWrapperParameter -> IrHookOriginBodyDescriptorWrapperImplArgument(
                IrBodyDescriptorWrapperImpl(
                    originatingFiles = originatingFiles,
                    descriptorClassName = descriptor.className,
                    parameters = descriptor.functionTypeParameters.map { it.asIrFunctionTypeParameter() },
                    returnTypeName = descriptor.returnTypeName,
                )
            )

            is HookOriginCallDescriptorWrapperParameter -> IrHookOriginCallDescriptorWrapperImplArgument(
                IrCallDescriptorWrapperImpl(
                    originatingFiles = originatingFiles,
                    descriptorClassName = descriptor.className,
                    receiverTypeName = if (descriptor.isStatic) null else descriptor.receiverTypeName,
                    parameters = descriptor.functionTypeParameters.map { it.asIrFunctionTypeParameter() },
                    returnTypeName = descriptor.returnTypeName,
                )
            )
        }
    }

    private fun lowerLocalVarBuiltin(parameter: HookLocalParameter): LocalVarImplBuiltin? =
        if (parameter.isLocalVar) LocalVarImplBuiltin.of(parameter.typeName)
        else null

    private fun resolveMixinRelatedClassName(
        sourceClassName: IrClassName,
        sourcePackageLCP: String,
        suffix: String,
    ): IrClassName {
        val sourcePackageName = sourceClassName.packageName
        val mixinPackageName = buildString {
            append(options.generatedMixinPackageName)
            if (sourcePackageName != sourcePackageLCP) {
                append(".")
                append(sourcePackageName.removePrefix("$sourcePackageLCP."))
            }
        }
        return IrClassName.of(mixinPackageName, sourceClassName.simpleName).derived(suffix)
    }

    private inline fun <reified T : IrDescriptorWrapperImpl<T>> findOriginDescriptorWrapperImpl(
        descriptorClassName: IrClassName
    ): T? =
        patches.asSequence()
            .flatMap { it.mixin.injections }
            .flatMap { it.hookArguments }
            .filterIsInstance<IrHookOriginDescriptorWrapperImplArgument<*>>()
            .map { it.wrapperImpl }
            .filterIsInstance<T>()
            .find { it.descriptorClassName == descriptorClassName }

    private fun findCancelDescriptorWrapperImpl(descriptorClassName: IrClassName): IrCancelDescriptorWrapperImpl? =
        patches.asSequence()
            .flatMap { it.mixin.injections }
            .flatMap { it.hookArguments }
            .filterIsInstance<IrHookCancelDescriptorWrapperImplArgument>()
            .map { it.wrapperImpl }
            .find { it.descriptorClassName == descriptorClassName }

    private fun findMixinSourcePackageLCP(sources: List<SourceFile>): String =
        sources.map { it.className.packageName }.reduceOrNull { lcp, next ->
            val currentParts = lcp.split('.')
            val nextParts = next.split('.')
            currentParts.zip(nextParts)
                .takeWhile { (current, next) -> current == next }
                .joinToString(".") { it.first }
        }.orEmpty()

    private fun String.withModIdPrefix(): String =
        withInternalPrefix(options.modId)
}

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

fun KSType.asIrTypeName(): IrTypeName =
    toTypeName().asIrTypeName()

fun KSClassDeclaration.asIrClassName(): IrClassName =
    toClassName().asIrClassName()
