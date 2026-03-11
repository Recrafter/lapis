package io.github.recrafter.lapis.layers.validator

import com.google.devtools.ksp.isAbstract
import io.github.recrafter.lapis.annotations.LaLiteral
import io.github.recrafter.lapis.annotations.enums.LapisHookKind
import io.github.recrafter.lapis.extensions.common.defaultValue
import io.github.recrafter.lapis.extensions.ksp.*
import io.github.recrafter.lapis.layers.Builtin
import io.github.recrafter.lapis.layers.Builtins
import io.github.recrafter.lapis.layers.JavaMemberKind
import io.github.recrafter.lapis.layers.parser.*
import org.spongepowered.asm.mixin.injection.At
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class FrontendValidator(
    private val logger: KSPLogger,
    private val builtins: Builtins
) {
    private val descriptorBindings: MutableList<DescriptorBinding> = mutableListOf()
    private val patchBindings: MutableList<PatchBinding> = mutableListOf()

    fun validate(parserResult: ParserResult): ValidatorResult {
        descriptorBindings += parserResult.descriptorContainers.flatMap { container ->
            container.descriptors.mapNotNull { parsedDescriptor ->
                val validatedDescriptor = try {
                    validateDescriptor(container, parsedDescriptor)
                } catch (_: ValidationErrorSignal) {
                    return@mapNotNull null
                }
                DescriptorBinding(parsedDescriptor, validatedDescriptor)
            }
        }
        patchBindings += parserResult.patches.mapNotNull { parsedPatch ->
            val validatedPatch = try {
                validatePatch(parsedPatch, descriptorBindings)
            } catch (_: ValidationErrorSignal) {
                return@mapNotNull null
            }
            PatchBinding(parsedPatch, validatedPatch)
        }
        return ValidatorResult(
            descriptors = descriptorBindings.map { it.validatedDescriptor },
            patches = buildPatchHierarchy(patchBindings),
        )
    }

    private fun validateDescriptor(
        container: ParsedDescriptorContainer,
        descriptor: ParsedDescriptor,
    ): Descriptor = with(descriptor) {
        kspRequireNotNull(container.classType) { "0" }
        kspRequire(container.classType.typeParameters.isEmpty()) { "0.25" }
        kspRequireNotNull(classType) { "0.5" }
        kspRequire(classType.typeParameters.isEmpty()) { "0.75" }
        kspRequire(targetClassType.isSame(container.targetClassType)) { "1" }
        kspRequire(superClassType?.isInstance(builtins[Builtin.Descriptor]) == true) { "2" }
        kspRequire(isFunctionType) { "3" }
        kspRequire(isCallable) { "4" }

        val receiverType = if (hasStaticAnnotation) {
            kspRequireNotNull(targetClassType?.asStarProjectedType()) { "5" }
        } else {
            kspRequireNotNull(receiverType) { "6" }
            kspRequire(!receiverType.isFunctionType) { "6.5" }
            receiverType
        }
        val parameters = parameters.map { parameter ->
            kspRequire(!parameter.type.isFunctionType) { "7" }
            FunctionParameter(
                type = parameter.type,
                name = kspRequireNotNull(parameter.name) { "8" },
            )
        }
        kspRequire(returnType == null || !returnType.isFunctionType) { "8.5" }

        val memberKind = kspRequireNotNull(memberKinds.singleOrNull()) { "9" }
        when (memberKind) {
            JavaMemberKind.CONSTRUCTOR -> {
                kspRequire(!hasStaticAnnotation) { "13" }
                ConstructorDescriptor(
                    symbol = symbol,

                    classType = classType,
                    returnType = kspRequireNotNull(returnType) { "13.5" },
                    parameters = parameters,
                )
            }

            JavaMemberKind.METHOD -> {
                if (hasStaticAnnotation) {
                    kspRequire(!hasReceiver) { "10" }
                } else {
                    kspRequire(hasReceiver) { "11" }
                    kspRequire(functionTypeReceiverName == callableReceiverName) { "11.5" }
                }
                MethodDescriptor(
                    symbol = symbol,

                    classType = classType,
                    receiverType = receiverType,
                    returnType = returnType,
                    targetName = kspRequireNotNull(callableName) { "12" },
                    parameters = parameters,
                    isStatic = hasStaticAnnotation,
                )
            }

            JavaMemberKind.FIELD -> {
                if (hasStaticAnnotation) {
                    kspRequire(!hasReceiver) { "14" }
                } else {
                    kspRequire(hasReceiver) { "15" }
                    kspRequire(functionTypeReceiverName == callableReceiverName) { "15.5" }
                }
                FieldDescriptor(
                    symbol = symbol,

                    classType = classType,
                    receiverType = receiverType,
                    targetName = kspRequireNotNull(callableName) { "16" },
                    type = kspRequireNotNull(returnType) { "16.5" },
                    isStatic = hasStaticAnnotation,
                )
            }
        }
    }

    private fun validatePatch(
        patch: ParsedPatch,
        descriptors: List<DescriptorBinding>
    ): Patch = with(patch) {
        if (hasOuter) {
            kspRequire(hasOuterAnnotation) { "17" }
            kspRequire(outerClassType != null) { "18" }
            if (widener != null) {
                kspRequire(outerWidener?.isNotEmpty() == true) { "19" }
            }
        }
        kspRequireNotNull(name) { "20" }
        kspRequireNotNull(targetClassType) { "20.5" }
        kspRequireNotNull(side) { "21" }
        kspRequire(classType?.run { isAbstract() && !isInner() && isClass() } == true) { "22" }
        kspRequire(superClassType?.isInstance(builtins[Builtin.Patch]) == true) { "23" }
        kspRequire(superGenericClassType.isSame(targetClassType)) { "24" }

        val accessProperties = mutableListOf<AccessProperty>()
        val sharedProperties = mutableListOf<SharedProperty>()
        properties.forEach { property ->
            with(property) {
                if (hasAccessAnnotation) {
                    kspRequire(hasFieldAnnotation) { "25" }
                    kspRequire(isPublic && isAbstract) { "26" }
                    kspRequire(!isExtension) { "27" }
                    kspRequireNotNull(accessName) { "28" }
                    accessProperties += AccessProperty(
                        name = name,
                        type = type,
                        vanillaName = accessName,
                        isStatic = hasStaticAnnotation,
                        isMutable = isMutable,
                    )
                } else if (isPublic && !isAbstract && !isExtension) {
                    sharedProperties += SharedProperty(
                        name = name,
                        type = type,
                        isMutable = isMutable,
                        isSetterPublic = isSetterPublic,
                    )
                }
            }
        }

        val accessFunctions = mutableListOf<AccessFunction>()
        val accessConstructors = mutableListOf<AccessConstructor>()
        val hooks = mutableListOf<Hook>()
        val sharedFunctions = mutableListOf<SharedFunction>()
        functions.forEach { function ->
            with(function) {
                if (hasAccessAnnotation) {
                    val memberKind = kspRequireNotNull(accessMemberKinds.singleOrNull()) { "29" }
                    kspRequireNotNull(accessName) { "30" }
                    val parameters = parameters.map {
                        FunctionParameter(
                            name = kspRequireNotNull(it.name) { "31" },
                            type = kspRequireNotNull(it.type) { "32" },
                        )
                    }
                    if (memberKind == JavaMemberKind.CONSTRUCTOR) {
                        accessConstructors += AccessConstructor(
                            name = name,
                            parameters = parameters,
                            classType = kspRequireNotNull(returnType) { "33" },
                        )
                    } else {
                        accessFunctions += AccessFunction(
                            name = name,
                            vanillaName = accessName,
                            isStatic = hasStaticAnnotation,
                            parameters = parameters,
                            returnType = returnType,
                        )
                    }
                } else if (hasHookAnnotation) {
                    kspRequireNotNull(hookKind) { "34" }
                    val descriptor = descriptors
                        .find { it.parsedDescriptor.classType.isSame(hookDescriptorClassType) }
                        ?.validatedDescriptor
                    kspRequire(descriptor is InvokableDescriptor) { "35" }
                    val parameters = parameters.map {
                        validateHookParameter(hookKind, descriptor, it, descriptors)
                    }
                    val ordinalParameters = parameters.filterIsInstance<HookOrdinalParameter>()
                    if (ordinalParameters.size > 1) {
                        kspError { "35.1" }
                    }
                    val ordinals = ordinalParameters.singleOrNull()?.indices.orEmpty().ifEmpty {
                        listOf(At::ordinal.defaultValue)
                    }
                    hooks += when (hookKind) {
                        LapisHookKind.Body -> {
                            kspRequire(descriptor is MethodDescriptor) { "35.2" }
                            kspRequire(returnType.isSame(descriptor.returnType)) { "35.3" }
                            BodyHook(
                                name = name,
                                descriptor = descriptor,
                                returnType = returnType,
                                parameters = parameters,
                            )
                        }

                        LapisHookKind.Call -> {
                            val target = parameters.filterIsInstance<HookTargetParameter>().singleOrNull()?.descriptor
                            kspRequire(target is MethodDescriptor) { "36" }
                            kspRequire(returnType?.makeNotNullable().isSame(target.returnType)) { "37" }
                            CallHook(
                                name = name,
                                descriptor = descriptor,
                                returnType = returnType,
                                methodDescriptor = target,
                                ordinals = ordinals,
                                parameters = parameters,
                            )
                        }

                        LapisHookKind.Literal -> {
                            val literalParameter = parameters.filterIsInstance<HookLiteralParameter>().singleOrNull()
                            kspRequireNotNull(literalParameter) { "38" }
                            val literalType = literalParameter.type
                            kspRequire(literalType.isSame(returnType)) { "38.1" }
                            kspRequire(!literalType.isMarkedNullable) { "38.2" }
                            val literalTypeClass = when (literalParameter.typeName) {
                                LaLiteral::int.name -> Int::class
                                LaLiteral::float.name -> Float::class
                                LaLiteral::long.name -> Long::class
                                LaLiteral::double.name -> Double::class
                                LaLiteral::string.name -> String::class
                                else -> kspError { "38.3" }
                            }
                            kspRequire(literalType.declaration.isInstance(literalTypeClass)) { "38.4" }
                            LiteralHook(
                                name = name,
                                descriptor = descriptor,
                                type = literalType,
                                typeName = literalParameter.typeName,
                                value = literalParameter.value,
                                ordinals = ordinals,
                                parameters = parameters,
                            )
                        }

                        LapisHookKind.FieldGet -> {
                            val target = parameters.filterIsInstance<HookTargetParameter>().singleOrNull()?.descriptor
                            kspRequire(target is FieldDescriptor) { "38.4" }
                            kspRequire(returnType?.makeNotNullable().isSame(target.type)) { "38.5" }
                            FieldGetHook(
                                name = name,
                                descriptor = descriptor,
                                type = target.type,
                                ordinals = ordinals,
                                fieldDescriptor = target,
                                parameters = parameters,
                            )
                        }

                        LapisHookKind.Head -> TODO("The kind ${hookKind.name} is not implemented.")
                        LapisHookKind.Return -> TODO("The kind ${hookKind.name} is not implemented.")
                        LapisHookKind.Tail -> TODO("The kind ${hookKind.name} is not implemented.")
                        LapisHookKind.ReturnValue -> TODO("The kind ${hookKind.name} is not implemented.")
                        LapisHookKind.New -> TODO("The kind ${hookKind.name} is not implemented.")
                        LapisHookKind.FieldSet -> TODO("The kind ${hookKind.name} is not implemented.")
                        LapisHookKind.LocalGet -> TODO("The kind ${hookKind.name} is not implemented.")
                        LapisHookKind.LocalSet -> TODO("The kind ${hookKind.name} is not implemented.")
                    }
                } else if (isPublic && !isAbstract && !isExtension) {
                    sharedFunctions += SharedFunction(
                        name = name,
                        parameters = parameters.map {
                            FunctionParameter(
                                name = kspRequireNotNull(it.name) { "39" },
                                type = kspRequireNotNull(it.type) { "40" },
                            )
                        },
                        returnType = returnType,
                    )
                }
            }
        }
        return Patch(
            symbol = symbol,

            name = name,
            classType = classType,
            targetClassType = targetClassType,
            side = side,
            accessProperties = accessProperties,
            accessFunctions = accessFunctions,
            accessConstructors = accessConstructors,
            sharedProperties = sharedProperties,
            sharedFunctions = sharedFunctions,
            hooks = hooks,
        )
    }

    private fun validateHookParameter(
        hookKind: LapisHookKind,
        descriptor: InvokableDescriptor,
        parameter: ParsedPatchFunctionParameter,
        descriptors: List<DescriptorBinding>,
    ): HookParameter = with(parameter) {
        kspRequireNotNull(name) { "41.5" }
        kspRequireNotNull(type) { "42" }
        when {
            hasTargetAnnotation -> {
                kspRequire(hookKind != LapisHookKind.Literal) { "42.1" }
                val targetDescriptor = descriptors
                    .find { it.parsedDescriptor.classType.isSame(targetDescriptorGenericClassType) }
                    ?.validatedDescriptor
                when {
                    targetDescriptorClassType?.isInstance(builtins[Builtin.Callable]) == true -> {
                        kspRequire(targetDescriptor is InvokableDescriptor) { "42.3" }
                        HookCallableTargetParameter(targetDescriptor)
                    }

                    targetDescriptorClassType?.isInstance(builtins[Builtin.Getter]) == true -> {
                        kspRequire(targetDescriptor is FieldDescriptor) { "42.4" }
                        HookGetterTargetParameter(targetDescriptor)
                    }

                    targetDescriptorClassType?.isInstance(builtins[Builtin.Setter]) == true -> {
                        kspRequire(targetDescriptor is FieldDescriptor) { "42.5" }
                        HookSetterTargetParameter(targetDescriptor)
                    }

                    else -> kspError { "42.6" }
                }
            }

            hasContextAnnotation -> {
                kspRequire(hookKind != LapisHookKind.Body) { "43.1" }
                kspRequire(contextDescriptorClassType?.isInstance(builtins[Builtin.Context]) == true) { "43.2" }
                kspRequireNotNull(contextDescriptorGenericClassType) { "43.3" }
                val contextDescriptor = descriptors
                    .find { it.parsedDescriptor.classType.isSame(contextDescriptorGenericClassType) }
                    ?.validatedDescriptor
                kspRequire(contextDescriptor == descriptor) { "43.4" }
                HookContextParameter(descriptor)
            }

            hasLiteralAnnotation -> {
                kspRequire(hookKind == LapisHookKind.Literal) { "44" }
                HookLiteralParameter(
                    type = kspRequireNotNull(literalType) { "45" },
                    typeName = kspRequireNotNull(literalTypeName) { "45.5" },
                    value = kspRequireNotNull(literalValue) { "46" },
                )
            }

            hasOrdinalAnnotation -> {
                kspRequire(hookKind != LapisHookKind.Body) { "47" }
                kspRequire(ordinalIndices.isNotEmpty()) { "49" }
                ordinalIndices.forEach {
                    kspRequire(it >= 0) { "49.1" }
                }
                HookOrdinalParameter(ordinalIndices)
            }

            hasLocalAnnotation -> {
                kspRequire(hookKind != LapisHookKind.Body) { "50" }
                kspRequireNotNull(type) { "50.1" }
                kspRequireNotNull(localOrdinal) { "51" }
                kspRequire(localOrdinal >= 0) { "51.1" }
                HookLocalParameter(name, type, localOrdinal)
            }

            else -> kspError { "52" }
        }
    }

    private fun buildPatchHierarchy(bindings: List<PatchBinding>): List<Patch> {
        bindings.forEach { binding ->
            bindings
                .firstOrNull { it.parsedPatch.classType.isSame(binding.parsedPatch.outerClassType) }
                ?.validatedPatch
                ?.innerPatches
                ?.add(binding.validatedPatch)
        }
        return bindings
            .filter { it.parsedPatch.outerClassType == null }
            .map { it.validatedPatch }
    }

    private inline fun KSPSource.kspError(crossinline message: () -> String): Nothing {
        val message = message()
        logger.error(message, symbol)
        throw ValidationErrorSignal
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun KSPSource.kspRequire(condition: Boolean, crossinline message: () -> String) {
        contract {
            returns() implies condition
        }
        if (!condition) {
            kspError(message = message)
        }
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun <T> KSPSource.kspRequireNotNull(value: T?, crossinline message: () -> String): T {
        contract {
            returns() implies (value != null)
        }
        return value ?: kspError(message = message)
    }

    private class DescriptorBinding(
        val parsedDescriptor: ParsedDescriptor,
        val validatedDescriptor: Descriptor,
    )

    private class PatchBinding(
        val parsedPatch: ParsedPatch,
        val validatedPatch: Patch,
    )
}
