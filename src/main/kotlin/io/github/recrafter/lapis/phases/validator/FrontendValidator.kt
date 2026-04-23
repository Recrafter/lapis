package io.github.recrafter.lapis.phases.validator

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.recrafter.lapis.LapisLogger
import io.github.recrafter.lapis.Options
import io.github.recrafter.lapis.annotations.At
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.indexOfFirstOrNull
import io.github.recrafter.lapis.extensions.kp.KPBoolean
import io.github.recrafter.lapis.extensions.ks.*
import io.github.recrafter.lapis.phases.builtins.Builtin
import io.github.recrafter.lapis.phases.builtins.Builtins
import io.github.recrafter.lapis.phases.builtins.DescriptorWrapperBuiltin
import io.github.recrafter.lapis.phases.builtins.SimpleBuiltin
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.parser.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class FrontendValidator(
    private val logger: LapisLogger,
    private val options: Options,
    private val builtins: Builtins,
    private val types: KSTypes,
) {
    private val validSchemas: MutableMap<String, Schema> = mutableMapOf()

    private val validDescriptors: MutableMap<String, Descriptor> = mutableMapOf()
    private val invalidDescriptors: MutableList<String> = mutableListOf()

    fun validate(parserResult: ParserResult): ValidatorResult =
        ValidatorResult(
            schemas = parserResult.schemas.flatMap { rootSchema ->
                runOrNullOnSkip { validateSchema(rootSchema) } ?: emptyList()
            },
            patches = parserResult.patches.mapNotNull {
                runOrNullOnSkip { validatePatch(it) }
            },
        )

    private fun validateSchema(parsedSchema: ParsedSchema): List<Schema> = with(parsedSchema) {
        kspRequire(classDeclaration?.isValid == true) { "45" }
        kspRequire(targetClassDeclaration?.isValid == true) { "46" }
        kspRequireNotNull(targetBinaryName) { "47" }
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "48" }
        val qualifiedName = kspRequireNotNull(classDeclaration.qualifiedName?.asString()) { "49" }

        val isAccessPending = targetClassDeclaration.qualifiedName?.asString() != targetBinaryName
        val descriptors = if (isAccessPending) {
            emptyList()
        } else {
            parsedSchema.descriptors.mapNotNull { parsedDescriptor ->
                val descriptorQualifiedName = parsedDescriptor.classDeclaration.qualifiedName?.asString()
                    ?: return@mapNotNull null
                val validatedDescriptor = runOrNullOnSkip {
                    validateDescriptor(targetClassDeclaration, parsedDescriptor)
                }
                if (validatedDescriptor != null) {
                    validDescriptors[descriptorQualifiedName] = validatedDescriptor
                } else {
                    invalidDescriptors += descriptorQualifiedName
                }
                return@mapNotNull validatedDescriptor
            }
        }
        val schema = Schema(
            source = symbol,
            classDeclaration = classDeclaration,
            targetClassDeclaration = targetClassDeclaration,
            targetBinaryName = targetBinaryName,
            makePublic = hasAccess,
            removeFinal = parsedSchema.unfinal,
            descriptors = descriptors,
        )
        validSchemas[qualifiedName] = schema

        return buildList {
            add(schema)
            if (!isAccessPending) {
                addAll(nestedSchemas.flatMap {
                    runOrNullOnSkip { validateSchema(it) } ?: emptyList()
                })
            }
        }
    }

    private fun validateDescriptor(
        schemaTargetClassDeclaration: KSClassDeclaration,
        descriptor: ParsedDescriptor
    ): Descriptor = with(descriptor) {
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "89" }
        kspRequire(superClassDeclaration?.isValid == true) { "90" }
        if (hasAccessAnnotation) {
            kspRequire(options.accessWidenerConfigName != null || options.accessTransformerConfigName != null) { "94" }
        }
        val receiverType = schemaTargetClassDeclaration.starProjectedType
        if (superClassDeclaration.isBuiltin(SimpleBuiltin.Field)) {
            kspRequire(genericType is ParsedTypeDescriptorGenericType) { "99" }
            kspRequireNotNull(genericType.type) { "100" }
            if (callableReference !is InvisibleCallableReference) {
                kspRequire(callableReference is ParsedFieldDescriptorCallableReference) { "102" }
                kspRequire(
                    callableReference.receiverClassDeclaration?.isAssignableFrom(schemaTargetClassDeclaration) == true
                ) { "128" }
            }
            return FieldDescriptor(
                name = name,
                classDeclaration = classDeclaration,
                receiverType = receiverType,
                targetName = kspRequireNotNull(callableReference.name) { "108" },
                fieldType = genericType.type,
                arrayComponentType = genericType.arrayComponentType,
                isStatic = hasStaticAnnotation,
                makePublic = hasAccessAnnotation,
                removeFinal = unfinal,
            )
        }
        kspRequire(genericType is ParsedFunctionTypeDescriptorGenericType) { "116" }
        val parameters = genericType.parameters.map { parameter ->
            kspRequire(!parameter.type.isFunctionType) { "118" }
            FunctionTypeParameter(
                type = parameter.type,
                name = parameter.name,
            )
        }
        return when {
            superClassDeclaration.isBuiltin(SimpleBuiltin.Method) -> {
                if (callableReference !is InvisibleCallableReference) {
                    kspRequire(callableReference is ParsedMethodDescriptorCallableReference) { "127" }
                    kspRequire(
                        callableReference.receiverClassDeclaration
                            ?.isAssignableFrom(schemaTargetClassDeclaration) == true
                    ) { "128" }
                }
                if (!hasStaticAnnotation) {
                    kspRequireNotNull(genericType.receiverType) { "130" }
                }
                MethodDescriptor(
                    name = name,
                    classDeclaration = classDeclaration,
                    receiverType = receiverType,
                    returnType = genericType.returnType,
                    targetName = kspRequireNotNull(callableReference.name) { "137" },
                    parameters = parameters,
                    isStatic = hasStaticAnnotation,
                    makePublic = hasAccessAnnotation,
                    removeFinal = unfinal,
                )
            }

            superClassDeclaration.isBuiltin(SimpleBuiltin.Constructor) -> {
                kspRequire(callableReference == null) { "147" }
                kspRequire(!unfinal) { "149" }
                kspRequire(genericType.receiverType == null) { "150" }
                kspRequire(genericType.returnType?.declaration == schemaTargetClassDeclaration) { "150" }
                ConstructorDescriptor(
                    name = name,
                    classDeclaration = classDeclaration,
                    returnType = genericType.returnType,
                    parameters = parameters,
                    makePublic = hasAccessAnnotation,
                )
            }

            else -> skipWithError { "159" }
        }
    }

    private fun validatePatch(patch: ParsedPatch): Patch = with(patch) {
        kspRequireNotNull(name) { "164" }
        kspRequire(schemaClassDeclaration?.isValid == true) { "165" }
        kspRequire(superGenericClassDeclaration?.isValid == true) { "166" }
        kspRequireNotNull(side) { "167" }
        kspRequire(classDeclaration?.run { isAbstract() && !isInner && isClass } == true) { "168" }
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "169" }
        kspRequire(superClassDeclaration?.isBuiltin(SimpleBuiltin.Patch) == true) { "170" }
        val schema = validSchemas[schemaClassDeclaration.qualifiedName?.asString()]
        kspRequire(superGenericClassDeclaration == schema?.targetClassDeclaration) { "172" }
        val (parsedHookFunctions, parsedRegularFunctions) = functions.partition { it.hasHookAnnotation }
        return Patch(
            source = symbol,

            name = name,
            side = side,

            classDeclaration = classDeclaration,
            targetClassDeclaration = superGenericClassDeclaration,

            sharedProperties = properties.filter { it.isShared }.map { property ->
                with(property) {
                    SharedProperty(
                        name = name,
                        type = type,
                        isMutable = isMutable,
                    )
                }
            },
            sharedFunctions = parsedRegularFunctions.filter { it.isShared }.mapNotNull {
                runOrNullOnSkip { validateSharedFunction(it) }
            },
            hooks = parsedHookFunctions.mapNotNull {
                runOrNullOnSkip { validateHook(it) }
            },
        )
    }

    private fun validateSharedFunction(function: ParsedPatchFunction): SharedFunction = with(function) {
        SharedFunction(
            name = name,
            parameters = function.parameters.map {
                FunctionParameter(
                    name = kspRequireNotNull(it.name) { "205" },
                    type = kspRequireNotNull(it.type) { "206" },
                )
            },
            returnType = function.returnType,
        )
    }

    private fun validateHook(function: ParsedPatchFunction): DomainHook = with(function) {
        kspRequireNotNull(hookAt) { "214" }
        kspRequire(!function.hasTypeParameters) { "215" }
        val hookDescriptor = validateDescriptorReference(hookDescriptorClassDeclaration)
        kspRequire(hookDescriptor is InvokableDescriptor) { "217" }
        if (hookDescriptor.isStatic) {
            kspRequire(fromCompanionObject) { "219" }
        } else {
            kspRequire(!fromCompanionObject) { "221" }
        }
        val ordinals: (List<Int>) -> List<Int> = { validateOrdinals(it) }
        val parameters: () -> List<HookParameter> = {
            function.parameters.mapNotNull { parameter ->
                runOrNullOnSkip { validateHookParameter(parameter, function, hookAt, hookDescriptor) }
            }
        }
        when (hookAt) {
            At.Head -> {
                kspRequire(returnType == null) { "231" }
                when (hookDescriptor) {
                    is ConstructorDescriptor -> {
                        kspRequire(hasAtConstructorHeadAnnotation) { "234" }
                        ConstructorHeadHook(
                            name = name,
                            descriptor = hookDescriptor,
                            phase = kspRequireNotNull(atConstructorHeadPhase) { "238" },
                            parameters = parameters(),
                        )
                    }

                    is MethodDescriptor -> {
                        MethodHeadHook(
                            name = name,
                            descriptor = hookDescriptor,
                            parameters = parameters(),
                        )
                    }
                }
            }

            At.Body -> {
                kspRequire(hookDescriptor is MethodDescriptor) { "254" }
                kspRequire(returnType == hookDescriptor.returnType) { "255" }
                BodyHook(
                    name = name,
                    targetDescriptor = hookDescriptor,
                    returnType = returnType,
                    parameters = parameters(),
                )
            }

            At.Tail -> {
                kspRequire(returnType == null) { "265" }
                TailHook(
                    name = name,
                    descriptor = hookDescriptor,
                    parameters = parameters(),
                )
            }

            At.Local -> {
                kspRequire(hasAtLocalAnnotation) { "274" }
                kspRequireNotNull(atLocalOp) { "275" }
                kspRequireNotNull(atLocalType) { "276" }
                kspRequire(returnType == atLocalType) { "277" }
                LocalHook(
                    name = name,
                    descriptor = hookDescriptor,
                    type = atLocalType,
                    ordinals = ordinals(atLocalOpOrdinals),
                    local = validateLocal(atLocalExplicitOrdinal, atLocalExplicitName),
                    op = atLocalOp,
                    parameters = parameters(),
                )
            }

            At.Instanceof -> {
                kspRequire(hasAtInstanceofAnnotation) { "290" }
                kspRequire(atInstanceofTypeClassDeclaration?.isValid == true) { "291" }
                kspRequire(returnType?.toClassName()?.asIrTypeName() == KPBoolean.asIrTypeName()) { "292" }
                InstanceofHook(
                    name = name,
                    descriptor = hookDescriptor,
                    classDeclaration = atInstanceofTypeClassDeclaration,
                    returnType = returnType,
                    ordinals = ordinals(atInstanceofOrdinals),
                    parameters = parameters(),
                )
            }

            At.Return -> {
                kspRequire(hasAtReturnAnnotation) { "304" }
                kspRequire(returnType == hookDescriptor.returnType) { "305" }
                ReturnHook(
                    name = name,
                    descriptor = hookDescriptor,
                    type = returnType,
                    ordinals = ordinals(atReturnOrdinals),
                    parameters = parameters(),
                )
            }

            At.Literal -> {
                kspRequire(hasAtLiteralAnnotation) { "316" }
                val literal = validateLiteral(function)
                val type = literal.getType(types)
                if (literal !is NullLiteral) {
                    if (literal !is StringLiteral && literal !is ClassLiteral) {
                        kspRequire(returnType?.isMarkedNullable == false) { "323" }
                    }
                    kspRequire(returnType == type) { "325" }
                }
                LiteralHook(
                    name = name,
                    descriptor = hookDescriptor,
                    type = type,
                    literal = literal,
                    ordinals = ordinals(atLiteralOrdinals),
                    parameters = parameters(),
                )
            }

            At.Field -> {
                kspRequire(hasAtFieldAnnotation) { "338" }
                kspRequireNotNull(atFieldOp) { "339" }
                val targetDescriptor = validateDescriptorReference(atFieldDescriptorClassDeclaration)
                kspRequire(targetDescriptor is FieldDescriptor) { "341" }
                when (atFieldOp) {
                    Op.Get -> {
                        kspRequire(returnType?.makeNotNullable() == targetDescriptor.fieldType) { "344" }
                        FieldGetHook(
                            name = name,
                            descriptor = hookDescriptor,
                            type = targetDescriptor.fieldType,
                            ordinals = ordinals(atFieldOrdinals),
                            targetDescriptor = targetDescriptor,
                            parameters = parameters(),
                        )
                    }

                    Op.Set -> {
                        kspRequire(returnType == null) { "356" }
                        FieldSetHook(
                            name = name,
                            descriptor = hookDescriptor,
                            type = targetDescriptor.fieldType,
                            ordinals = ordinals(atFieldOrdinals),
                            targetDescriptor = targetDescriptor,
                            parameters = parameters(),
                        )
                    }
                }
            }

            At.Array -> {
                kspRequire(hasAtArrayAnnotation) { "370" }
                kspRequireNotNull(atArrayOp) { "371" }
                val targetDescriptor = validateDescriptorReference(atArrayDescriptorClassDeclaration)
                kspRequire(targetDescriptor is FieldDescriptor) { "373" }
                kspRequireNotNull(targetDescriptor.arrayComponentType) { "374" }
                when (atArrayOp) {
                    Op.Get -> kspRequire(returnType == targetDescriptor.arrayComponentType) { "376" }
                    Op.Set -> kspRequire(returnType == null) { "377" }
                }
                ArrayHook(
                    name = name,
                    descriptor = hookDescriptor,
                    op = atArrayOp,
                    type = targetDescriptor.fieldType,
                    componentType = targetDescriptor.arrayComponentType,
                    targetDescriptor = targetDescriptor,
                    ordinals = ordinals(atArrayOrdinals),
                    parameters = parameters(),
                )
            }

            At.Call -> {
                kspRequire(hasAtCallAnnotation) { "392" }
                val targetDescriptor = validateDescriptorReference(atCallDescriptorClassDeclaration)
                kspRequire(targetDescriptor is MethodDescriptor) { "394" }
                kspRequire(returnType?.makeNotNullable() == targetDescriptor.returnType) { "395" }
                CallHook(
                    name = name,
                    descriptor = hookDescriptor,
                    returnType = returnType,
                    targetDescriptor = targetDescriptor,
                    ordinals = ordinals(atCallOrdinals),
                    parameters = parameters(),
                )
            }
        }
    }

    private fun validateLiteral(function: ParsedPatchFunction): Literal = with(function) {
        kspRequireNotNull(
            listOfNotNull(
                atLiteralExplicitZero?.let { ZeroLiteral(atLiteralZeroConditions) },
                atLiteralExplicitInt?.let {
                    kspRequire(it != 0) { "417" }
                    IntLiteral(it)
                },
                atLiteralExplicitFloat?.let { FloatLiteral(it) },
                atLiteralExplicitLong?.let { LongLiteral(it) },
                atLiteralExplicitDouble?.let { DoubleLiteral(it) },
                atLiteralExplicitString?.let { StringLiteral(it) },
                atLiteralExplicitClassType?.let {
                    kspRequireNotNull(atLiteralExplicitClassDeclaration) { "425" }
                    ClassLiteral(atLiteralExplicitClassDeclaration)
                },
                atLiteralExplicitNull?.let { NullLiteral },
            ).singleOrNull()
        ) { "409" }
    }

    private fun SymbolSource.validateLocal(
        ordinal: Int?,
        explicitName: String?,
        fallbackName: String? = explicitName
    ): DomainLocal =
        kspRequireNotNull(
            when {
                ordinal != null -> ordinal.takeIf { explicitName == null }?.let {
                    kspRequire(it >= 0) { "438" }
                    PositionalLocal(it)
                }

                explicitName != null -> {
                    kspRequire(explicitName.isNotEmpty()) { "439" }
                    NamedLocal(explicitName)
                }

                fallbackName != null -> NamedLocal(fallbackName)
                else -> null
            }
        ) { "445" }

    private fun SymbolSource.validateOrdinals(ordinals: List<Int>): List<Int> {
        val invalidOrdinals = ordinals.filter { it < 0 }
        if (invalidOrdinals.isNotEmpty()) {
            invalidOrdinals.forEach {
                kspError { "Ordinal cannot be negative, but found: $it" }
            }
            skip()
        }
        return ordinals.toSortedSet().toList()
    }

    private fun SymbolSource.validateDescriptorReference(classDeclaration: KSClassDeclaration?): Descriptor {
        kspRequire(classDeclaration?.isValid == true) { "456" }
        val qualifiedName = classDeclaration.qualifiedName?.asString()
        if (invalidDescriptors.contains(qualifiedName)) {
            skipWithError { "459" }
        }
        return validDescriptors[qualifiedName] ?: lapisError("Failed to find descriptor by $qualifiedName")
    }

    private fun validateHookParameter(
        parameter: ParsedPatchFunctionParameter,
        function: ParsedPatchFunction,
        at: At,
        hookDescriptor: InvokableDescriptor,
    ): HookParameter =
        with(parameter) {
            kspRequireNotNull(name) { "471" }
            kspRequireNotNull(type) { "472" }
            kspRequire(!hasDefaultArgument) { "473" }
            when {
                hasOriginAnnotation -> when (at) {
                    At.Head, At.Tail -> skipWithError { "476" }

                    At.Body -> {
                        val originDescriptor = validateDescriptorReference(originGenericClassDeclaration)
                        kspRequire(originDescriptor is InvokableDescriptor) { "480" }
                        kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Body)) { "481" }
                        HookOriginDescriptorBodyWrapperParameter(originDescriptor)
                    }

                    At.Local -> {
                        kspRequire(type == function.returnType) { "486" }
                        HookOriginValueParameter
                    }

                    At.Instanceof -> {
                        kspRequire(type.declaration.isBuiltin(SimpleBuiltin.Instanceof)) { "491" }
                        HookOriginInstanceofParameter
                    }

                    At.Return -> {
                        kspRequireNotNull(hookDescriptor.returnType) { "496" }
                        kspRequire(type == hookDescriptor.returnType) { "497" }
                        HookOriginValueParameter
                    }

                    At.Literal -> {
                        val literal = validateLiteral(function)
                        kspRequire(literal !is NullLiteral) { "503" }
                        kspRequire(type == literal.getType(types)) { "504" }
                        HookOriginValueParameter
                    }

                    At.Field -> {
                        kspRequireNotNull(function.atFieldOp) { "509" }
                        val originDescriptor = validateDescriptorReference(originGenericClassDeclaration)
                        kspRequire(originDescriptor is FieldDescriptor) { "511" }
                        when (function.atFieldOp) {
                            Op.Get -> {
                                kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.FieldGet)) { "515" }
                                HookOriginDescriptorFieldGetWrapperParameter(originDescriptor)
                            }

                            Op.Set -> {
                                kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.FieldSet)) { "522" }
                                HookOriginDescriptorFieldSetWrapperParameter(originDescriptor)
                            }
                        }
                    }

                    At.Array -> {
                        kspRequireNotNull(function.atArrayOp) { "530" }
                        val originDescriptor = validateDescriptorReference(originGenericClassDeclaration)
                        kspRequire(originDescriptor is FieldDescriptor) { "532" }
                        kspRequireNotNull(originDescriptor.arrayComponentType) { "533" }
                        when (function.atArrayOp) {
                            Op.Get -> {
                                kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.ArrayGet)) { "537" }
                                HookOriginDescriptorArrayGetWrapperParameter(
                                    originDescriptor,
                                    originDescriptor.arrayComponentType
                                )
                            }

                            Op.Set -> {
                                kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.ArraySet)) { "544" }
                                HookOriginDescriptorArraySetWrapperParameter(
                                    originDescriptor,
                                    originDescriptor.arrayComponentType
                                )
                            }
                        }
                    }

                    At.Call -> {
                        val originDescriptor = validateDescriptorReference(originGenericClassDeclaration)
                        kspRequire(originDescriptor is InvokableDescriptor) { "553" }
                        kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Call)) { "554" }
                        HookOriginDescriptorCallWrapperParameter(originDescriptor)
                    }
                }

                hasCancelAnnotation -> {
                    kspRequire(at != At.Body) { "560" }
                    kspRequire(hookDescriptor is MethodDescriptor) { "561" }
                    val cancelDescriptor = validateDescriptorReference(cancelGenericClassDeclaration)
                    kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Cancel)) { "564" }
                    kspRequire(cancelDescriptor == hookDescriptor) { "565" }
                    HookCancelParameter(hookDescriptor)
                }

                hasOrdinalAnnotation -> {
                    kspRequire(type == types.int) { "570" }
                    kspRequire(function.hasOrdinals()) { "571" }
                    HookOrdinalParameter
                }

                hasParamAnnotation -> {
                    kspRequire(at != At.Body) { "576" }
                    if (explicitParamName != null) {
                        kspRequire(explicitParamName.trim().isNotEmpty()) { "566" }
                    }
                    val paramName = explicitParamName ?: name
                    val descriptorParameterIndex = hookDescriptor.parameters.indexOfFirstOrNull { it.name == paramName }
                    kspRequireNotNull(descriptorParameterIndex) { "580" }
                    val descriptorParameter = hookDescriptor.parameters[descriptorParameterIndex]
                    val (paramLocalType, isVar) = validateLocalType(type)
                    kspRequire(descriptorParameter.type == paramLocalType) { "582" }
                    HookParamLocalParameter(paramName, paramLocalType, descriptorParameterIndex, isVar)
                }

                hasLocalAnnotation -> {
                    kspRequire(at != At.Body) { "587" }
                    val (bodyLocalType, isVar) = validateLocalType(type)
                    HookBodyLocalParameter(
                        name,
                        bodyLocalType,
                        validateLocal(explicitLocalOrdinal, explicitLocalName, name),
                        isVar,
                    )
                }

                hasShareAnnotation -> {
                    kspRequire(type.declaration.isBuiltin(SimpleBuiltin.LocalVar)) { "588" }
                    val type = kspRequireNotNull(type.findGenericType()) { "535" }
                    if (explicitShareKey != null) {
                        kspRequire(explicitShareKey.trim().isNotEmpty()) { "566" }
                    }
                    HookShareLocalParameter(name, type, explicitShareKey ?: name, isShareExported)
                }

                else -> skipWithError { "592" }
            }
        }

    private fun ParsedPatchFunctionParameter.validateLocalType(type: KSType): Pair<KSType, Boolean> {
        val isVar = type.declaration.isBuiltin(SimpleBuiltin.LocalVar)
        val localType = if (isVar) {
            kspRequireNotNull(type.findGenericType()) { "535" }
        } else {
            kspRequireNotNull(type) { "588" }
        }
        return localType to isVar
    }

    private fun KSDeclaration.isBuiltin(builtin: Builtin<*>): Boolean =
        qualifiedName?.asString() == builtins[builtin].qualifiedName

    private inline fun SymbolSource.kspInfo(crossinline message: () -> String) {
        logger.info(message(), symbol)
    }

    private inline fun SymbolSource.kspWarn(crossinline message: () -> String) {
        logger.warn(message(), symbol)
    }

    private inline fun SymbolSource.kspError(crossinline message: () -> String) {
        logger.error(message(), symbol)
    }

    private fun skip(): Nothing = throw SkipSignal()

    private inline fun SymbolSource.skipWithError(crossinline message: () -> String): Nothing {
        kspError(message)
        skip()
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun SymbolSource.kspRequire(condition: Boolean, crossinline message: () -> String) {
        contract {
            returns() implies condition
        }
        if (!condition) {
            skipWithError(message = message)
        }
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun <T> SymbolSource.kspRequireNotNull(value: T?, crossinline message: () -> String): T {
        contract {
            returns() implies (value != null)
        }
        return value ?: skipWithError(message = message)
    }

    private fun <R> runOrNullOnSkip(block: () -> R): R? =
        try {
            block()
        } catch (_: SkipSignal) {
            null
        }

    private class SkipSignal : Exception()
}
