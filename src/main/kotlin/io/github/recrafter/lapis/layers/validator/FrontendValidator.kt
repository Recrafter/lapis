package io.github.recrafter.lapis.layers.validator

import com.google.devtools.ksp.isAbstract
import io.github.recrafter.lapis.Hook
import io.github.recrafter.lapis.Options
import io.github.recrafter.lapis.annotations.LaLiteral
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
    private val options: Options,
    private val builtins: Builtins
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

    private fun validateSchema(schema: ParsedSchema, parentWidener: String? = null): List<Schema> = with(schema) {
        kspRequireNotNull(classType) { "0.0" }
        kspRequire(classType.typeParameters.isEmpty()) { "0.3" }
        val finalWidener = when {
            parentWidener != null && widener != null -> {
                parentWidener + "$" + widener.removePrefix(".")
            }

            widener != null -> widener.replace('/', '.')
            targetClassType != null -> targetClassType.qualifiedName?.asString()
            else -> parentWidener
        }
        kspRequireNotNull(finalWidener) { "0.32" }
        val validatedSchema = Schema(
            symbol = symbol,
            classType = classType,
            targetClassType = targetClassType,
            needAccess = schema.widener != null,
            needRemoveFinal = schema.isMarkedAsFinal,
            widener = finalWidener,
            descriptors = schema.descriptors.mapNotNull { descriptor ->
                kspRequireNotNull(targetClassType) { "0.33" }
                runSignalCatching {
                    validateDescriptor(targetClassType, descriptor)
                }?.also {
                    descriptorBindings += DescriptorBinding(descriptor, it)
                }
            },
        )
        return listOf(validatedSchema) + nestedSchemas.flatMap { nestedSchema ->
            runSignalCatching { validateSchema(nestedSchema, finalWidener) } ?: emptyList()
        }
    }

    private fun validateDescriptor(
        targetClassType: KSPClass,
        descriptor: ParsedDescriptor
    ): Descriptor = with(descriptor) {
        kspRequireNotNull(name) { "0.4" }
        kspRequireNotNull(classType) { "0.5" }
        kspRequire(classType.typeParameters.isEmpty()) { "0.75" }
        kspRequire(superClassType?.isInstance(builtins[Builtin.Descriptor]) == true) { "2" }
        kspRequire(isFunctionType) { "3" }
        kspRequire(isCallable) { "4" }
        val memberKind = kspRequireNotNull(memberKinds.singleOrNull()) { "9" }

        val receiverType = if (hasStaticAnnotation || memberKind == JavaMemberKind.CONSTRUCTOR) {
            targetClassType.asStarProjectedType()
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
        if (hasAccessAnnotation) {
            kspRequire(options.accessWidener != null || options.accessTransformer != null) { "8.6" }
        }

        when (memberKind) {
            JavaMemberKind.CONSTRUCTOR -> {
                kspRequire(!hasStaticAnnotation) { "10" }
                ConstructorDescriptor(
                    name = name,
                    classType = classType,
                    ownerClassType = kspRequireNotNull(returnType) { "13.5" },
                    parameters = parameters,
                    needAccess = hasAccessAnnotation,
                    needRemoveFinal = isMarkedAsFinal,
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
                    name = name,
                    classType = classType,
                    receiverType = receiverType,
                    returnType = returnType,
                    targetName = kspRequireNotNull(callableName) { "12" },
                    parameters = parameters,
                    isStatic = hasStaticAnnotation,
                    needAccess = hasAccessAnnotation,
                    needRemoveFinal = isMarkedAsFinal,
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
                    name = name,
                    classType = classType,
                    receiverType = receiverType,
                    targetName = kspRequireNotNull(callableName) { "16" },
                    type = kspRequireNotNull(returnType) { "16.5" },
                    isStatic = hasStaticAnnotation,
                    needAccess = hasAccessAnnotation,
                    needRemoveFinal = isMarkedAsFinal,
                )
            }
        }
    }

    private fun validatePatch(patch: ParsedPatch): Patch = with(patch) {
        kspRequireNotNull(name) { "20" }
        kspRequireNotNull(targetClassType) { "20.5" }
        kspRequireNotNull(side) { "21" }
        kspRequire(classType?.run { isAbstract() && !isInner && isClass } == true) { "22" }
        kspRequire(classType.typeParameters.isEmpty()) { "22.25" }
        kspRequire(superClassType?.isInstance(builtins[Builtin.Patch]) == true) { "23" }
        kspRequire(superGenericClassType.isSame(targetClassType)) { "24" }
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
                    name = kspRequireNotNull(it.name) { "39" },
                    type = kspRequireNotNull(it.type) { "40" },
                )
            },
            returnType = function.returnType,
        )
    }

    private fun validateHook(function: ParsedPatchFunction): HookModel = with(function) {
        kspRequireNotNull(hookKind) { "34" }
        kspRequire(!function.hasTypeParameters) { "34.25" }
        val descriptor = descriptorBindings
            .find { it.parsedDescriptor.classType.isSame(hookDescriptorClassType) }
            ?.validatedDescriptor
        kspRequire(descriptor is InvokableDescriptor) { "35" }
        val parameters = parameters.map {
            validateHookParameter(hookKind, descriptor, it)
        }
        val ordinalParameters = parameters.filterIsInstance<HookOrdinalParameter>()
        if (ordinalParameters.size > 1) {
            kspError { "35.1" }
        }
        val ordinals = ordinalParameters.singleOrNull()?.indices.orEmpty().ifEmpty {
            listOf(At::ordinal.defaultValue)
        }
        when (hookKind) {
            Hook.Body -> {
                kspRequire(descriptor is MethodDescriptor) { "35.2" }
                kspRequire(returnType.isSame(descriptor.returnType)) { "35.3" }
                BodyHook(
                    name = name,
                    descriptor = descriptor,
                    returnType = returnType,
                    parameters = parameters,
                )
            }

            Hook.Call -> {
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

            Hook.Literal -> {
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

            Hook.FieldGet -> {
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


            Hook.FieldSet -> {
                val target = parameters.filterIsInstance<HookTargetParameter>().singleOrNull()?.descriptor
                kspRequire(target is FieldDescriptor) { "38.6" }
                kspRequire(returnType == null) { "38.7" }
                FieldSetHook(
                    name = name,
                    descriptor = descriptor,
                    type = target.type,
                    ordinals = ordinals,
                    fieldDescriptor = target,
                    parameters = parameters,
                )
            }

            else -> TODO("The kind ${hookKind.name} is not implemented.")
        }
    }

    private fun validateHookParameter(
        hookKind: Hook,
        descriptor: InvokableDescriptor,
        parameter: ParsedPatchFunctionParameter,
    ): HookParameter = with(parameter) {
        kspRequireNotNull(name) { "41.5" }
        kspRequireNotNull(type) { "42" }
        when {
            hasTargetAnnotation -> {
                kspRequire(hookKind != Hook.Literal) { "42.1" }
                val targetDescriptor = descriptorBindings
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
                kspRequire(hookKind != Hook.Body) { "43.1" }
                kspRequire(contextDescriptorClassType?.isInstance(builtins[Builtin.Context]) == true) { "43.2" }
                kspRequireNotNull(contextDescriptorGenericClassType) { "43.3" }
                val contextDescriptor = descriptorBindings
                    .find { it.parsedDescriptor.classType.isSame(contextDescriptorGenericClassType) }
                    ?.validatedDescriptor
                kspRequire(contextDescriptor == descriptor) { "43.4" }
                HookContextParameter(descriptor)
            }

            hasLiteralAnnotation -> {
                kspRequire(hookKind == Hook.Literal) { "44" }
                HookLiteralParameter(
                    type = kspRequireNotNull(literalType) { "45" },
                    typeName = kspRequireNotNull(literalTypeName) { "45.5" },
                    value = kspRequireNotNull(literalValue) { "46" },
                )
            }

            hasOrdinalAnnotation -> {
                kspRequire(hookKind != Hook.Body) { "47" }
                kspRequire(ordinalIndices.isNotEmpty()) { "49" }
                ordinalIndices.forEach {
                    kspRequire(it >= 0) { "49.1" }
                }
                HookOrdinalParameter(ordinalIndices)
            }

            hasLocalAnnotation -> {
                kspRequire(hookKind != Hook.Body) { "50" }
                kspRequireNotNull(type) { "50.1" }
                kspRequireNotNull(localOrdinal) { "51" }
                kspRequire(localOrdinal >= 0) { "51.1" }
                HookLocalParameter(name, type, localOrdinal)
            }

            else -> kspError { "52" }
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
