package io.github.recrafter.lapis.layers.lowering

import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.recrafter.lapis.api.LapisContext
import io.github.recrafter.lapis.extensions.capitalizeWithPrefix
import io.github.recrafter.lapis.extensions.common.asIr
import io.github.recrafter.lapis.extensions.common.getAnnotationDefaultIntValue
import io.github.recrafter.lapis.extensions.jvm.JvmVoid
import io.github.recrafter.lapis.extensions.kp.KPClassName
import io.github.recrafter.lapis.extensions.kp.KPParameterizedTypeName
import io.github.recrafter.lapis.extensions.kp.KPTypeName
import io.github.recrafter.lapis.extensions.kp.KPWildcardTypeName
import io.github.recrafter.lapis.extensions.ksp.KspClassDeclaration
import io.github.recrafter.lapis.extensions.ksp.KspType
import io.github.recrafter.lapis.extensions.withJavaInternalPrefix
import io.github.recrafter.lapis.layers.validator.*
import io.github.recrafter.lapis.options.Options
import org.spongepowered.asm.mixin.injection.At

class MixinLowering(private val options: Options) {

    fun lower(validatorResult: ValidatorResult): IrResult {
        val descriptorBindings = validatorResult.descriptors.map { validated ->
            DescriptorBinding(
                validated = validated,
                lowered = lowerDescriptor(validated),
            )
        }
        return IrResult(
            descriptors = descriptorBindings.map { it.lowered },
            mixins = validatorResult.rootPatches.map { lowerMixin(it, descriptorBindings) },
        )
    }

    private fun lowerDescriptor(descriptor: Descriptor): IrDescriptor {
        when (descriptor) {
            is MethodDescriptor -> {
                val superType = descriptor.classDeclaration.asIr()
                val containerType = descriptor.containerClassDeclaration.asIr()

                val implNamePrefix = "${containerType.simpleName}_${superType.simpleName}"
                return IrDescriptor(
                    source = descriptor.source,

                    contextImpl = IrDescriptorContextImpl(
                        type = IrClassName.of(options.generatedPackageName, "_${implNamePrefix}_ContextImpl"),
                        superType = LapisContext::class.asIr().parameterizedBy(superType),
                        parameters = descriptor.parameters.asIr(),
                        returnType = descriptor.returnType?.asIr(),
                    ),
                    targetImpl = IrDescriptorTargetImpl(
                        type = IrClassName.of(options.generatedPackageName, "_${implNamePrefix}_TargetImpl"),
                        superType = superType,
                        receiverType = when {
                            descriptor.isStatic -> null
                            else -> descriptor.receiverType.asIr()
                        },
                        parameters = descriptor.parameters.asIr(),
                        returnType = descriptor.returnType?.asIr(),
                    ),
                )
            }

            is FieldDescriptor -> {
                TODO()
            }
        }
    }

    private fun lowerMixin(patch: Patch, descriptorBindings: List<DescriptorBinding>): IrMixin =
        IrMixin(
            source = patch.source,

            type = IrClassName.of(
                options.mixinPackageName,
                "_" + patch.name + "_Mixin"
            ),
            side = patch.side,
            patchType = patch.classDeclaration.asIr(),
            patchImplType = IrClassName.of(
                options.generatedPackageName,
                "_" + patch.name + "_Impl"
            ),
            targetType = patch.targetClassDeclaration.asIr(),
            extension = lowerExtension(patch),
            accessor = lowerAccessor(patch),
            injections = patch.hooks.flatMap { lowerInjections(it, descriptorBindings) },
            innerMixins = patch.innerPatches.map { lowerMixin(it, descriptorBindings) },
        )

