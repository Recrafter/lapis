package io.github.recrafter.lapis.layers.lowering

import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.recrafter.lapis.extensions.capitalize
import io.github.recrafter.lapis.extensions.common.getAnnotationDefaultIntValue
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ksp.KspClassDeclaration
import io.github.recrafter.lapis.extensions.ksp.KspType
import io.github.recrafter.lapis.layers.ApiContext
import io.github.recrafter.lapis.layers.RuntimeApi
import io.github.recrafter.lapis.layers.generator.withInternalPrefix
import io.github.recrafter.lapis.layers.lowering.types.*
import io.github.recrafter.lapis.layers.validator.*
import io.github.recrafter.lapis.options.Options
import org.spongepowered.asm.mixin.injection.At
import kotlin.reflect.KClass

class MixinLowering(
    private val options: Options,
    private val runtimeApi: RuntimeApi,
) {
    fun lower(validatorResult: ValidatorResult): IrResult {
        val descriptorBindings = validatorResult.descriptors.map { validated ->
            DescriptorBinding(
                validated = validated,
                lowered = lowerDescriptor(validated, validatorResult.patches),
            )
        }
        return IrResult(
            descriptors = descriptorBindings.map { it.lowered },
            mixins = validatorResult.patches.map { lowerMixin(it, descriptorBindings) },
        )
    }

    private fun lowerDescriptor(descriptor: Descriptor, patches: List<Patch>): IrDescriptor =
        when (descriptor) {
            is MethodDescriptor -> {
                val descriptorDeclarationType = descriptor.classDeclaration.asIr()
                val isContextUsed = patches.any { patch ->
                    patch.hooks.any { hook ->
                        hook.parameters.any { parameter ->
                            parameter is HookContextParameter && parameter.descriptor == descriptor
                        }
                    }
                }
                IrDescriptor(
                    source = descriptor.source,

                    contextImpl = if (isContextUsed) {
                        IrDescriptorContextImpl(
                            type = IrClassName.of(
                                options.generatedPackageName,
                                "ContextImpl".withQualifiedNamePrefix(descriptorDeclarationType)
                            ),
                            superType = runtimeApi[ApiContext].parameterizedBy(descriptorDeclarationType),
                            parameters = descriptor.parameters.asIr(),
                            returnType = descriptor.returnType,
                        )
                    } else null,
                    targetImpl = IrDescriptorTargetImpl(
                        type = IrClassName.of(
                            options.generatedPackageName,
                            "TargetImpl".withQualifiedNamePrefix(descriptorDeclarationType)
                        ),
                        superType = descriptorDeclarationType,
                        receiverType = when {
                            descriptor.isStatic -> null
                            else -> descriptor.receiverType
                        },
                        parameters = descriptor.parameters.asIr(),
                        returnType = descriptor.returnType,
                    ),
                )
            }

            is FieldDescriptor -> {
                TODO()
            }
        }

    private fun lowerMixin(patch: Patch, descriptorBindings: List<DescriptorBinding>): IrMixin {
        val patchDeclarationType = patch.classDeclaration.asIr()
        return IrMixin(
            source = patch.source,

            type = IrClassName.of(
                options.mixinPackageName,
                "Mixin".withQualifiedNamePrefix(patchDeclarationType)
            ),
            side = patch.side,
            patchDeclarationType = patchDeclarationType,
            patchImplType = IrClassName.of(
                options.generatedPackageName,
                "Impl".withQualifiedNamePrefix(patchDeclarationType)
            ),
            targetType = patch.targetClassDeclaration.asIr(),
            extension = lowerExtension(patch, patchDeclarationType),
            accessor = lowerAccessor(patch, patchDeclarationType),
            injections = patch.hooks.flatMap { lowerInjections(it, descriptorBindings) },
            innerMixins = patch.innerPatches.map { lowerMixin(it, descriptorBindings) },
        )
    }

    private fun lowerAccessor(patch: Patch, patchDeclarationType: IrClassName): IrAccessor? {
        if (patch.accessProperties.isEmpty() && patch.accessFunctions.isEmpty() && patch.accessConstructors.isEmpty()) {
            return null
        }
        return IrAccessor(
            type = IrClassName.of(
                options.mixinPackageName,
                "Accessor".withQualifiedNamePrefix(patchDeclarationType)
            ),
            kinds = buildList {
                patch.accessProperties.forEach { property ->
                    add(
                        IrFieldGetterAccessor(
                            source = property.source,

                            name = property.name,
                            internalName = "get" + property.name.capitalize(),
                            targetName = property.vanillaName,
                            type = property.type.asIr(),
                            isStatic = property.isStatic,
                        )
                    )
                    if (property.isMutable) {
                        add(
                            IrFieldSetterAccessor(
                                source = property.source,

                                name = property.name,
                                internalName = "set" + property.name.capitalize(),
                                targetName = property.vanillaName,
                                type = property.type.asIr(),
                                isStatic = property.isStatic,
                            )
                        )
                    }
                }
                addAll(patch.accessConstructors.map { constructor ->
                    IrConstructorAccessor(
                        source = constructor.source,

                        name = constructor.name,
                        internalName = "invoke" + constructor.name.capitalize(),
                        parameters = constructor.parameters.asIr(),
                        classType = constructor.classType.asIr(),
                    )
                })
                addAll(patch.accessFunctions.map { function ->
                    IrMethodAccessor(
                        source = function.source,

                        name = function.name,
                        internalName = "invoke" + function.name.capitalize(),
                        targetName = function.vanillaName,
                        parameters = function.parameters.asIr(),
                        returnType = function.returnType?.asIr(),
                        isStatic = function.isStatic,
                    )
                })
            },
        )
    }

    private fun lowerExtension(patch: Patch, patchDeclarationType: IrClassName): IrExtension? {
        if (patch.sharedProperties.isEmpty() && patch.sharedFunctions.isEmpty()) {
            return null
        }
        return IrExtension(
            type = IrClassName.of(
                options.generatedPackageName,
                "Extension".withQualifiedNamePrefix(patchDeclarationType)
            ),
            kinds = buildList {
                patch.sharedProperties.forEach { property ->
                    add(
                        IrFieldGetterExtension(
                            name = property.name,
                            internalName = ("get" + property.name.capitalize()).withModId(),
                            type = property.type.asIr(),
                        )
                    )
                    if (property.isMutable && property.isSetterPublic) {
                        add(
                            IrFieldSetterExtension(
                                name = property.name,
                                internalName = ("set" + property.name.capitalize()).withModId(),
                                type = property.type.asIr(),
                            )
                        )
                    }
                }
                addAll(patch.sharedFunctions.map { function ->
                    IrMethodExtension(
                        name = function.name,
                        internalName = function.name.withModId(),
                        parameters = function.parameters.asIr(),
                        returnType = function.returnType?.asIr(),
                    )
                })
            },
        )
    }

    private fun lowerInjections(hook: Hook, descriptorBindings: List<DescriptorBinding>): List<IrInjection> {
        val parameters = hook.parameters.flatMap { lowerInjectionParameter(hook, it) }
        return when (hook) {
            is MethodBodyHook -> listOf(
                IrWrapMethodInjection(
                    source = hook.source,

                    name = hook.name,
                    hookName = hook.name,
                    method = hook.descriptor.getMemberReference(),
                    isStatic = hook.descriptor.isStatic,
                    returnType = hook.returnType?.asIr(),
                    parameters = parameters,
                    hookArguments = hook.parameters.map { lowerHookArgument(it, descriptorBindings) },
                )
            )

            is InvokeMethodHook -> hook.ordinals.map { ordinal ->
                IrWrapOperationInjection(
                    source = hook.source,

                    name = buildString {
                        append(hook.name)
                        if (hook.ordinals.size > 1) {
                            append("_ordinal$ordinal")
                        }
                    },
                    hookName = hook.name,
                    method = hook.descriptor.getMemberReference(),
                    returnType = hook.returnType?.asIr(),
                    parameters = parameters,
                    hookArguments = hook.parameters.map { lowerHookArgument(it, descriptorBindings, ordinal) },
                    target = hook.targetDescriptor.getMemberReference(withReceiver = true),
                    isStatic = hook.targetDescriptor.isStatic,
                    ordinal = ordinal.takeIf { it != getAnnotationDefaultIntValue(At::ordinal) },
                )
            }

            is LiteralHook -> hook.ordinals.map { ordinal ->
                IrModifyConstantValueInjection(
                    source = hook.source,

                    name = buildString {
                        append(hook.name)
                        if (hook.ordinals.size > 1) {
                            append("_ordinal$ordinal")
                        }
                    },
                    hookName = hook.name,
                    method = hook.descriptor.getMemberReference(),
                    parameters = parameters,
                    hookArguments = hook.parameters.map { lowerHookArgument(it, descriptorBindings, ordinal) },
                    literalType = hook.literalType.asIr(),
                    literalTypeName = hook.literalTypeName,
                    literalValue = hook.literalValue,
                    ordinal = ordinal.takeIf { it != getAnnotationDefaultIntValue(At::ordinal) },
                )
            }
        }
    }

    private fun lowerInjectionParameter(hook: Hook, parameter: HookParameter): List<IrInjectionParameter> =
        when (parameter) {
            is HookContextParameter -> buildList {
                var currentSlot = if (parameter.descriptor.isStatic) 0 else 1
                addAll(parameter.descriptor.parameters.map { parameter ->
                    val irType = parameter.type
                    val irParameter = IrInjectionParameterParameter(parameter.name, irType, currentSlot)
                    currentSlot += if (irType.is64bit) 2 else 1
                    return@map irParameter
                })
                add(IrInjectionCallbackParameter(parameter.descriptor.returnType))
            }

            is HookTargetParameter -> buildList {
                if (hook is InvokeMethodHook && !parameter.descriptor.isStatic) {
                    add(IrInjectionReceiverParameter(parameter.descriptor.receiverType))
                }
                addAll(parameter.descriptor.parameters.map { parameter ->
                    IrInjectionArgumentParameter(parameter.name, parameter.type)
                })
                add(IrInjectionOperationParameter(parameter.descriptor.returnType))
            }

            is HookLiteralParameter -> listOf(
                IrInjectionLiteralParameter(parameter.type.asIr())
            )

            is HookLocalParameter -> {
                val signatureOffset = buildList {
                    if (!hook.descriptor.isStatic) {
                        add(hook.descriptor.receiverType)
                    }
                    addAll(hook.descriptor.parameters.map { it.type })
                }.count { it == parameter.type }
                listOf(
                    IrInjectionLocalParameter(
                        parameter.name,
                        parameter.type.asIr(),
                        signatureOffset + parameter.ordinal
                    )
                )
            }

            is HookOrdinalParameter -> emptyList()
        }

    private fun lowerHookArgument(
        parameter: HookParameter,
        descriptorBindings: List<DescriptorBinding>,
        ordinal: Int = getAnnotationDefaultIntValue(At::ordinal),
    ): IrHookArgument =
        when (parameter) {
            is HookContextParameter -> IrHookContextArgument(
                descriptorBindings
                    .first { it.validated.classDeclaration == parameter.descriptor.classDeclaration }
                    .lowered
                    .contextImpl!!
            )

            is HookTargetParameter -> IrHookTargetArgument(
                descriptorBindings
                    .first { it.validated.classDeclaration == parameter.descriptor.classDeclaration }
                    .lowered
                    .targetImpl
            )

            is HookLiteralParameter -> IrHookLiteralArgument
            is HookOrdinalParameter -> IrHookOrdinalArgument(ordinal)
            is HookLocalParameter -> IrHookLocalArgument(parameter.name)
        }

    private fun String.withModId(): String =
        withInternalPrefix(options.modId)

    private class DescriptorBinding(
        val validated: Descriptor,
        val lowered: IrDescriptor,
    )
}

fun KClass<*>.asIr(): IrClassName =
    asClassName().asIr()

fun KspType.asIr(): IrTypeName =
    toTypeName().asIr()

fun KspClassDeclaration.asIr(): IrClassName =
    toClassName().asIr()

fun List<FunctionParameter>.asIr(): List<IrParameter> =
    map { parameter -> IrParameter(parameter.name, parameter.type) }

fun KPTypeName.asIr(): IrTypeName =
    IrTypeName(this)

fun KPClassName.asIr(): IrClassName =
    IrClassName(this)

fun KPParameterizedTypeName.asIr(): IrParameterizedTypeName =
    IrParameterizedTypeName(this)

fun KPWildcardTypeName.asIr(): IrWildcardTypeName =
    IrWildcardTypeName(this)

fun KPTypeVariableName.asIr(): IrTypeVariable =
    IrTypeVariable(this)

fun String.withQualifiedNamePrefix(className: IrClassName): String =
    withInternalPrefix(className.qualifiedName.replace('.', '_'))
