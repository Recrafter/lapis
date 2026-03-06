package io.github.recrafter.lapis.layers.validator

import com.google.devtools.ksp.isAbstract
import io.github.recrafter.lapis.annotations.LaLiteral
import io.github.recrafter.lapis.annotations.enums.LapisHookKind
import io.github.recrafter.lapis.extensions.common.getAnnotationDefaultIntValue
import io.github.recrafter.lapis.extensions.ksp.*
import io.github.recrafter.lapis.layers.*
import io.github.recrafter.lapis.layers.parser.*
import io.github.recrafter.lapis.layers.validator.signals.InvalidStateSignal
import org.spongepowered.asm.mixin.injection.At
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class FrontendValidator(
    private val logger: KspLogger,
    private val runtimeApi: RuntimeApi
) {
    private val descriptorBindings: MutableList<DescriptorBinding> = mutableListOf()

    fun validate(parserResult: ParserResult): ValidatorResult {
        val unresolvedSymbols = mutableListOf<KspAnnotated>()
        descriptorBindings += parserResult.descriptorContainers.flatMap { container ->
            container.descriptors.mapNotNull { parsed ->
                val validated = try {
                    validateDescriptor(container, parsed)
                } catch (signal: InvalidStateSignal) {
                    if (signal.isUnresolved) {
                        unresolvedSymbols += container.source
                    }
                    return@mapNotNull null
                }
                DescriptorBinding(parsed, validated)
            }
        }
        val patchBindings = parserResult.patches.mapNotNull { parsed ->
            val validated = try {
                validatePatch(parsed, descriptorBindings)
            } catch (signal: InvalidStateSignal) {
                if (signal.isUnresolved) {
                    unresolvedSymbols += parsed.source
                }
                return@mapNotNull null
            }
            PatchBinding(parsed, validated)
        }
        return ValidatorResult(
            descriptors = descriptorBindings.map { it.validated },
            patches = buildHierarchy(patchBindings),
            unresolvedSymbols = unresolvedSymbols,
        )
    }

    private fun validateDescriptor(
        container: ParsedDescriptorContainer,
        descriptor: ParsedDescriptor,
    ): Descriptor = with(descriptor) {
        kspRequireNotNull(container.classDeclaration) { "0" }
        kspRequire(container.classDeclaration.typeParameters.isEmpty()) { "0.25" }
        kspRequireNotNull(classDeclaration) { "0.5" }
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "0.75" }
        kspRequire(targetClassDeclaration.isSame(container.targetClassDeclaration)) { "1" }
        if (superClassDeclaration?.isInstance(runtimeApi[ApiDescriptor]) == false) {
            if (superClassDeclaration.isError) {
                throw InvalidStateSignal(true)
            }
            kspError { "2" }
        }
        kspRequire(isFunctionType) { "3" }
        kspRequire(isCallable) { "4" }

        val receiverType = if (hasStaticAnnotation) {
            kspRequireNotNull(targetClassDeclaration?.asStarProjectedType()) { "5" }
        } else {
            kspRequireNotNull(receiverType) { "6" }
        }
        val parameters = parameters.map { parameter ->
            kspRequire(!parameter.type.isFunctionType) { "7" }
            FunctionParameter(
                type = parameter.type,
                name = kspRequireNotNull(parameter.name) { "8" },
            )
        }

        val memberKind = kspRequireNotNull(memberKinds.singleOrNull()) { "9" }
        when (memberKind) {
            MemberKind.CONSTRUCTOR -> {
                kspRequire(!hasStaticAnnotation) { "13" }
                ConstructorDescriptor(
                    source = source,

                    classDeclaration = classDeclaration,
                    classType = kspRequireNotNull(returnType) { "13.5" },
                    parameters = parameters,
                )
            }

            MemberKind.METHOD -> {
                if (hasStaticAnnotation) {
                    kspRequire(!hasReceiver) { "10" }
                } else {
                    kspRequire(hasReceiver) { "11" }
                    kspRequire(functionTypeReceiverName == callableReceiverName) { "11.5" }
                }
                MethodDescriptor(
                    source = source,

                    classDeclaration = classDeclaration,
                    receiverType = receiverType,
                    returnType = returnType,
                    targetName = kspRequireNotNull(callableName) { "12" },
                    parameters = parameters,
                    isStatic = hasStaticAnnotation,
                )
            }

            MemberKind.FIELD -> {
                if (hasStaticAnnotation) {
                    kspRequire(!hasReceiver) { "14" }
                } else {
                    kspRequire(hasReceiver) { "15" }
                    kspRequire(functionTypeReceiverName == callableReceiverName) { "15.5" }
                }
                FieldDescriptor(
                    source = source,

                    classDeclaration = classDeclaration,
                    receiverType = receiverType,
                    targetName = kspRequireNotNull(callableName) { "16" },
                    type = kspRequireNotNull(returnType) { "16.5" },
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
            kspRequire(outerClassDeclaration != null) { "18" }
            if (widener != null) {
                kspRequire(outerWidener?.isNotEmpty() == true) { "19" }
            }
        }
        kspRequireNotNull(name) { "20" }
        kspRequireNotNull(targetClassDeclaration) { "20.5" }
        kspRequireNotNull(side) { "21" }
        kspRequire(classDeclaration?.run { isAbstract() && !isInner() && isClass() } == true) { "22" }

        if (superClassDeclaration?.isInstance(runtimeApi[ApiPatch]) == false) {
            if (superClassDeclaration.isError) {
                throw InvalidStateSignal(true)
            }
            kspError { "23" }
        }
        kspRequire(superGenericClassDeclaration.isSame(targetClassDeclaration)) { "24" }

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
                        source = source,

                        name = name,
                        type = type,
                        vanillaName = accessName,
                        isStatic = hasStaticAnnotation,
                        isMutable = isMutable,
                    )
                } else if (isPublic && !isAbstract && !isExtension) {
                    sharedProperties += SharedProperty(
                        source = source,

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
                    if (memberKind == MemberKind.CONSTRUCTOR) {
                        accessConstructors += AccessConstructor(
                            source = source,

                            name = name,
                            parameters = parameters,
                            classType = kspRequireNotNull(returnType) { "33" },
                        )
                    } else {
                        accessFunctions += AccessFunction(
                            source = source,

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
                        .find { it.parsed.classDeclaration.isSame(hookDescriptorClassDeclaration) }
                        ?.validated
                    kspRequire(descriptor is MethodDescriptor) { "35" }
                    val parameters = parameters.map {
                        validateHookParameter(hookKind, descriptor, it, descriptors)
                    }
                    val ordinalParameters = parameters.filterIsInstance<HookOrdinalParameter>()
                    if (ordinalParameters.size > 1) {
                        kspError { "35.1" }
                    }
                    val ordinals = ordinalParameters.singleOrNull()?.indices.orEmpty().ifEmpty {
                        listOf(getAnnotationDefaultIntValue(At::ordinal))
                    }
                    hooks += when (hookKind) {
                        LapisHookKind.Body -> {
                            kspRequire(returnType.isSame(descriptor.kspReturnType)) { "35.2" }
                            MethodBodyHook(
                                source = source,

                                name = name,
                                descriptor = descriptor,
                                returnType = returnType,
                                parameters = parameters,
                            )
                        }

                        LapisHookKind.Call -> {
                            val target = parameters.filterIsInstance<HookTargetParameter>().singleOrNull()?.descriptor
                            kspRequireNotNull(target) { "36" }
                            kspRequire(returnType?.makeNotNullable().isSame(target.kspReturnType)) { "37" }
                            InvokeMethodHook(
                                source = source,

                                name = name,
                                descriptor = descriptor,
                                returnType = returnType,
                                targetDescriptor = target,
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
                            val literalTypeName = literalParameter.typeName
                            val literalTypeClass = when (literalTypeName) {
                                LaLiteral::int.name -> Int::class
                                LaLiteral::float.name -> Float::class
                                LaLiteral::long.name -> Long::class
                                LaLiteral::double.name -> Double::class
                                LaLiteral::string.name -> String::class
                                else -> kspError { "38.3" }
                            }
                            kspRequire(literalType.declaration.isInstance(literalTypeClass)) { "38.4" }
                            LiteralHook(
                                source = source,

                                name = name,
                                descriptor = descriptor,
                                literalType = literalType,
                                literalTypeName = literalTypeName,
                                literalValue = literalParameter.value,
                                ordinals = ordinals,
                                parameters = parameters,
                            )
                        }

                        else -> TODO()
                    }
                } else if (isPublic && !isAbstract && !isExtension) {
                    sharedFunctions += SharedFunction(
                        source = source,

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
            source = source,

            name = name,
            classDeclaration = classDeclaration,
            targetClassDeclaration = targetClassDeclaration,
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
        descriptor: MethodDescriptor,
        parameter: ParsedPatchFunctionParameter,
        descriptors: List<DescriptorBinding>,
    ): HookParameter = with(parameter) {
        kspRequireNotNull(name) { "41.5" }
        kspRequireNotNull(type) { "42" }
        when {
            hasContextAnnotation -> {
                kspRequire(hookKind != LapisHookKind.Body) { "42.1" }
                if (contextDescriptorClassDeclaration?.isInstance(runtimeApi[ApiContext]) == false) {
                    if (contextDescriptorClassDeclaration.isError) {
                        throw InvalidStateSignal(true)
                    }
                    kspError { "42.2" }
                }
                kspRequireNotNull(contextDescriptorGenericClassDeclaration) { "42.3" }
                val contextDescriptor = descriptors
                    .find { it.parsed.classDeclaration.isSame(contextDescriptorGenericClassDeclaration) }
                    ?.validated
                kspRequire(contextDescriptor is MethodDescriptor && contextDescriptor == descriptor) { "42.4" }
                HookContextParameter(contextDescriptor)
            }

            hasTargetAnnotation -> {
                kspRequire(hookKind != LapisHookKind.Literal) { "43" }
                kspRequireNotNull(targetDescriptorClassDeclaration) { "43.1" }
                val targetDescriptor = descriptors
                    .find { it.parsed.classDeclaration.isSame(targetDescriptorClassDeclaration) }
                    ?.validated
                kspRequire(targetDescriptor is MethodDescriptor) { "43.2" }
                HookTargetParameter(targetDescriptor)
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

    private fun buildHierarchy(patchBindings: List<PatchBinding>): List<Patch> {
        patchBindings.forEach { patchBinding ->
            patchBindings
                .firstOrNull { it.parsed.classDeclaration.isSame(patchBinding.parsed.outerClassDeclaration) }
                ?.validated
                ?.innerPatches
                ?.add(patchBinding.validated)
        }
        return patchBindings
            .filter { it.parsed.outerClassDeclaration == null }
            .map { it.validated }
    }

    private inline fun KspSourceHolder.kspError(crossinline message: () -> String): Nothing {
        val message = message()
        logger.error(message, source)
        throw InvalidStateSignal()
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun KspSourceHolder.kspRequire(condition: Boolean, crossinline message: () -> String) {
        contract {
            returns() implies condition
        }
        if (!condition) {
            kspError(message = message)
        }
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun <T> KspSourceHolder.kspRequireNotNull(value: T?, crossinline message: () -> String): T {
        contract {
            returns() implies (value != null)
        }
        return value ?: kspError(message = message)
    }

    private class DescriptorBinding(
        val parsed: ParsedDescriptor,
        val validated: Descriptor,
    )

    private class PatchBinding(
        val parsed: ParsedPatch,
        val validated: Patch,
    )
}