    private fun lowerAccessor(patch: Patch): IrAccessor? {
        if (patch.accessProperties.isEmpty() && patch.accessFunctions.isEmpty() && patch.accessConstructors.isEmpty()) {
            return null
        }
        return IrAccessor(
            type = IrClassName.of(options.mixinPackageName, "_" + patch.name + "_Accessor"),
            kinds = buildList {
                patch.accessProperties.forEach { property ->
                    add(
                        IrFieldGetterAccessor(
                            source = property.source,

                            name = property.name,
                            internalName = property.name.capitalizeWithPrefix("get"),
                            vanillaName = property.vanillaName,
                            type = property.type.asIr(),
                            isStatic = property.isStatic,
                        )
                    )
                    if (property.isMutable) {
                        add(
                            IrFieldSetterAccessor(
                                source = property.source,

                                name = property.name,
                                internalName = property.name.capitalizeWithPrefix("set"),
                                vanillaName = property.vanillaName,
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
                        internalName = constructor.name.capitalizeWithPrefix("invoke"),
                        parameters = constructor.parameters.asIr(),
                        classType = constructor.classType.asIr(),
                    )
                })
                addAll(patch.accessFunctions.map { function ->
                    IrMethodAccessor(
                        source = function.source,

                        name = function.name,
                        internalName = function.name.capitalizeWithPrefix("invoke"),
                        vanillaName = function.vanillaName,
                        parameters = function.parameters.asIr(),
                        returnType = function.returnType?.asIr(),
                        isStatic = function.isStatic,
                    )
                })
            },
        )
    }

    private fun lowerExtension(patch: Patch): IrExtension? {
        if (patch.sharedProperties.isEmpty() && patch.sharedFunctions.isEmpty()) {
            return null
        }
        return IrExtension(
            type = IrClassName.of(options.generatedPackageName, "_" + patch.name + "_Extension"),
            kinds = buildList {
                patch.sharedProperties.forEach { property ->
                    add(
                        IrFieldGetterExtension(
                            name = property.name,
                            internalName = property.name.capitalizeWithPrefix("get").withModId(),
                            type = property.type.asIr(),
                        )
                    )
                    if (property.isMutable) {
                        add(
                            IrFieldSetterExtension(
                                name = property.name,
                                internalName = property.name.capitalizeWithPrefix("set").withModId(),
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
                    method = hook.method.getJvmDescriptor(),
                    isStatic = hook.method.isStatic,
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
                    method = hook.method.getJvmDescriptor(),
                    returnType = hook.returnType?.asIr(),
                    parameters = parameters,
                    hookArguments = hook.parameters.map { lowerHookArgument(it, descriptorBindings, ordinal) },
                    target = hook.target.getJvmDescriptor(withReceiver = true),
                    isStatic = hook.target.isStatic,
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
                    method = hook.method.getJvmDescriptor(),
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
                    val irType = parameter.type.asIr()
                    val irParameter = IrInjectionSignatureLocalParameter(parameter.name, irType, currentSlot)
                    currentSlot += if (irType.is64bit) 2 else 1
                    return@map irParameter
                })
                add(IrInjectionCallbackParameter(parameter.descriptor.returnType?.asIr()))
            }

            is HookTargetParameter -> buildList {
                if (hook is InvokeMethodHook && !parameter.descriptor.isStatic) {
                    add(IrInjectionReceiverParameter(parameter.descriptor.receiverType.asIr()))
                }
                addAll(parameter.descriptor.parameters.map { parameter ->
                    IrInjectionArgumentParameter(parameter.name, parameter.type.asIr())
                })
                add(IrInjectionOperationParameter(parameter.descriptor.returnType?.asIr()))
            }

            is HookLiteralParameter -> listOf(
                IrInjectionLiteralParameter(parameter.type.asIr())
            )

            is HookLocalParameter -> {
                val signatureOffset = buildList {
                    if (!hook.method.isStatic) {
                        add(hook.method.receiverType)
                    }
                    addAll(hook.method.parameters.map { it.type })
                }.count { it == parameter.type }
                listOf(
                    IrInjectionBodyLocalParameter(
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
                    .contextImpl
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
        withJavaInternalPrefix(options.modId)

    private class DescriptorBinding(
        val validated: Descriptor,
        val lowered: IrDescriptor,
    )
}

fun KspType.asIr(): IrTypeName =
    toTypeName().asIr()

fun KspClassDeclaration.asIr(): IrClassName =
    toClassName().asIr()

fun List<FunctionParameter>.asIr(): List<IrParameter> =
    map { parameter -> IrParameter(parameter.name, parameter.type.asIr()) }

fun KPTypeName.asIr(): IrTypeName =
    IrTypeName(this)

fun KPClassName.asIr(): IrClassName =
    IrClassName(this)

fun KPParameterizedTypeName.asIr(): IrParameterizedTypeName =
    IrParameterizedTypeName(this)

fun KPWildcardTypeName.asIr(): IrWildcardTypeName =
    IrWildcardTypeName(this)

fun Descriptor.getJvmDescriptor(withReceiver: Boolean = false): String =
    when (val descriptor = this) {
        is MethodDescriptor -> buildString {
            if (withReceiver) {
                append(receiverType.asIr().jvmType.mixinDescriptor)
            }
            append(name)
            append("(")
            parameters.forEach {
                append(it.type.asIr().jvmType.mixinDescriptor)
            }
            append(")")
            if (descriptor is ConstructorDescriptor) {
                append(JvmVoid)
            } else {
                append(returnType?.asIr()?.jvmType?.mixinDescriptor ?: JvmVoid)
            }
        }

        is FieldDescriptor -> TODO()
    }
