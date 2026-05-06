package io.github.recrafter.lapis.phases.lowering

import com.squareup.kotlinpoet.asTypeName
import io.github.recrafter.lapis.LapisLogger
import io.github.recrafter.lapis.LapisOptions
import io.github.recrafter.lapis.annotations.ConstructorHeadPhase
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ks.isInterface
import io.github.recrafter.lapis.extensions.withInternalPrefix
import io.github.recrafter.lapis.phases.builtins.DescriptorWrapperBuiltin
import io.github.recrafter.lapis.phases.builtins.LocalVarImplBuiltin
import io.github.recrafter.lapis.phases.common.binaryName
import io.github.recrafter.lapis.phases.common.getMixinReference
import io.github.recrafter.lapis.phases.lowering.models.*
import io.github.recrafter.lapis.phases.lowering.types.*
import io.github.recrafter.lapis.phases.validator.*
import org.spongepowered.asm.mixin.injection.Constant
import kotlin.reflect.KClass

class MixinLowering(
    private val options: LapisOptions,
    @Suppress("unused") private val logger: LapisLogger,
) {
    private val patches: MutableList<IrPatch> = mutableListOf()

    fun lower(result: ValidatorResult): IrResult {
        val mixinSourcePackageLCP = findMixinSourcePackageLCP(result.schemas, result.patches)
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
            is InvokableDescriptor -> {
                if (descriptor is ConstructorDescriptor) {
                    IrConstructorDescriptor(
                        callWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                        parameters = descriptor.parameters.map { it.asIrFunctionTypeParameter() },
                        returnTypeName = descriptor.className,
                    )
                } else {
                    IrMethodDescriptor(
                        name = descriptor.name,
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
                    name = descriptor.name,
                    fieldGetWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                    fieldSetWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                    arrayGetWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                    arraySetWrapperImpl = findOriginDescriptorWrapperImpl(descriptor.className),
                    typeName = descriptor.fieldTypeName,
                )
            }
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
        if (schema.accessRequest != null) {
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
                        parameterTypes = descriptor.parameters.map { it.typeName },
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
            members += when (descriptor) {
                is InvokableDescriptor -> {
                    val parameters = descriptor.parameters.map {
                        IrParameter(it.name ?: lapisError("Parameter name cannot be null"), it.typeName)
                    }
                    IrMixinAccessorMethodMember(
                        name = descriptor.name,
                        mappingName = descriptor.binaryName,
                        parameters = parameters,
                        returnTypeName = descriptor.returnTypeName,
                        isStatic = descriptor is ConstructorDescriptor || descriptor.isStatic,
                        schemaReceiverClassName = schema.className,
                    )
                }

                is FieldDescriptor -> {
                    IrMixinAccessorFieldMember(
                        name = descriptor.name,
                        mappingName = descriptor.mappingName,
                        typeName = descriptor.fieldTypeName,
                        isStatic = descriptor.isStatic,
                        removeFinal = accessRequest.shouldRemoveFinal,
                        ops = accessRequest.fieldOps,
                        schemaReceiverClassName = descriptor.className,
                    )
                }
            }
        }
        return IrMixinAccessor(
            originatingFiles = listOfNotNull(schema.containingFile),
            className = lowerMixinRelatedClassName(schema.className, sourcePackageLCP, "Accessor"),
            schemaClassName = schema.className,
            side = schema.side,
            targetInternalName = schema.originJvmClassName.internalName,
            receiverTypeName = schema.originTypeName,
            isAccessibleSchema = schema.isAccessible,
            members = members,
        )
    }

    private fun lowerPatch(patch: Patch, mixinSourcePackageLCP: String): IrPatch {
        val constructorArguments = patch.constructorParameters.map(::lowerPatchConstructorArgument)
        return IrPatch(
            isObject = patch.isObject,
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
        if (patch.hasStaticHooksOnly) null
        else IrPatchImpl(
            originatingFiles = listOfNotNull(patch.containingFile),
            className = patch.className.derived("_Impl"),
            constructorParameters = buildList {
                if (constructorArguments.any { it is IrPatchConstructorOriginArgument }) {
                    add(IrPatchImplConstructorInstanceParameter)
                }
            },
            initStrategy = patch.initStrategy,
        )

    private fun lowerMixin(patch: Patch, sourcePackageLCP: String): IrMixin =
        IrMixin(
            originatingFiles = listOfNotNull(patch.containingFile),
            className = lowerMixinRelatedClassName(patch.className, sourcePackageLCP, "Mixin"),
            targetInstanceTypeName = patch.schema.originTypeName,
            isInterfaceTarget = patch.schema.originClassDeclaration.isInterface,
            targetInternalName = patch.schema.originJvmClassName.internalName,
            side = patch.side,
            injections = patch.hooks.flatMap(::lowerInjections),
            bridge = lowerBridge(patch),
        )

    private fun lowerBridge(patch: Patch): IrMixinBridge? =
        if (patch.bridgeSources.isEmpty()) null
        else IrMixinBridge(
            originatingFiles = listOfNotNull(patch.containingFile),

            className = patch.className.derived("Bridge"),
            functions = patch.bridgeSources.map(::lowerBridgeFunction),
        )

    private fun lowerBridgeFunction(source: PatchBridgeSource): IrMixinBridgeFunction =
        when (source) {
            is PatchBridgeSourceProperty -> {
                val (setterName, setterSourceJvmName) = if (source.isMutable) {
                    source.setterJvmName.let { it?.withModIdPrefix() to it }
                } else {
                    null to null
                }
                IrMixinBridgeFunctionProperty(
                    sourceName = source.name,
                    typeName = source.typeName,
                    impl = lowerPropertyBridgeFunctionImpl(source),
                    getterName = source.getterJvmName.withModIdPrefix(),
                    getterSourceJvmName = source.getterJvmName,
                    setterName = setterName,
                    setterSourceJvmName = setterSourceJvmName,
                )
            }

            is PatchBridgeSourceFunction -> IrMixinBridgeFunctionFunction(
                sourceName = source.name,
                name = source.jvmName.withModIdPrefix(),
                sourceJvmName = source.jvmName,
                parameters = source.parameters.map { it.asIrParameter() },
                returnTypeName = source.returnTypeName,
                impl = lowerFunctionBridgeFunctionImpl(source),
            )
        }

    private fun lowerPropertyBridgeFunctionImpl(property: PatchBridgeSourceProperty): IrMixinBridgeFunctionPropertyImpl =
        when (property) {
            is PatchExtensionProperty -> IrMixinBridgeFunctionPropertyExtensionImpl
        }

    private fun lowerFunctionBridgeFunctionImpl(function: PatchBridgeSourceFunction): IrMixinBridgeFunctionFunctionImpl =
        when (function) {
            is PatchExtensionFunction -> IrMixinBridgeFunctionFunctionExtensionImpl
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
                add(IrInjectionCallbackParameter(hook.descriptor.returnTypeName))
            }
            addAll(hook.parameters.mapNotNull { lowerInjectionLocalParameter(hook, it) })
        }
        val hookArguments = hook.parameters.map(::lowerHookArgument)
        return hook.ordinals.ifEmpty { listOf(null) }.map { ordinal ->
            when (hook) {
                is MethodHeadHook -> IrMethodHeadInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = hook.descriptor.getMixinReference(),
                    parameters = parameters,
                    hookArguments = hookArguments,
                    isStatic = hook.descriptor.isStatic,
                )

                is ConstructorHeadHook -> IrConstructorHeadInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = hook.descriptor.getMixinReference(),
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
                    jvmName = hook.jvmName,
                    methodMixinReference = hook.targetDescriptor.getMixinReference(),
                    isStaticTarget = hook.targetDescriptor.isStatic,
                    returnTypeName = hook.returnTypeName,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    isStatic = hook.descriptor.isStatic,
                )

                is TailHook -> IrReturnInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = hook.descriptor.getMixinReference(),
                    parameters = parameters,
                    hookArguments = hookArguments,
                    ordinal = null,
                    isTail = true,
                    isStatic = hook.descriptor.isStatic,
                )

                is LocalHook -> IrModifyVariableInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = hook.descriptor.getMixinReference(),
                    returnTypeName = hook.returnTypeName,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    local = lowerLocal(hook.local, hook.descriptor, hook.typeName),
                    op = hook.op,
                    ordinal = ordinal,
                    isStatic = hook.descriptor.isStatic,
                )

                is InstanceofHook -> IrInstanceofInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = hook.descriptor.getMixinReference(),
                    className = hook.typeClassName,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    ordinal = ordinal,
                    isStatic = hook.descriptor.isStatic,
                )

                is ReturnHook -> {
                    if (hook.isInjectBased) {
                        IrReturnInjection(
                            jvmName = hook.jvmName,
                            methodMixinReference = hook.descriptor.getMixinReference(),
                            parameters = parameters,
                            hookArguments = hookArguments,
                            ordinal = ordinal,
                            isTail = false,
                            isStatic = hook.descriptor.isStatic,
                        )
                    } else {
                        IrModifyReturnValueInjection(
                            jvmName = hook.jvmName,
                            methodMixinReference = hook.descriptor.getMixinReference(),
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
                        is ClassLiteral -> listOf("classValue" to literal.typeClassName.internalName)
                        NullLiteral -> listOf("nullValue" to "true")
                    }
                    IrModifyExpressionValueInjection(
                        jvmName = hook.jvmName,
                        methodMixinReference = hook.descriptor.getMixinReference(),
                        parameters = parameters,
                        hookArguments = hookArguments,
                        constantTypeName = hook.typeName,
                        atArgs = args,
                        ordinal = ordinal,
                        isStatic = hook.descriptor.isStatic,
                    )
                }

                is FieldGetHook -> IrFieldGetInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = hook.descriptor.getMixinReference(),
                    parameters = parameters,
                    hookArguments = hookArguments,
                    targetMixinReference = hook.targetDescriptor.getMixinReference(isTarget = true),
                    isStaticTarget = hook.targetDescriptor.isStatic,
                    fieldTypeName = hook.typeName,
                    ordinal = ordinal,
                    isStatic = hook.descriptor.isStatic,
                )

                is FieldSetHook -> IrFieldSetInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = hook.descriptor.getMixinReference(),
                    parameters = parameters,
                    hookArguments = hookArguments,
                    targetMixinReference = hook.targetDescriptor.getMixinReference(isTarget = true),
                    isStaticTarget = hook.targetDescriptor.isStatic,
                    ordinal = ordinal,
                    isStatic = hook.descriptor.isStatic,
                )

                is ArrayHook -> IrArrayInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = hook.descriptor.getMixinReference(),
                    parameters = parameters,
                    hookArguments = hookArguments,
                    targetMixinReference = hook.targetDescriptor.getMixinReference(isTarget = true),
                    isStaticTarget = hook.targetDescriptor.isStatic,
                    ordinal = ordinal,
                    componentTypeName = hook.componentTypeName,
                    isStatic = hook.descriptor.isStatic,
                    op = hook.op,
                )

                is CallHook -> IrWrapOperationInjection(
                    jvmName = hook.jvmName,
                    methodMixinReference = hook.descriptor.getMixinReference(),
                    returnTypeName = hook.returnTypeName,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    targetMixinReference = hook.targetDescriptor.getMixinReference(isTarget = true),
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
                namespace = if (parameter.isExported) options.modId else null,
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

            is HookOriginDescriptorWrapperParameter -> {
                val descriptor = parameter.descriptor
                val originatingFiles = listOfNotNull(descriptor.containingFile)
                when (parameter) {
                    is HookOriginBodyDescriptorWrapperParameter -> IrHookOriginBodyDescriptorWrapperImplArgument(
                        IrBodyDescriptorWrapperImpl(
                            originatingFiles = originatingFiles,
                            descriptorClassName = descriptor.className,
                            wrapperBuiltin = DescriptorWrapperBuiltin.Body,
                            parameters = descriptor.parameters.map { it.asIrFunctionTypeParameter() },
                            returnTypeName = descriptor.returnTypeName,
                        )
                    )

                    is HookOriginFieldGetDescriptorWrapperParameter -> {
                        IrHookOriginFieldGetDescriptorWrapperImplArgument(
                            IrFieldGetDescriptorWrapperImpl(
                                originatingFiles = originatingFiles,
                                descriptorClassName = descriptor.className,
                                wrapperBuiltin = DescriptorWrapperBuiltin.FieldGet,
                                receiverTypeName = if (descriptor.isStatic) null else descriptor.receiverTypeName,
                                fieldTypeName = parameter.descriptor.fieldTypeName,
                            )
                        )
                    }

                    is HookOriginFieldSetDescriptorWrapperParameter -> {
                        IrHookOriginFieldSetDescriptorWrapperImplArgument(
                            IrFieldSetDescriptorWrapperImpl(
                                originatingFiles = originatingFiles,
                                descriptorClassName = descriptor.className,
                                wrapperBuiltin = DescriptorWrapperBuiltin.FieldSet,
                                receiverTypeName = if (descriptor.isStatic) null else descriptor.receiverTypeName,
                                fieldTypeName = parameter.descriptor.fieldTypeName,
                            )
                        )
                    }

                    is HookOriginArrayGetDescriptorWrapperParameter -> {
                        IrHookOriginArrayGetDescriptorWrapperImplArgument(
                            IrArrayGetDescriptorWrapperImpl(
                                originatingFiles = originatingFiles,
                                descriptorClassName = descriptor.className,
                                wrapperBuiltin = DescriptorWrapperBuiltin.ArrayGet,
                                arrayTypeName = parameter.descriptor.fieldTypeName,
                                arrayComponentTypeName = parameter.arrayComponentTypeName,
                            )
                        )
                    }

                    is HookOriginArraySetDescriptorWrapperParameter -> {
                        IrHookOriginArraySetDescriptorWrapperImplArgument(
                            IrArraySetDescriptorWrapperImpl(
                                originatingFiles = originatingFiles,
                                descriptorClassName = descriptor.className,
                                wrapperBuiltin = DescriptorWrapperBuiltin.ArraySet,
                                arrayTypeName = parameter.descriptor.fieldTypeName,
                                arrayComponentTypeName = parameter.arrayComponentTypeName,
                            )
                        )
                    }

                    is HookOriginCallDescriptorWrapperParameter -> IrHookOriginCallDescriptorWrapperImplArgument(
                        IrCallDescriptorWrapperImpl(
                            originatingFiles = originatingFiles,
                            descriptorClassName = descriptor.className,
                            wrapperBuiltin = DescriptorWrapperBuiltin.Call,
                            receiverTypeName = if (descriptor.isStatic) null else descriptor.receiverTypeName,
                            parameters = descriptor.parameters.map { it.asIrFunctionTypeParameter() },
                            returnTypeName = descriptor.returnTypeName,
                        )
                    )

                    is HookCancelDescriptorWrapperParameter -> IrHookCancelDescriptorWrapperImplArgument(
                        IrCancelDescriptorWrapperImpl(
                            originatingFiles = originatingFiles,
                            descriptorClassName = descriptor.className,
                            wrapperBuiltin = DescriptorWrapperBuiltin.Cancel,
                            parameters = descriptor.parameters.map { it.asIrFunctionTypeParameter() },
                            returnTypeName = if (descriptor is MethodDescriptor) descriptor.returnTypeName else null
                        )
                    )
                }
            }

            is HookOriginInstanceofWrapperParameter -> IrHookOriginInstanceofWrapperImplArgument

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

    private fun lowerMixinRelatedClassName(
        sourceClassName: IrClassName,
        sourcePackageLCP: String,
        suffix: String,
    ): IrClassName {
        val fullPackage = sourceClassName.packageName
        val finalPackageName = buildString {
            append(options.generatedMixinPackageName)
            if (fullPackage != sourcePackageLCP) {
                append(".")
                append(fullPackage.removePrefix("$sourcePackageLCP."))
            }
        }
        return IrClassName.of(finalPackageName, "${sourceClassName.simpleName}_$suffix")
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

    private fun findMixinSourcePackageLCP(schemas: List<Schema>, patches: List<Patch>): String {
        val classNames = (schemas + patches).map { it.className }
        return when {
            classNames.isEmpty() -> ""
            classNames.size == 1 -> classNames.first().packageName
            else -> {
                val packageNames = classNames.map { it.packageName }.sorted()
                val first = packageNames.first().split('.')
                val last = packageNames.last().split('.')
                first.zip(last).takeWhile { (previous, next) -> previous == next }.joinToString(".") { it.first }
            }
        }
    }

    private fun String.withModIdPrefix(): String =
        withInternalPrefix(options.modId)
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
