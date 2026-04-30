package io.github.recrafter.lapis.phases.validator

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
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
import io.github.recrafter.lapis.phases.common.JvmClassName
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
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "48" }
        kspRequireNotNull(originJvmClassName) { "41" }
        kspRequire(originClassDeclaration?.isValid == true) { "46" }
        kspRequireNotNull(
            listOf(
                hasSchemaAnnotation,
                hasInnerSchemaAnnotation,
                hasLocalSchemaAnnotation,
                hasAnonymousSchemaAnnotation,
            ).singleOrNull { it }
        ) { "47" }
        if (hasAccessAnnotation) {
            kspRequire(isAccessible) { "49" }
        }
        val qualifiedName = kspRequireNotNull(classDeclaration.qualifiedName?.asString()) { "49" }
        val descriptors = parsedSchema.descriptors.mapNotNull { parsedDescriptor ->
            val descriptorQualifiedName = parsedDescriptor.classDeclaration.qualifiedName?.asString()
                ?: return@mapNotNull null
            val validatedDescriptor = runOrNullOnSkip {
                validateDescriptor(parsedDescriptor, originClassDeclaration, originJvmClassName, isAccessible)
            }
            if (validatedDescriptor != null) {
                validDescriptors[descriptorQualifiedName] = validatedDescriptor
            } else {
                invalidDescriptors += descriptorQualifiedName
            }
            return@mapNotNull validatedDescriptor
        }
        val schema = Schema(
            source = symbol,
            classDeclaration = classDeclaration,
            originJvmClassName = originJvmClassName,
            originClassDeclaration = originClassDeclaration,
            isAccessible = isAccessible,
            makePublic = hasAccessAnnotation,
            removeFinal = parsedSchema.unfinal,
            descriptors = descriptors,
        )
        validSchemas[qualifiedName] = schema
        return buildList {
            add(schema)
            addAll(nestedSchemas.flatMap {
                runOrNullOnSkip { validateSchema(it) } ?: emptyList()
            })
        }
    }

    private fun validateDescriptor(
        descriptor: ParsedDescriptor,
        schemaOriginClassDeclaration: KSClassDeclaration,
        schemaOriginJvmClassName: JvmClassName,
        isAccessibleSchema: Boolean,
    ): Descriptor = with(descriptor) {
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "89" }
        kspRequire(superClassDeclaration?.isValid == true) { "90" }
        if (hasAccessAnnotation) {
            kspRequire(isAccessibleSchema) { "91" }
            kspRequire(options.accessWidenerConfigName != null || options.accessTransformerConfigName != null) { "94" }
        }
        val mappingName = if (mappingName != null) {
            kspRequire(mappingName.isNotEmpty()) { "439" }
            mappingName
        } else {
            name
        }
        val receiverType = schemaOriginClassDeclaration.starProjectedType
        if (superClassDeclaration.isBuiltin(SimpleBuiltin.Field)) {
            kspRequire(genericType is ParsedTypeDescriptorGenericType) { "99" }
            kspRequireNotNull(genericType.type) { "100" }
            return FieldDescriptor(
                name = name,
                classDeclaration = classDeclaration,
                receiverType = receiverType,
                inaccessibleReceiverJvmClassName = if (isAccessibleSchema) null else schemaOriginJvmClassName,
                mappingName = mappingName,
                fieldType = genericType.type,
                arrayComponentType = genericType.arrayComponentType,
                isStatic = hasStaticAnnotation,
                makePublic = hasAccessAnnotation,
                removeFinal = unfinal,
            )
        }
        kspRequire(genericType is ParsedFunctionTypeDescriptorGenericType) { "116" }
        kspRequire(genericType.receiverType == null) { "150" }
        val parameters = genericType.parameters.map { parameter ->
            kspRequire(!parameter.type.isFunctionType) { "118" }
            FunctionTypeParameter(
                type = parameter.type,
                name = parameter.name,
            )
        }
        return when {
            superClassDeclaration.isBuiltin(SimpleBuiltin.Method) -> {
                MethodDescriptor(
                    name = name,
                    classDeclaration = classDeclaration,
                    receiverType = receiverType,
                    inaccessibleReceiverJvmClassName = if (isAccessibleSchema) null else schemaOriginJvmClassName,
                    returnType = genericType.returnType,
                    mappingName = mappingName,
                    parameters = parameters,
                    isStatic = hasStaticAnnotation,
                    makePublic = hasAccessAnnotation,
                    removeFinal = unfinal,
                )
            }

            superClassDeclaration.isBuiltin(SimpleBuiltin.Constructor) -> {
                kspRequire(!unfinal) { "149" }
                kspRequire(genericType.returnType == null) { "150" }
                kspRequire(!hasMappingNameAnnotation) { "151" }
                ConstructorDescriptor(
                    name = name,
                    classDeclaration = classDeclaration,
                    returnType = receiverType,
                    parameters = parameters,
                    makePublic = hasAccessAnnotation,
                )
            }

            else -> skipWithError { "159" }
        }
    }

    private fun validatePatch(patch: ParsedPatch): Patch = with(patch) {
        kspRequireNotNull(name) { "164" }
        kspRequireNotNull(side) { "167" }
        kspRequireNotNull(initStrategy) { "167" }
        kspRequire(classDeclaration?.isValid == true) { "162" }
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "169" }
        kspRequire(schemaClassDeclaration?.isValid == true) { "165" }
        val schema = validSchemas[schemaClassDeclaration.qualifiedName?.asString()]
        kspRequireNotNull(schema) { "172" }
        val constructor = kspRequireNotNull(patch.constructors.singleOrNull()) { "111" }
        val (parsedHookFunctions, parsedRegularFunctions) = functions.partition { it.hasHookAnnotation }
        val constructorParameters = constructor.parameters.mapNotNull {
            runOrNullOnSkip { validatePatchConstructorParameter(it, schema) }
        }
        val sharedProperties = properties.filter { it.isShared }.mapNotNull {
            runOrNullOnSkip { validatePatchSharedProperty(it, schema) }
        }
        val sharedFunctions = parsedRegularFunctions.filter { it.isShared }.mapNotNull {
            runOrNullOnSkip { validatePatchSharedFunction(it, schema) }
        }
        val hooks = parsedHookFunctions.mapNotNull {
            runOrNullOnSkip { validatePatchHook(it) }
        }
        val isObject = constructorParameters.isEmpty() && sharedProperties.isEmpty() && sharedFunctions.isEmpty()
            && hooks.all { it.descriptor.isStatic }
        if (!isObject) {
            kspRequire(classDeclaration.run { isAbstract() && !isInner && isClass }) { "168" }
        }
        return Patch(
            source = symbol,

            name = name,
            side = side,
            implInitStrategy = initStrategy,
            isObject = isObject,

            classDeclaration = classDeclaration,

            schema = schema,

            constructorParameters = constructorParameters,
            sharedProperties = sharedProperties,
            sharedFunctions = sharedFunctions,
            hooks = hooks,
        )
    }

    private fun validatePatchConstructorParameter(
        parameter: ParsedPatchConstructorParameter,
        schema: Schema,
    ): PatchConstructorParameter = with(parameter) {
        when {
            hasOriginAnnotation -> {
                val instanceClassDeclaration = type.toClassDeclaration()
                kspRequire(instanceClassDeclaration == schema.originClassDeclaration) { "172" }
                kspRequire(type.arguments.none { it.variance != Variance.STAR }) { "213" }
                PatchConstructorOriginParameter
            }

            else -> skipWithError { "222" }
        }
    }

    private fun validatePatchSharedProperty(
        property: ParsedPatchProperty,
        schema: Schema,
    ): PatchSharedProperty = with(property) {
        kspRequire(schema.isAccessible) { "208" }
        PatchSharedProperty(
            name = name,
            type = type,
            isMutable = isMutable,
        )
    }

    private fun validatePatchSharedFunction(
        function: ParsedPatchFunction,
        schema: Schema,
    ): PatchSharedFunction = with(function) {
        kspRequire(schema.isAccessible) { "208" }
        PatchSharedFunction(
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

    private fun validatePatchHook(function: ParsedPatchFunction): PatchHook = with(function) {
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
                atLiteralExplicitFloat?.let(::FloatLiteral),
                atLiteralExplicitLong?.let(::LongLiteral),
                atLiteralExplicitDouble?.let(::DoubleLiteral),
                atLiteralExplicitString?.let(::StringLiteral),
                atLiteralExplicitClassType?.let {
                    kspRequireNotNull(atLiteralExplicitClassDeclaration?.isValid) { "425" }
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
    ): HookParameter = with(parameter) {
        kspRequireNotNull(name) { "471" }
        kspRequireNotNull(type) { "472" }
        kspRequire(!hasDefaultArgument) { "473" }
        when {
            hasOriginAnnotation -> when (at) {
                At.Head, At.Tail -> skipWithError { "476" }

                At.Body -> {
                    val originDescriptor = validateDescriptorReference(originGenericTypeClassDeclaration)
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
                    val originDescriptor = validateDescriptorReference(originGenericTypeClassDeclaration)
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
                    val originDescriptor = validateDescriptorReference(originGenericTypeClassDeclaration)
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
                    val originDescriptor = validateDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is InvokableDescriptor) { "553" }
                    kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Call)) { "554" }
                    HookOriginDescriptorCallWrapperParameter(originDescriptor)
                }
            }

            hasCancelAnnotation -> {
                kspRequire(at != At.Body) { "560" }
                kspRequire(hookDescriptor is MethodDescriptor) { "561" }
                val cancelDescriptor = validateDescriptorReference(cancelGenericTypeClassDeclaration)
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
                val parameterName = explicitParamName ?: name
                val parameterIndex = hookDescriptor.parameters.indexOfFirstOrNull { it.name == parameterName }
                kspRequireNotNull(parameterIndex) { "580" }
                val (paramLocalType, isVar) = validateLocalType(type)
                kspRequire(hookDescriptor.parameters[parameterIndex].type == paramLocalType) { "582" }
                HookParamLocalParameter(parameterName, paramLocalType, parameterIndex, isVar)
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

    @Suppress("unused")
    private inline fun SymbolSource.kspInfo(crossinline message: () -> String) {
        logger.info(message(), symbol)
    }

    @Suppress("unused")
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
