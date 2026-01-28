package io.github.recrafter.lapis.layers.lowering

import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.recrafter.lapis.extensions.capitalizeWithPrefix
import io.github.recrafter.lapis.extensions.jvm.JvmVoid
import io.github.recrafter.lapis.extensions.kp.KPClassName
import io.github.recrafter.lapis.extensions.kp.KPParameterizedTypeName
import io.github.recrafter.lapis.extensions.kp.KPTypeName
import io.github.recrafter.lapis.extensions.ksp.KspClassDeclaration
import io.github.recrafter.lapis.extensions.ksp.KspLogger
import io.github.recrafter.lapis.extensions.ksp.KspType
import io.github.recrafter.lapis.layers.validator.*
import io.github.recrafter.lapis.options.Options

class IrLowering(val options: Options, val logger: KspLogger) {

    fun lower(validatorResult: ValidatorResult): IrResult {
        val descriptorBindings = validatorResult.descriptors.mapNotNull { validated ->
            val lowered = lowerDescriptor(validated) ?: return@mapNotNull null
            DescriptorBinding(
                validated = validated,
                lowered = lowered,
            )
        }
        return IrResult(
            descriptorImpls = descriptorBindings.map { it.lowered },
            rootMixins = validatorResult.rootPatches.map { lowerMixin(null, it, descriptorBindings) },
        )
    }

    private fun lowerDescriptor(descriptor: Descriptor): IrDescriptorImpl? {
        if (descriptor !is MethodDescriptor) {
            return null
        }
        val superType = descriptor.classDeclaration.asIr()
        val containerType = descriptor.containerClassDeclaration.asIr()

        return IrDescriptorImpl(
            source = descriptor.source,

            type = IrClassName.of(options.generatedPackageName, buildString {
                append("_")
                append(containerType.simpleName)
                append("_")
                append(superType.simpleName)
                append("_Impl")
            }),
            superType = superType,
            receiverType = when {
                descriptor.isStatic -> null
                else -> descriptor.receiverType.asIr()
            },
            parameters = descriptor.parameters.asIr(),
            returnType = descriptor.returnType?.asIr(),
        )
    }

    private fun lowerMixin(parentPatch: Patch?, patch: Patch, descriptorBindings: List<DescriptorBinding>): IrMixin =
        IrMixin(
            source = patch.source,

            type = IrClassName.of(
                options.mixinPackageName,
                *listOfNotNull(
                    parentPatch?.let { "_" + it.name + "_Mixin" },
                    "_" + patch.name + "_Mixin",
                ).toTypedArray()
            ),
            side = patch.side,
            patchType = patch.classDeclaration.asIr(),
            patchImplType = IrClassName.of(
                options.generatedPackageName,
                *listOfNotNull(
                    parentPatch?.let { "_" + it.name + "_Impl" },
                    "_" + patch.name + "_Impl",
                ).toTypedArray()
            ),
            targetType = patch.targetClassDeclaration.asIr(),
            extension = lowerExtension(patch),
            accessor = lowerAccessor(patch),
            injections = patch.hooks.flatMap { lowerInjections(it, descriptorBindings) },
            innerMixins = patch.innerPatches.map { lowerMixin(patch, it, descriptorBindings) },
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
                    method = hook.method.jvmDescriptor,
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
                    method = hook.method.jvmDescriptor,
                    returnType = hook.returnType?.asIr(),
                    parameters = parameters,
                    hookArguments = hook.parameters.map { lowerHookArgument(it, descriptorBindings, ordinal) },
                    target = hook.target.jvmDescriptor,
                    ordinal = ordinal,
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
                    method = hook.method.jvmDescriptor,
                    parameters = parameters,
                    hookArguments = hook.parameters.map { lowerHookArgument(it, descriptorBindings, ordinal) },
                    literalType = hook.literalType.asIr(),
                    literalTypeName = hook.literalTypeName,
                    literalValue = hook.literalValue,
                    ordinal = ordinal,
                )
            }
        }
    }

    private fun lowerInjectionParameter(hook: Hook, parameter: HookParameter): List<IrInjectionParameter> {
        if (parameter is HookTargetParameter) {
            return buildList {
                if (hook is InvokeMethodHook && !parameter.descriptor.isStatic) {
                    add(IrInjectionReceiverParameter(parameter.descriptor.receiverType.asIr()))
                }
                addAll(parameter.descriptor.parameters.map { parameter ->
                    IrInjectionArgumentParameter(parameter.name, parameter.type.asIr())
                })
                add(IrInjectionOperationParameter(parameter.descriptor.returnType?.asIr()))
            }
        }
        return listOfNotNull(
            when (parameter) {
                is HookTargetParameter -> null
                is HookParameterParameter -> null
                is HookLiteralParameter -> IrInjectionLiteralParameter(parameter.type.asIr())
                is HookOrdinalParameter -> null
                is HookCancelerParameter -> null
                is HookReturnerParameter -> null
                is HookNamedLocalParameter -> null
                is HookPositionalLocalParameter -> null
            }
        )
    }

    private fun lowerHookArgument(
        parameter: HookParameter,
        descriptorBindings: List<DescriptorBinding>,
        ordinal: Int = -1,
    ): IrHookArgument =
        when (parameter) {
            is HookTargetParameter -> IrHookTargetArgument(
                descriptorBindings
                    .first { it.validated.classDeclaration == parameter.descriptor.classDeclaration }
                    .lowered
            )

            is HookParameterParameter -> IrHookParameterArgument()
            is HookLiteralParameter -> IrHookLiteralArgument()
            is HookOrdinalParameter -> IrHookOrdinalArgument(ordinal)
            is HookCancelerParameter -> IrHookCancelerArgument()
            is HookReturnerParameter -> IrHookReturnerArgument()
            is HookNamedLocalParameter -> IrHookNamedLocalArgument()
            is HookPositionalLocalParameter -> IrHookPositionalLocalArgument()
        }

    private fun String.withModId(): String =
        "${options.modId}__$this"

    private class DescriptorBinding(
        val validated: Descriptor,
        val lowered: IrDescriptorImpl,
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

val Descriptor.jvmDescriptor: String
    get() = when (this) {
        is MethodDescriptor -> buildString {
            append(receiverType.asIr().jvmDescriptor)
            append(name)
            append("(")
            append(parameters.joinToString("") { it.type.asIr().jvmDescriptor })
            append(")")
            append(returnType?.asIr()?.jvmDescriptor ?: JvmVoid)
        }

        is ConstructorDescriptor -> buildString {
            append(receiverType.asIr().jvmDescriptor)
            append("<init>")
            append("(")
            append(parameters.joinToString("") { it.type.asIr().jvmDescriptor })
            append(")")
            append(JvmVoid)
        }

        is FieldDescriptor -> TODO()
    }
