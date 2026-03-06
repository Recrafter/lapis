package io.github.recrafter.lapis.layers.validator

import com.google.devtools.ksp.isAbstract
import io.github.recrafter.lapis.annotations.LaLiteral
import io.github.recrafter.lapis.annotations.enums.LapisHookKind
import io.github.recrafter.lapis.api.LapisContext
import io.github.recrafter.lapis.api.LapisDescriptor
import io.github.recrafter.lapis.api.LapisPatch
import io.github.recrafter.lapis.extensions.common.getAnnotationDefaultIntValue
import io.github.recrafter.lapis.extensions.ksp.KspLogger
import io.github.recrafter.lapis.extensions.ksp.isClass
import io.github.recrafter.lapis.extensions.ksp.isInner
import io.github.recrafter.lapis.extensions.ksp.isInstance
import io.github.recrafter.lapis.layers.parser.*
import io.github.recrafter.lapis.utils.MemberKind
import org.spongepowered.asm.mixin.injection.At
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class FrontendValidator(private val logger: KspLogger) {

    fun validate(parserResult: ParserResult): ValidatorResult {
        val descriptorBindings = parserResult.descriptorContainers.flatMap { container ->
            container.descriptors.mapNotNull { parsed ->
                val validated = try {
                    validateDescriptor(container, parsed)
                } catch (_: ValidationException) {
                    return@mapNotNull null
                }
                DescriptorBinding(parsed, validated)
            }
        }
        val patchBindings = parserResult.patches.mapNotNull { parsed ->
            val validated = try {
                validatePatch(parsed, descriptorBindings)
            } catch (_: ValidationException) {
                return@mapNotNull null
            }
            PatchBinding(parsed, validated)
        }
        return ValidatorResult(
            descriptors = descriptorBindings.map { it.validated },
            rootPatches = buildHierarchy(patchBindings),
        )
    }

    private fun validateDescriptor(
        container: ParsedDescriptorContainer,
        descriptor: ParsedDescriptor,
    ): Descriptor = with(descriptor) {
        kspRequireNotNull(container.classDeclaration) { "0" }
        kspRequireNotNull(classDeclaration) { "0.5" }
        kspRequire(targetClassDeclaration == container.targetClassDeclaration) { "1" }
        kspRequire(superClassDeclaration?.isInstance<LapisDescriptor<*>>() == true) { "2" }
        kspRequire(isFunctionType) { "3" }
        kspRequire(isCallable) { "4" }

        val receiverType = if (hasStaticAnnotation) {
            kspRequireNotNull(targetClassDeclaration?.asStarProjectedType()) { "5" }
        } else {
            kspRequireNotNull(receiverType) { "6" }
        }
        val parameters = parameters.map { parameter ->
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

                    containerClassDeclaration = container.classDeclaration,
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

                    containerClassDeclaration = container.classDeclaration,
                    classDeclaration = classDeclaration,
                    receiverType = receiverType,
                    returnType = returnType,
                    name = kspRequireNotNull(callableName) { "12" },
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

                    containerClassDeclaration = container.classDeclaration,
                    classDeclaration = classDeclaration,
                    receiverType = receiverType,
                    name = kspRequireNotNull(callableName) { "16" },
                    type = kspRequireNotNull(returnType) { "16.5" },
                )
            }
        }
    }

    private fun validatePatch(
        patch: ParsedPatch,
        descriptorBindings: List<DescriptorBinding>
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

        kspRequire(superClassDeclaration?.isInstance<LapisPatch<*>>() == true) { "23" }
        kspRequire(superGenericClassDeclaration == targetClassDeclaration) { "24" }

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
                    val method = descriptorBindings
                        .find { it.parsed.classDeclaration == hookMethodDescriptorClassDeclaration }
                        ?.validated
                    kspRequire(method is MethodDescriptor) { "35" }
                    val parameters = parameters.map {
                        validateHookParameter(hookKind, method, it, descriptorBindings)
                    }
                    val ordinalParameters = parameters.filterIsInstance<HookOrdinalParameter>()
                    kspRequire(ordinalParameters.size < 2) { "35.1" }
                    val ordinals = ordinalParameters.singleOrNull()?.indices.orEmpty().ifEmpty {
                        listOf(getAnnotationDefaultIntValue(At::ordinal))
                    }
                    hooks += when (hookKind) {
                        LapisHookKind.Body -> {
                            kspRequire(returnType == method.returnType) { "35.2" }
                            MethodBodyHook(
                                source = source,

                                name = name,
                                method = method,
                                returnType = returnType,
                                parameters = parameters,
                            )
                        }

                        LapisHookKind.Call -> {
                            val target = parameters.filterIsInstance<HookTargetParameter>().singleOrNull()?.descriptor
                            kspRequireNotNull(target) { "36" }
                            kspRequire(returnType?.makeNotNullable() == target.returnType) { "37" }
                            InvokeMethodHook(
                                source = source,

                                name = name,
                                method = method,
                                returnType = returnType,
                                target = target,
                                ordinals = ordinals,
                                parameters = parameters,
                            )
                        }

                        LapisHookKind.Literal -> {
                            val literalParameter = parameters.filterIsInstance<HookLiteralParameter>().singleOrNull()
                            kspRequireNotNull(literalParameter) { "38" }
                            val literalType = literalParameter.type
                            kspRequire(literalType == returnType) { "38.1" }
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
                                method = method,
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
        method: MethodDescriptor,
        parameter: ParsedPatchFunctionParameter,
        descriptors: List<DescriptorBinding>,
    ): HookParameter = with(parameter) {
        kspRequireNotNull(name) { "41.5" }
        kspRequireNotNull(type) { "42" }
        when {
            hasContextAnnotation -> {
                kspRequire(hookKind != LapisHookKind.Body) { "42.1" }
                kspRequire(contextDescriptorClassDeclaration?.isInstance<LapisContext<*>>() == true) { "42.2" }
                kspRequireNotNull(contextDescriptorGenericClassDeclaration) { "42.3" }
                val descriptor = descriptors
                    .find { it.parsed.classDeclaration == contextDescriptorGenericClassDeclaration }
                    ?.validated
                kspRequire(descriptor is MethodDescriptor && descriptor == method) { "42.4" }
                HookContextParameter(descriptor)
            }

            hasTargetAnnotation -> {
                kspRequire(hookKind != LapisHookKind.Literal) { "43" }
                kspRequireNotNull(targetDescriptorClassDeclaration) { "43.1" }
                val descriptor = descriptors
                    .find { it.parsed.classDeclaration == targetDescriptorClassDeclaration }
                    ?.validated
                kspRequire(descriptor is MethodDescriptor) { "43.2" }
                HookTargetParameter(descriptor)
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
                .firstOrNull { it.parsed.classDeclaration == patchBinding.parsed.outerClassDeclaration }
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
        throw ValidationException()
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
