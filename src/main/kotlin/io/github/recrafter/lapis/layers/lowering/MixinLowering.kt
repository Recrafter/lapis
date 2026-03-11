package io.github.recrafter.lapis.layers.lowering

import com.squareup.kotlinpoet.asClassName
import io.github.recrafter.lapis.Options
import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.common.defaultValue
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ksp.KSPLogger
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.layers.Builtin
import io.github.recrafter.lapis.layers.Builtins
import io.github.recrafter.lapis.layers.generator.withInternalPrefix
import io.github.recrafter.lapis.layers.lowering.types.*
import io.github.recrafter.lapis.layers.validator.*
import org.spongepowered.asm.mixin.injection.At
import kotlin.reflect.KClass

class MixinLowering(
    private val options: Options,
    private val builtins: Builtins,
    private val logger: KSPLogger,
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
            is InvokableDescriptor -> {
                val callable = if (patches.hasHookParameter<HookCallableTargetParameter>(descriptor)) {
                    IrDescriptorCallable(
                        classType = IrClassType.of(
                            options.generatedPackageName,
                            "Callable".withQualifiedNamePrefix(descriptor.irClassType)
                        ),
                        superClassType = builtins[Builtin.Callable].generic(descriptor.irClassType),
                        receiverType = if (descriptor.isStatic) null else descriptor.irReceiverType,
                        parameters = descriptor.parameters.asIr(),
                        returnType = descriptor.irReturnType,
                    )
                } else null
                val context = if (patches.hasHookParameter<HookContextParameter>(descriptor)) {
                    IrDescriptorContext(
                        classType = IrClassType.of(
                            options.generatedPackageName,
                            "Context".withQualifiedNamePrefix(descriptor.irClassType)
                        ),
                        superClassType = builtins[Builtin.Context].generic(descriptor.irClassType),
                        parameters = descriptor.parameters.asIr(),
                        returnType = descriptor.irReturnType,
                    )
                } else null
                IrInvokableDescriptor(
                    containingFile = descriptor.containingFile,
                    classType = descriptor.irClassType,
                    callable = callable,
                    context = context,
                )
            }

            is FieldDescriptor -> {
                val getter = if (patches.hasHookParameter<HookGetterTargetParameter>(descriptor)) {
                    IrDescriptorGetter(
                        classType = IrClassType.of(
                            options.generatedPackageName,
                            "Getter".withQualifiedNamePrefix(descriptor.irClassType)
                        ),
                        superClassType = builtins[Builtin.Getter].generic(descriptor.irClassType),
                        receiverType = if (descriptor.isStatic) null else descriptor.irReceiverType,
                        type = descriptor.irType,
                    )
                } else null
                val setter = if (patches.hasHookParameter<HookSetterTargetParameter>(descriptor)) {
                    IrDescriptorSetter(
                        classType = IrClassType.of(
                            options.generatedPackageName,
                            "Setter".withQualifiedNamePrefix(descriptor.irClassType)
                        ),
                        superClassType = builtins[Builtin.Setter].generic(descriptor.irClassType),
                        receiverType = if (descriptor.isStatic) null else descriptor.irReceiverType,
                        type = descriptor.irType,
                    )
                } else null
                IrFieldDescriptor(
                    containingFile = descriptor.containingFile,
                    classType = descriptor.irClassType,
                    getter = getter,
                    setter = setter,
                )
            }
        }

    private fun lowerMixin(patch: Patch, descriptorBindings: List<DescriptorBinding>): IrMixin =
        IrMixin(
            containingFile = patch.containingFile,

            classType = IrClassType.of(
                options.mixinPackageName,
                "Mixin".withQualifiedNamePrefix(patch.irClassType)
            ),
            patchClassType = patch.irClassType,
            patchImplClassType = IrClassType.of(
                options.generatedPackageName,
                "Impl".withQualifiedNamePrefix(patch.irClassType)
            ),
            targetClassType = patch.irTargetClassType,

            side = patch.side,
            extension = lowerExtension(patch, patch.irClassType),
            accessor = lowerAccessor(patch, patch.irClassType),
            injections = patch.hooks.flatMap { lowerInjections(it, descriptorBindings) },

            innerMixins = patch.innerPatches.map { lowerMixin(it, descriptorBindings) },
        )

    private fun lowerAccessor(patch: Patch, patchClassType: IrClassType): IrAccessor? {
        if (patch.accessProperties.isEmpty() && patch.accessFunctions.isEmpty() && patch.accessConstructors.isEmpty()) {
            return null
        }
        return IrAccessor(
            classType = IrClassType.of(
                options.mixinPackageName,
                "Accessor".withQualifiedNamePrefix(patchClassType)
            ),
            kinds = buildList {
                patch.accessProperties.forEach { property ->
                    add(
                        IrFieldGetterAccessor(
                            name = property.name,
                            targetName = property.vanillaName,
                            type = property.irType,
                            isStatic = property.isStatic,
                        )
                    )
                    if (property.isMutable) {
                        add(
                            IrFieldSetterAccessor(
                                name = property.name,
                                targetName = property.vanillaName,
                                type = property.irType,
                                isStatic = property.isStatic,
                            )
                        )
                    }
                }
                addAll(patch.accessConstructors.map { constructor ->
                    IrConstructorAccessor(
                        name = constructor.name,
                        parameters = constructor.parameters.asIr(),
                        classType = constructor.irClassType,
                    )
                })
                addAll(patch.accessFunctions.map { function ->
                    IrMethodAccessor(
                        name = function.name,
                        targetName = function.vanillaName,
                        parameters = function.parameters.asIr(),
                        returnType = function.irReturnType,
                        isStatic = function.isStatic,
                    )
                })
            },
        )
    }

    private fun lowerExtension(patch: Patch, patchClassType: IrClassType): IrExtension? {
        if (patch.sharedProperties.isEmpty() && patch.sharedFunctions.isEmpty()) {
            return null
        }
        return IrExtension(
            classType = IrClassType.of(
                options.generatedPackageName,
                "Extension".withQualifiedNamePrefix(patchClassType)
            ),
            kinds = buildList {
                patch.sharedProperties.forEach { property ->
                    add(
                        IrFieldGetterExtension(
                            name = property.name,
                            type = property.irType,
                        )
                    )
                    if (property.isMutable && property.isSetterPublic) {
                        add(
                            IrFieldSetterExtension(
                                name = property.name,
                                type = property.irType,
                            )
                        )
                    }
                }
                addAll(patch.sharedFunctions.map { function ->
                    IrMethodExtension(
                        name = function.name,
                        parameters = function.parameters.asIr(),
                        returnType = function.irReturnType,
                    )
                })
            },
        )
    }

    private fun lowerInjections(hook: Hook, descriptorBindings: List<DescriptorBinding>): List<IrInjection> {
        val parameters = hook.parameters.flatMap { lowerInjectionParameter(hook, it) }
        return hook.ordinals.map { ordinal ->
            when (hook) {
                is BodyHook -> IrWrapMethodInjection(
                    name = hook.name,
                    method = hook.descriptor.getMemberReference(),
                    isStatic = hook.descriptor.isStatic,
                    returnType = hook.irReturnType,
                    parameters = parameters,
                    hookArguments = hook.parameters.map { lowerHookArgument(it, descriptorBindings) },
                )

                is CallHook -> IrWrapOperationInjection(
                    name = hook.name,
                    method = hook.descriptor.getMemberReference(),
                    returnType = hook.irReturnType,
                    parameters = parameters,
                    hookArguments = hook.parameters.map { lowerHookArgument(it, descriptorBindings, ordinal) },
                    target = hook.methodDescriptor.getMemberReference(withReceiver = true),
                    isStatic = hook.methodDescriptor.isStatic,
                    ordinal = ordinal,
                )

                is LiteralHook -> IrModifyConstantValueInjection(
                    name = hook.name,
                    method = hook.descriptor.getMemberReference(),
                    parameters = parameters,
                    hookArguments = hook.parameters.map { lowerHookArgument(it, descriptorBindings, ordinal) },
                    constantType = hook.irType,
                    constantTypeName = hook.typeName,
                    constantValue = hook.value,
                    ordinal = ordinal,
                )

                is FieldGetHook -> IrFieldGetInjection(
                    name = hook.name,
                    method = hook.descriptor.getMemberReference(),
                    parameters = parameters,
                    hookArguments = hook.parameters.map { lowerHookArgument(it, descriptorBindings, ordinal) },
                    target = hook.fieldDescriptor.getMemberReference(withReceiver = true),
                    isStatic = hook.fieldDescriptor.isStatic,
                    fieldType = hook.irType,
                    ordinal = ordinal,
                )
            }
        }
    }

    private fun lowerInjectionParameter(hook: Hook, parameter: HookParameter): List<IrInjectionParameter> =
        when (parameter) {
            is HookTargetParameter -> buildList {
                if (hook !is BodyHook && !parameter.descriptor.isStatic) {
                    add(IrInjectionReceiverParameter(parameter.descriptor.irReceiverType))
                }
                addAll(parameter.descriptor.parameters.map { parameter ->
                    IrInjectionArgumentParameter(parameter.name, parameter.irType)
                })
                add(IrInjectionOperationParameter(parameter.descriptor.irReturnType))
            }

            is HookContextParameter -> buildList {
                var currentSlot = if (parameter.descriptor.isStatic) 0 else 1
                addAll(parameter.descriptor.parameters.map { parameter ->
                    val irType = parameter.irType
                    val irParameter = IrInjectionParameterParameter(parameter.name, irType, currentSlot)
                    currentSlot += if (irType.is64bit) 2 else 1
                    return@map irParameter
                })
                add(IrInjectionCallbackParameter(parameter.descriptor.irReturnType))
            }

            is HookLiteralParameter -> listOf(
                IrInjectionLiteralParameter(parameter.irType)
            )

            is HookLocalParameter -> {
                val signatureOffset = buildList {
                    if (!hook.descriptor.isStatic) {
                        add(hook.descriptor.irReceiverType)
                    }
                    addAll(hook.descriptor.parameters.map { it.irType })
                }.count { it == parameter.irType }
                listOf(
                    IrInjectionLocalParameter(
                        parameter.name,
                        parameter.irType,
                        signatureOffset + parameter.ordinal
                    )
                )
            }

            is HookOrdinalParameter -> emptyList()
        }

    private fun lowerHookArgument(
        parameter: HookParameter,
        descriptorBindings: List<DescriptorBinding>,
        ordinal: Int = At::ordinal.defaultValue,
    ): IrHookArgument =
        when (parameter) {
            is HookCallableTargetParameter -> IrHookCallableTargetArgument(
                descriptorBindings.findLowered<IrInvokableDescriptor>(parameter.descriptor.irClassType).callable
                    ?: lapisError("Callable for ${parameter.descriptor.irClassType.qualifiedName.quoted()} expected")
            )

            is HookGetterTargetParameter -> IrHookGetterTargetArgument(
                descriptorBindings.findLowered<IrFieldDescriptor>(parameter.descriptor.irClassType).getter
                    ?: lapisError("Getter for ${parameter.descriptor.irClassType.qualifiedName.quoted()} expected")
            )

            is HookSetterTargetParameter -> IrHookSetterTargetArgument(
                descriptorBindings.findLowered<IrFieldDescriptor>(parameter.descriptor.irClassType).setter
                    ?: lapisError("Setter for ${parameter.descriptor.irClassType.qualifiedName.quoted()} expected")
            )

            is HookContextParameter -> IrHookContextArgument(
                descriptorBindings.findLowered<IrInvokableDescriptor>(parameter.descriptor.irClassType).context
                    ?: lapisError("Context for ${parameter.descriptor.irClassType.qualifiedName.quoted()} expected")
            )

            is HookLiteralParameter -> IrHookLiteralArgument
            is HookOrdinalParameter -> IrHookOrdinalArgument(ordinal)
            is HookLocalParameter -> IrHookLocalArgument(parameter.name)
        }

    private inline fun <reified T : IrDescriptor> List<DescriptorBinding>.findLowered(classType: IrClassType): T =
        firstOrNull { it.validated.irClassType == classType }?.lowered?.castOrNull<T>()
            ?: lapisError("Binding for ${classType.qualifiedName.quoted()} not found")

    private inline fun <reified T : HookDescriptorParameter> List<Patch>.hasHookParameter(
        descriptor: Descriptor
    ): Boolean =
        any { patch ->
            patch.hooks.any { hook ->
                hook.parameters.any {
                    it is T && it.descriptor == descriptor
                }
            }
        }

    private class DescriptorBinding(
        val validated: Descriptor,
        val lowered: IrDescriptor,
    )
}

fun List<FunctionParameter>.asIr(): List<IrParameter> =
    map { parameter -> IrParameter(parameter.name, parameter.irType) }

fun KClass<*>.asIr(): IrClassType =
    asClassName().asIr()

fun KPType.asIr(): IrType =
    IrType(this)

fun KPClassType.asIr(): IrClassType =
    IrClassType(this)

fun KPGenericType.asIr(): IrGenericType =
    IrGenericType(this)

fun KPWildcardType.asIr(): IrWildcardType =
    IrWildcardType(this)

fun KPVariableType.asIr(): IrVariableType =
    IrVariableType(this)

fun String.withQualifiedNamePrefix(className: IrClassType): String =
    withInternalPrefix(className.qualifiedName.replace('.', '_'))
