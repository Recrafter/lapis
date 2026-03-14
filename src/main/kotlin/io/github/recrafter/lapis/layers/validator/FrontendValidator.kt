package io.github.recrafter.lapis.layers.validator

import com.google.devtools.ksp.isAbstract
import io.github.recrafter.lapis.Hook
import io.github.recrafter.lapis.Options
import io.github.recrafter.lapis.annotations.LaLiteral
import io.github.recrafter.lapis.extensions.common.defaultValue
import io.github.recrafter.lapis.extensions.ksp.*
import io.github.recrafter.lapis.layers.generator.Builtin
import io.github.recrafter.lapis.layers.generator.Builtins
import io.github.recrafter.lapis.layers.JavaMemberKind
import io.github.recrafter.lapis.layers.parser.*
import org.spongepowered.asm.mixin.injection.At
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class FrontendValidator(
    private val logger: KSPLogger,
    private val options: Options,
    private val builtins: Builtins,
) {
    private val descriptorBindings: MutableList<DescriptorBinding> = mutableListOf()

    fun validate(parserResult: ParserResult): ValidatorResult =
        ValidatorResult(
            schemas = parserResult.schemas.flatMap { rootSchema ->
                runSignalCatching { validateSchema(rootSchema) } ?: emptyList()
            },
            patches = parserResult.patches.mapNotNull {
                runSignalCatching { validatePatch(it) }
            },
        )

    private fun validateSchema(schema: ParsedSchema): List<Schema> = with(schema) {
        kspRequireNotNull(classType) { "10.0" }
        kspRequireNotNull(targetClassType) { "10.1" }
        kspRequire(classType.typeParameters.isEmpty()) { "10.2" }
        widener?.let { path ->
            kspRequire(targetClassType.qualifiedName?.asString() == path) { "10.3" }
        }
        val validatedSchema = Schema(
            symbol = symbol,
            classType = classType,
            targetClassType = targetClassType,
            needAccess = hasWidener,
            needRemoveFinal = schema.isMarkedAsFinal,
            descriptors = schema.descriptors.mapNotNull { descriptor ->
                runSignalCatching {
                    validateDescriptor(targetClassType, descriptor)
                }?.also {
                    descriptorBindings += DescriptorBinding(descriptor, it)
                }
            },
        )
        return listOf(validatedSchema) + nestedSchemas.flatMap { nestedSchema ->
            runSignalCatching { validateSchema(nestedSchema) } ?: emptyList()
        }
    }

    private fun validateDescriptor(
        targetClassType: KSPClass,
        descriptor: ParsedDescriptor
    ): Descriptor = with(descriptor) {
        kspRequireNotNull(name) { "20.0" }
        kspRequireNotNull(classType) { "20.1" }
        kspRequire(classType.typeParameters.isEmpty()) { "20.2" }
        kspRequire(superClassType?.isInstance(builtins[Builtin.Descriptor]) == true) { "20.3" }
        kspRequire(isFunctionType) { "20.4" }
        kspRequire(isCallable) { "20.5" }
        val memberKind = kspRequireNotNull(memberKinds.singleOrNull()) { "20.6" }

        val receiverType = if (hasStaticAnnotation || memberKind == JavaMemberKind.CONSTRUCTOR) {
            targetClassType.asStarProjectedType()
        } else {
            kspRequireNotNull(receiverType) { "20.7" }
            kspRequire(!receiverType.isFunctionType) { "20.8" }
            receiverType
        }
        val parameters = parameters.map { parameter ->
            kspRequire(!parameter.type.isFunctionType) { "20.9" }
            FunctionTypeParameter(
                type = parameter.type,
                name = parameter.name,
            )
        }
        kspRequire(returnType == null || !returnType.isFunctionType) { "20.10" }
        if (hasAccessAnnotation) {
            kspRequire(options.accessWidener != null || options.accessTransformer != null) { "20.11" }
        }

        when (memberKind) {
            JavaMemberKind.CONSTRUCTOR -> {
                kspRequire(!hasStaticAnnotation) { "20.12" }
                ConstructorDescriptor(
                    name = name,
                    classType = classType,
                    ownerClassType = kspRequireNotNull(returnType) { "20.13" },
                    parameters = parameters,
                    needAccess = hasAccessAnnotation,
                    needRemoveFinal = isMarkedAsFinal,
                )
            }

            JavaMemberKind.METHOD -> {
                if (hasStaticAnnotation) {
                    kspRequire(!hasReceiver) { "20.14" }
                } else {
                    kspRequire(hasReceiver) { "20.15" }
                    kspRequire(functionTypeReceiverName == callableReceiverName) { "20.16" }
                }
                MethodDescriptor(
                    name = name,
                    classType = classType,
                    receiverType = receiverType,
                    returnType = returnType,
                    targetName = kspRequireNotNull(callableName) { "20.17" },
                    parameters = parameters,
                    isStatic = hasStaticAnnotation,
                    needAccess = hasAccessAnnotation,
                    needRemoveFinal = isMarkedAsFinal,
                )
            }

            JavaMemberKind.FIELD -> {
                if (hasStaticAnnotation) {
                    kspRequire(!hasReceiver) { "20.18" }
                } else {
                    kspRequire(hasReceiver) { "20.19" }
                    kspRequire(functionTypeReceiverName == callableReceiverName) { "20.20" }
                }
                FieldDescriptor(
                    name = name,
                    classType = classType,
                    receiverType = receiverType,
                    targetName = kspRequireNotNull(callableName) { "20.21" },
                    type = kspRequireNotNull(returnType) { "20.22" },
                    isStatic = hasStaticAnnotation,
                    needAccess = hasAccessAnnotation,
                    needRemoveFinal = isMarkedAsFinal,
                )
            }
        }
    }

    private fun validatePatch(patch: ParsedPatch): Patch = with(patch) {
        kspRequireNotNull(name) { "30.0" }
        kspRequireNotNull(targetClassType) { "30.1" }
        kspRequireNotNull(side) { "30.2" }
        kspRequire(classType?.run { isAbstract() && !isInner && isClass } == true) { "30.3" }
        kspRequire(classType.typeParameters.isEmpty()) { "30.4" }
        kspRequire(superClassType?.isInstance(builtins[Builtin.Patch]) == true) { "30.5" }
        kspRequire(superGenericClassType.isSame(targetClassType)) { "30.6" }
        return Patch(
            symbol = symbol,

            name = name,
            classType = classType,
            targetClassType = targetClassType,
            side = side,
            sharedProperties = properties.filter { it.isShared }.map { property ->
                with(property) {
                    SharedProperty(
                        name = name,
                        type = type,
                        isMutable = isMutable,
                    )
                }
            },
            sharedFunctions = functions.filter { !it.hasHookAnnotation && it.isShared }.mapNotNull {
                runSignalCatching { validateSharedFunction(it) }
            },
            hooks = functions.filter { it.hasHookAnnotation }.mapNotNull {
                runSignalCatching { validateHook(it) }
            },
        )
    }

    private fun validateSharedFunction(function: ParsedPatchFunction): SharedFunction = with(function) {
        SharedFunction(
            name = name,
            parameters = function.parameters.map {
                FunctionParameter(
                    name = kspRequireNotNull(it.name) { "40.0" },
                    type = kspRequireNotNull(it.type) { "40.1" },
                )
            },
            returnType = function.returnType,
        )
    }

    private fun validateHook(function: ParsedPatchFunction): HookModel = with(function) {
        kspRequireNotNull(hookKind) { "50.0" }
        kspRequire(!function.hasTypeParameters) { "50.1" }
        val descriptor = descriptorBindings
            .find { it.parsedDescriptor.classType.isSame(hookDescriptorClassType) }
            ?.validatedDescriptor
        kspRequire(descriptor is InvokableDescriptor) { "50.2" }
        val parameters = parameters.map {
            validateHookParameter(hookKind, descriptor, it)
        }
        val ordinalParameters = parameters.filterIsInstance<HookOrdinalParameter>()
        if (ordinalParameters.size > 1) {
            kspError { "50.3" }
        }
        val ordinals = ordinalParameters.singleOrNull()?.indices.orEmpty().ifEmpty {
            listOf(At::ordinal.defaultValue)
        }
        when (hookKind) {
            Hook.Body -> {
                kspRequire(descriptor is MethodDescriptor) { "50.4" }
                kspRequire(returnType.isSame(descriptor.returnType)) { "50.5" }
                BodyHook(
                    name = name,
                    descriptor = descriptor,
                    returnType = returnType,
                    parameters = parameters,
                )
            }

            Hook.Call -> {
                val target = parameters.filterIsInstance<HookTargetParameter>().singleOrNull()?.descriptor
                kspRequire(target is MethodDescriptor) { "50.6" }
                kspRequire(returnType?.makeNotNullable().isSame(target.returnType)) { "50.7" }
                CallHook(
                    name = name,
                    descriptor = descriptor,
                    returnType = returnType,
                    methodDescriptor = target,
                    ordinals = ordinals,
                    parameters = parameters,
                )
            }

            Hook.Literal -> {
                val literalParameter = parameters.filterIsInstance<HookLiteralParameter>().singleOrNull()
                kspRequireNotNull(literalParameter) { "50.8" }
                val literalType = literalParameter.type
                kspRequire(literalType.isSame(returnType)) { "50.9" }
                kspRequire(!literalType.isMarkedNullable) { "50.10" }
                val literalTypeClass = when (literalParameter.typeName) {
                    LaLiteral::int.name -> Int::class
                    LaLiteral::float.name -> Float::class
                    LaLiteral::long.name -> Long::class
                    LaLiteral::double.name -> Double::class
                    LaLiteral::string.name -> String::class
                    else -> kspError { "50.11" }
                }
                kspRequire(literalType.declaration.isInstance(literalTypeClass)) { "50.12" }
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

            Hook.FieldGet -> {
                val target = parameters.filterIsInstance<HookTargetParameter>().singleOrNull()?.descriptor
                kspRequire(target is FieldDescriptor) { "50.13" }
                kspRequire(returnType?.makeNotNullable().isSame(target.type)) { "50.14" }
                FieldGetHook(
                    name = name,
                    descriptor = descriptor,
                    type = target.type,
                    ordinals = ordinals,
                    fieldDescriptor = target,
                    parameters = parameters,
                )
            }


            Hook.FieldSet -> {
                val target = parameters.filterIsInstance<HookTargetParameter>().singleOrNull()?.descriptor
                kspRequire(target is FieldDescriptor) { "50.15" }
                kspRequire(returnType == null) { "50.16" }
                FieldSetHook(
                    name = name,
                    descriptor = descriptor,
                    type = target.type,
                    ordinals = ordinals,
                    fieldDescriptor = target,
                    parameters = parameters,
                )
            }

            else -> TODO("[LAPIS] The kind ${hookKind.name} is not implemented.")
        }
    }

    private fun validateHookParameter(
        hookKind: Hook,
        descriptor: InvokableDescriptor,
        parameter: ParsedPatchFunctionParameter,
    ): HookParameter = with(parameter) {
        kspRequireNotNull(name) { "60.0" }
        kspRequireNotNull(type) { "60.1" }
        when {
            hasTargetAnnotation -> {
                kspRequire(hookKind != Hook.Literal) { "60.2" }
                val targetDescriptor = descriptorBindings
                    .find { it.parsedDescriptor.classType.isSame(targetDescriptorGenericClassType) }
                    ?.validatedDescriptor
                when {
                    targetDescriptorClassType?.isInstance(builtins[Builtin.Callable]) == true -> {
                        kspRequire(targetDescriptor is InvokableDescriptor) { "60.3" }
                        HookCallableTargetParameter(targetDescriptor)
                    }

                    targetDescriptorClassType?.isInstance(builtins[Builtin.Getter]) == true -> {
                        kspRequire(targetDescriptor is FieldDescriptor) { "60.4" }
                        HookGetterTargetParameter(targetDescriptor)
                    }

                    targetDescriptorClassType?.isInstance(builtins[Builtin.Setter]) == true -> {
                        kspRequire(targetDescriptor is FieldDescriptor) { "60.5" }
                        HookSetterTargetParameter(targetDescriptor)
                    }

                    else -> kspError { "60.6" }
                }
            }

            hasContextAnnotation -> {
                kspRequire(hookKind != Hook.Body) { "60.7" }
                kspRequire(contextDescriptorClassType?.isInstance(builtins[Builtin.Context]) == true) { "60.8" }
                kspRequireNotNull(contextDescriptorGenericClassType) { "60.9" }
                val contextDescriptor = descriptorBindings
                    .find { it.parsedDescriptor.classType.isSame(contextDescriptorGenericClassType) }
                    ?.validatedDescriptor
                kspRequire(contextDescriptor == descriptor) { "60.10" }
                HookContextParameter(descriptor)
            }

            hasLiteralAnnotation -> {
                kspRequire(hookKind == Hook.Literal) { "60.11" }
                HookLiteralParameter(
                    type = kspRequireNotNull(literalType) { "60.12" },
                    typeName = kspRequireNotNull(literalTypeName) { "60.13" },
                    value = kspRequireNotNull(literalValue) { "60.14" },
                )
            }

            hasOrdinalAnnotation -> {
                kspRequire(hookKind != Hook.Body) { "60.15" }
                kspRequire(ordinalIndices.isNotEmpty()) { "60.16" }
                ordinalIndices.forEach {
                    kspRequire(it >= 0) { "60.17" }
                }
                HookOrdinalParameter(ordinalIndices)
            }

            hasLocalAnnotation -> {
                kspRequire(hookKind != Hook.Body) { "60.18" }
                kspRequireNotNull(type) { "60.19" }
                kspRequireNotNull(localOrdinal) { "60.20" }
                kspRequire(localOrdinal >= 0) { "60.21" }
                HookLocalParameter(name, type, localOrdinal)
            }

            else -> kspError { "60.22" }
        }
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

    @Suppress("UnusedReceiverParameter")
    private fun <R> FrontendValidator.runSignalCatching(block: () -> R): R? =
        try {
            block()
        } catch (_: ValidationErrorSignal) {
            null
        }

    private class DescriptorBinding(
        val parsedDescriptor: ParsedDescriptor,
        val validatedDescriptor: Descriptor,
    )
}
