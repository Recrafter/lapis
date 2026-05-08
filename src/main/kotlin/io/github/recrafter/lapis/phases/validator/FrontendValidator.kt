package io.github.recrafter.lapis.phases.validator

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.recrafter.lapis.LapisLogger
import io.github.recrafter.lapis.LapisOptions
import io.github.recrafter.lapis.annotations.AccessStrategy
import io.github.recrafter.lapis.annotations.At
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.indexOfFirstOrNull
import io.github.recrafter.lapis.extensions.kp.KPBoolean
import io.github.recrafter.lapis.extensions.ks.findGenericType
import io.github.recrafter.lapis.extensions.ks.isValid
import io.github.recrafter.lapis.extensions.ks.starProjectedType
import io.github.recrafter.lapis.extensions.ks.toClassDeclaration
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
    private val options: LapisOptions,
    private val builtins: Builtins,
    private val types: KSTypes,
) {
    private val validSchemas: MutableMap<String, Schema> = mutableMapOf()

    private val validDescriptors: MutableMap<String, Descriptor> = mutableMapOf()
    private val invalidDescriptors: MutableList<String> = mutableListOf()

    fun validate(result: ParserResult): ValidatorResult =
        ValidatorResult(
            schemas = result.schemas.flatMap { rootSchema ->
                runOrNullOnSkip { validateSchema(rootSchema) } ?: emptyList()
            },
            patches = result.patches.mapNotNull {
                runOrNullOnSkip { validatePatch(it) }
            },
        )

    private fun validateSchema(parsedSchema: ParsedSchema): List<Schema> = with(parsedSchema) {
        kspRequire(classDeclaration?.isValid == true) { "52" }
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "53" }
        kspRequireNotNull(originJvmClassName) { "54" }
        kspRequire(originClassDeclaration?.isValid == true) { "55" }
        val hasSingleSchemaAnnotation = listOf(
            hasSchemaAnnotation,
            hasInnerSchemaAnnotation,
            hasLocalSchemaAnnotation,
            hasAnonymousSchemaAnnotation,
        ).count { it } == 1
        kspRequire(hasSingleSchemaAnnotation) { "62" }
        if (hasSchemaAnnotation) {
            kspRequire(isTopLevel) { "64" }
        }
        kspRequire(hasPackageName) { "66" }
        kspRequire(isObject) { "67" }
        val accessRequest = validateAccessRequest(
            hasAccessAnnotation, isResolvable, accessStrategy, isAccessUnfinal, emptyList()
        )
        val qualifiedName = kspRequireNotNull(classDeclaration.qualifiedName?.asString()) { "71" }
        val descriptors = descriptors.mapNotNull { parsedDescriptor ->
            val descriptorQualifiedName = parsedDescriptor.classDeclaration.qualifiedName?.asString()
                ?: return@mapNotNull null
            val validatedDescriptor = runOrNullOnSkip {
                validateDescriptor(parsedDescriptor, originClassDeclaration, originJvmClassName, isResolvable)
            }
            if (validatedDescriptor != null) {
                validDescriptors[descriptorQualifiedName] = validatedDescriptor
            } else {
                invalidDescriptors += descriptorQualifiedName
            }
            return@mapNotNull validatedDescriptor
        }
        val schema = Schema(
            symbol = symbol,
            classDeclaration = classDeclaration,

            originJvmClassName = originJvmClassName,
            originClassDeclaration = originClassDeclaration,
            side = kspRequireNotNull(side) { "91" },
            isAccessible = isResolvable,
            accessRequest = accessRequest,
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
        isResolvable: Boolean,
    ): Descriptor = with(descriptor) {
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "111" }
        kspRequire(superClassDeclaration?.isValid == true) { "112" }
        kspRequire(isObject) { "113" }
        val accessRequest = validateAccessRequest(
            hasAccessAnnotation, isResolvable, accessStrategy, isAccessUnfinal, accessFieldOps,
        )
        val mappingName = if (mappingName != null) {
            kspRequire(mappingName.isNotEmpty()) { "118" }
            mappingName
        } else {
            name
        }
        val receiverType = schemaOriginClassDeclaration.starProjectedType
        if (superClassDeclaration.isBuiltin(SimpleBuiltin.Field)) {
            kspRequire(genericType is ParsedTypeDescriptorGenericType) { "125" }
            kspRequireNotNull(genericType.type) { "126" }
            if (accessRequest is MixinAccessRequest) {
                kspRequire(accessRequest.fieldOps.isNotEmpty()) { "128" }
            }
            return FieldDescriptor(
                symbol = symbol,
                classDeclaration = classDeclaration,

                name = name,
                receiverType = receiverType,
                inaccessibleReceiverJvmClassName = if (isResolvable) null else schemaOriginJvmClassName,
                mappingName = mappingName,
                fieldType = genericType.type,
                arrayComponentType = genericType.arrayComponentType,
                isStatic = hasStaticAnnotation,
                accessRequest = accessRequest,
            )
        }
        kspRequire(genericType is ParsedFunctionTypeDescriptorGenericType) { "144" }
        kspRequire(genericType.receiverType == null) { "145" }
        val parameters = genericType.parameters.map { parameter ->
            kspRequire(!parameter.type.isFunctionType) { "147" }
            FunctionTypeParameter(
                type = parameter.type,
                name = parameter.name,
            )
        }
        if (accessRequest != null) {
            kspRequire(parameters.all { it.name != null }) { "154" }
        }
        return when {
            superClassDeclaration.isBuiltin(SimpleBuiltin.Method) -> {
                MethodDescriptor(
                    symbol = symbol,
                    classDeclaration = classDeclaration,

                    name = name,
                    receiverType = receiverType,
                    inaccessibleReceiverJvmClassName = if (isResolvable) null else schemaOriginJvmClassName,
                    returnType = genericType.returnType,
                    mappingName = mappingName,
                    parameters = parameters,
                    isStatic = hasStaticAnnotation,
                    accessRequest = accessRequest,
                )
            }

            superClassDeclaration.isBuiltin(SimpleBuiltin.Constructor) -> {
                kspRequire(!isAccessUnfinal) { "174" }
                kspRequire(genericType.returnType == null) { "175" }
                kspRequire(!hasMappingNameAnnotation) { "176" }
                if (accessRequest is MixinAccessRequest) {
                    kspRequire(isResolvable) { "178" }
                }
                ConstructorDescriptor(
                    symbol = symbol,
                    classDeclaration = classDeclaration,

                    name = name,
                    returnType = receiverType,
                    parameters = parameters,
                    accessRequest = accessRequest,
                )
            }

            else -> skipWithError { "191" }
        }
    }

    private fun SymbolSource.validateAccessRequest(
        hasAccessAnnotation: Boolean,
        isResolvable: Boolean,
        strategy: AccessStrategy?,
        unfinal: Boolean,
        fieldOps: List<Op>,
    ): AccessRequest? {
        if (!hasAccessAnnotation) return null
        kspRequireNotNull(strategy) { "203" }
        if (strategy == AccessStrategy.Tweak) {
            kspRequire(isResolvable) { "205" }
            kspRequire(options.accessWidenerConfig != null || options.accessTransformerConfig != null) { "206" }
            return TweakAccessRequest(unfinal)
        }
        return MixinAccessRequest(unfinal, fieldOps)
    }

    private fun validatePatch(patch: ParsedPatch): Patch = with(patch) {
        kspRequireNotNull(name) { "213" }
        kspRequireNotNull(side) { "214" }
        kspRequireNotNull(initStrategy) { "215" }
        kspRequire(classDeclaration?.isValid == true) { "216" }
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "217" }
        kspRequire(schemaClassDeclaration?.isValid == true) { "218" }
        kspRequire(isTopLevel) { "219" }
        kspRequire(hasPackageName) { "220" }
        kspRequire(isPublic) { "221" }
        val schema = validSchemas[schemaClassDeclaration.qualifiedName?.asString()]
        kspRequireNotNull(schema) { "223" }
        kspRequire(isClass || isObject) { "224" }
        kspRequire(!isSealed) { "225" }
        kspRequire(!isOpen) { "226" }
        val constructor = kspRequireNotNull(patch.constructors.singleOrNull()) { "227" }
        constructor.kspRequire(constructor.isPublic) { "228" }
        val companionObjects = companionObjects.mapNotNull {
            runOrNullOnSkip { validatePatchCompanionObject(it) }
        }
        val companionObjectHooks = companionObjects.flatMap { companionObject ->
            companionObject.functions.filter { it.hasHookAnnotation }.mapNotNull {
                runOrNullOnSkip { validatePatchHook(it, isObject, isInCompanionObject = true) }
            }
        }
        val (parsedHookFunctions, parsedRegularFunctions) = functions.partition { it.hasHookAnnotation }
        val constructorParameters = constructor.parameters.mapNotNull {
            runOrNullOnSkip { validatePatchConstructorParameter(it, schema) }
        }
        val extensionProperties = properties.filter { it.hasExtensionAnnotation }.mapNotNull {
            runOrNullOnSkip { validatePatchExtensionProperty(it, schema) }
        }
        val extensionFunctions = parsedRegularFunctions.filter { it.hasExtensionAnnotation }.mapNotNull {
            runOrNullOnSkip { validatePatchExtensionFunction(it, schema.isAccessible) }
        }
        val shadowProperties = properties.filter { it.hasShadowAnnotation }.mapNotNull {
            runOrNullOnSkip { validatePatchShadowProperty(it) }
        }
        val shadowFunctions = parsedRegularFunctions.filter { it.hasShadowAnnotation }.mapNotNull {
            runOrNullOnSkip { validatePatchShadowFunction(it) }
        }
        val hooks = parsedHookFunctions.mapNotNull {
            runOrNullOnSkip { validatePatchHook(it, isObject, isInCompanionObject = false) }
        }
        val hasStaticHooksOnly = constructorParameters.isEmpty()
            && extensionProperties.isEmpty() && extensionFunctions.isEmpty()
            && shadowProperties.isEmpty() && shadowFunctions.isEmpty()
            && hooks.all { it.descriptor.isStatic }
        if (!hasStaticHooksOnly) {
            kspRequire(isAbstract) { "261" }
        }
        return Patch(
            symbol = symbol,
            classDeclaration = classDeclaration,

            name = name,
            side = side,
            initStrategy = initStrategy,
            isObject = isObject,
            hasStaticHooksOnly = hasStaticHooksOnly,
            schema = schema,

            constructorParameters = constructorParameters,
            extensionSources = extensionProperties + extensionFunctions,
            shadowSources = shadowProperties + shadowFunctions,
            hooks = hooks + companionObjectHooks,
        )
    }

    private fun validatePatchCompanionObject(
        companionObject: ParsedPatchCompanionObject,
    ): ParsedPatchCompanionObject = with(companionObject) {
        kspRequire(isPublic) { "284" }
        companionObject
    }

    private fun validatePatchConstructorParameter(
        parameter: ParsedPatchConstructorParameter,
        schema: Schema,
    ): PatchConstructorParameter = with(parameter) {
        when {
            hasOriginAnnotation -> {
                val instanceClassDeclaration = type.toClassDeclaration()
                kspRequire(instanceClassDeclaration == schema.originClassDeclaration) { "295" }
                kspRequire(type.arguments.none { it.variance != Variance.STAR }) { "296" }
                PatchConstructorOriginParameter
            }

            else -> skipWithError { "300" }
        }
    }

    private fun validatePatchExtensionProperty(
        property: ParsedPatchProperty,
        schema: Schema,
    ): PatchExtensionProperty = with(property) {
        kspRequireNotNull(getterJvmName) { "308" }
        kspRequire(property.isPublic) { "309" }
        kspRequire(!property.isExtension) { "310" }
        kspRequire(schema.isAccessible) { "311" }
        kspRequire(!property.isOpen && !property.isAbstract) { "312" }
        return PatchExtensionProperty(
            name = name,
            getterJvmName = getterJvmName,
            setterJvmName = setterJvmName,
            type = type,
            isMutable = isMutable,
        )
    }

    private fun validatePatchShadowProperty(property: ParsedPatchProperty): PatchShadowProperty = with(property) {
        kspRequireNotNull(getterJvmName) { "323" }
        kspRequire(property.isPublic) { "324" }
        kspRequire(!property.isExtension) { "325" }
        kspRequire(property.isAbstract) { "326" }
        val mappingName = if (explicitShadowName != null) {
            kspRequire(explicitShadowName.isNotEmpty()) { "328" }
            explicitShadowName
        } else {
            name
        }
        PatchShadowProperty(
            name = name,
            getterJvmName = getterJvmName,
            setterJvmName = setterJvmName,
            mappingName = mappingName,
            isStatic = property.hasStaticAnnotation,
            type = type,
            isMutable = isMutable,
            isFinal = isShadowFinal,
        )
    }

    private fun validatePatchExtensionFunction(
        function: ParsedPatchFunction,
        isResolvable: Boolean,
    ): PatchExtensionFunction = with(function) {
        kspRequire(function.isPublic) { "349" }
        kspRequire(!function.isExtension) { "350" }
        val parameters = function.parameters.map {
            FunctionParameter(
                name = kspRequireNotNull(it.name) { "353" },
                type = kspRequireNotNull(it.type) { "354" },
            )
        }
        kspRequire(isResolvable) { "357" }
        kspRequire(!function.isOpen && !function.isAbstract) { "358" }
        return PatchExtensionFunction(
            name = name,
            jvmName = jvmName,
            parameters = parameters,
            returnType = function.returnType,
        )
    }

    private fun validatePatchShadowFunction(function: ParsedPatchFunction): PatchShadowFunction = with(function) {
        kspRequire(function.isPublic) { "368" }
        kspRequire(!function.isExtension) { "369" }
        val parameters = function.parameters.map {
            FunctionParameter(
                name = kspRequireNotNull(it.name) { "372" },
                type = kspRequireNotNull(it.type) { "373" },
            )
        }
        kspRequire(function.isAbstract) { "376" }
        kspRequire(!function.isShadowFinal) { "377" }
        val mappingName = if (explicitShadowName != null) {
            kspRequire(explicitShadowName.isNotEmpty()) { "379" }
            explicitShadowName
        } else {
            name
        }
        PatchShadowFunction(
            name = name,
            jvmName = jvmName,
            mappingName = mappingName,
            isStatic = function.hasStaticAnnotation,
            parameters = parameters,
            returnType = function.returnType,
        )
    }

    private fun validatePatchHook(
        function: ParsedPatchFunction,
        isInObject: Boolean,
        isInCompanionObject: Boolean,
    ): PatchHook = with(function) {
        kspRequireNotNull(hookAt) { "399" }
        kspRequire(!function.isOpen) { "400" }
        kspRequire(!function.hasTypeParameters) { "401" }
        val hookDescriptor = validateDescriptorReference(hookDescriptorClassDeclaration)
        kspRequire(hookDescriptor is InvokableDescriptor) { "403" }
        if (hookDescriptor.isStatic) {
            kspRequire(isInObject || isInCompanionObject) { "405" }
        } else {
            kspRequire(!isInObject) { "407" }
            kspRequire(!isInCompanionObject) { "408" }
        }
        val ordinals: (List<Int>) -> List<Int> = { validateOrdinals(it) }
        val parameters: () -> List<HookParameter> = {
            function.parameters.mapNotNull { parameter ->
                runOrNullOnSkip { validateHookParameter(parameter, function, hookAt, hookDescriptor) }
            }
        }
        when (hookAt) {
            At.Head -> {
                kspRequire(returnType == null) { "418" }
                when (hookDescriptor) {
                    is ConstructorDescriptor -> {
                        kspRequire(hasAtConstructorHeadAnnotation) { "421" }
                        ConstructorHeadHook(
                            jvmName = jvmName,
                            descriptor = hookDescriptor,
                            phase = kspRequireNotNull(atConstructorHeadPhase) { "425" },
                            parameters = parameters(),
                        )
                    }

                    is MethodDescriptor -> {
                        MethodHeadHook(
                            jvmName = jvmName,
                            descriptor = hookDescriptor,
                            parameters = parameters(),
                        )
                    }
                }
            }

            At.Body -> {
                kspRequire(hookDescriptor is MethodDescriptor) { "441" }
                kspRequire(returnType == hookDescriptor.returnType) { "442" }
                BodyHook(
                    jvmName = jvmName,
                    targetDescriptor = hookDescriptor,
                    returnType = returnType,
                    parameters = parameters(),
                )
            }

            At.Tail -> {
                kspRequire(returnType == null) { "452" }
                TailHook(
                    jvmName = jvmName,
                    descriptor = hookDescriptor,
                    parameters = parameters(),
                )
            }

            At.Local -> {
                kspRequire(hasAtLocalAnnotation) { "461" }
                kspRequireNotNull(atLocalOp) { "462" }
                kspRequireNotNull(atLocalType) { "463" }
                kspRequire(returnType == atLocalType) { "464" }
                LocalHook(
                    jvmName = jvmName,
                    descriptor = hookDescriptor,
                    type = atLocalType,
                    ordinals = ordinals(atLocalOpOrdinals),
                    local = validateLocal(atLocalExplicitOrdinal, atLocalExplicitName),
                    op = atLocalOp,
                    parameters = parameters(),
                )
            }

            At.Instanceof -> {
                kspRequire(hasAtInstanceofAnnotation) { "477" }
                kspRequire(atInstanceofTypeClassDeclaration?.isValid == true) { "478" }
                kspRequire(returnType?.toClassName()?.asIrTypeName() == KPBoolean.asIrTypeName()) { "479" }
                InstanceofHook(
                    jvmName = jvmName,
                    descriptor = hookDescriptor,
                    typeClassDeclaration = atInstanceofTypeClassDeclaration,
                    returnType = returnType,
                    ordinals = ordinals(atInstanceofOrdinals),
                    parameters = parameters(),
                )
            }

            At.Return -> {
                kspRequire(hasAtReturnAnnotation) { "491" }
                kspRequire(returnType == hookDescriptor.returnType) { "492" }
                ReturnHook(
                    jvmName = jvmName,
                    descriptor = hookDescriptor,
                    type = returnType,
                    ordinals = ordinals(atReturnOrdinals),
                    parameters = parameters(),
                )
            }

            At.Literal -> {
                kspRequire(hasAtLiteralAnnotation) { "503" }
                val literal = validateLiteral(function)
                val type = literal.getType(types)
                if (literal !is NullLiteral) {
                    if (literal !is StringLiteral && literal !is ClassLiteral) {
                        kspRequire(returnType?.isMarkedNullable == false) { "508" }
                    }
                    kspRequire(returnType == type) { "510" }
                }
                LiteralHook(
                    jvmName = jvmName,
                    descriptor = hookDescriptor,
                    type = type,
                    literal = literal,
                    ordinals = ordinals(atLiteralOrdinals),
                    parameters = parameters(),
                )
            }

            At.Field -> {
                kspRequire(hasAtFieldAnnotation) { "523" }
                kspRequireNotNull(atFieldOp) { "524" }
                val targetDescriptor = validateDescriptorReference(atFieldDescriptorClassDeclaration)
                kspRequire(targetDescriptor is FieldDescriptor) { "526" }
                when (atFieldOp) {
                    Op.Get -> {
                        kspRequire(returnType?.makeNotNullable() == targetDescriptor.fieldType) { "529" }
                        FieldGetHook(
                            jvmName = jvmName,
                            descriptor = hookDescriptor,
                            type = targetDescriptor.fieldType,
                            ordinals = ordinals(atFieldOrdinals),
                            targetDescriptor = targetDescriptor,
                            parameters = parameters(),
                        )
                    }

                    Op.Set -> {
                        kspRequire(returnType == null) { "541" }
                        FieldSetHook(
                            jvmName = jvmName,
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
                kspRequire(hasAtArrayAnnotation) { "555" }
                kspRequireNotNull(atArrayOp) { "556" }
                val targetDescriptor = validateDescriptorReference(atArrayDescriptorClassDeclaration)
                kspRequire(targetDescriptor is FieldDescriptor) { "558" }
                kspRequireNotNull(targetDescriptor.arrayComponentType) { "559" }
                when (atArrayOp) {
                    Op.Get -> kspRequire(returnType == targetDescriptor.arrayComponentType) { "561" }
                    Op.Set -> kspRequire(returnType == null) { "562" }
                }
                ArrayHook(
                    jvmName = jvmName,
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
                kspRequire(hasAtCallAnnotation) { "577" }
                val targetDescriptor = validateDescriptorReference(atCallDescriptorClassDeclaration)
                kspRequire(targetDescriptor is MethodDescriptor) { "579" }
                kspRequire(returnType?.makeNotNullable() == targetDescriptor.returnType) { "580" }
                CallHook(
                    jvmName = jvmName,
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
                    kspRequire(it != 0) { "598" }
                    IntLiteral(it)
                },
                atLiteralExplicitFloat?.let(::FloatLiteral),
                atLiteralExplicitLong?.let(::LongLiteral),
                atLiteralExplicitDouble?.let(::DoubleLiteral),
                atLiteralExplicitString?.let(::StringLiteral),
                atLiteralExplicitClassType?.let {
                    kspRequire(atLiteralExplicitClassDeclaration?.isValid == true) { "606" }
                    ClassLiteral(atLiteralExplicitClassDeclaration)
                },
                atLiteralExplicitNull?.let { NullLiteral },
            ).singleOrNull()
        ) { "611" }
    }

    private fun SymbolSource.validateLocal(
        ordinal: Int?,
        explicitName: String?,
        fallbackName: String? = explicitName
    ): DomainLocal =
        kspRequireNotNull(
            when {
                ordinal != null -> ordinal.takeIf { explicitName == null }?.let {
                    kspRequire(it >= 0) { "622" }
                    PositionalLocal(it)
                }

                explicitName != null -> {
                    kspRequire(explicitName.isNotEmpty()) { "627" }
                    NamedLocal(explicitName)
                }

                fallbackName != null -> NamedLocal(fallbackName)
                else -> null
            }
        ) { "634" }

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
        kspRequire(classDeclaration?.isValid == true) { "648" }
        val qualifiedName = classDeclaration.qualifiedName?.asString()
        if (invalidDescriptors.contains(qualifiedName)) {
            skipWithError { "651" }
        }
        return validDescriptors[qualifiedName] ?: lapisError("Descriptor cannot be null")
    }

    private fun validateHookParameter(
        parameter: ParsedPatchFunctionParameter,
        function: ParsedPatchFunction,
        at: At,
        hookDescriptor: InvokableDescriptor,
    ): HookParameter = with(parameter) {
        kspRequireNotNull(name) { "662" }
        kspRequireNotNull(type) { "663" }
        kspRequire(!hasDefaultArgument) { "664" }
        when {
            hasOriginAnnotation -> when (at) {
                At.Head, At.Tail -> skipWithError { "667" }

                At.Body -> {
                    val originDescriptor = validateDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is InvokableDescriptor) { "671" }
                    kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Body)) { "672" }
                    HookOriginBodyDescriptorWrapperParameter(originDescriptor)
                }

                At.Local -> {
                    kspRequire(type == function.returnType) { "677" }
                    HookOriginValueParameter
                }

                At.Instanceof -> {
                    kspRequire(type.declaration.isBuiltin(SimpleBuiltin.Instanceof)) { "682" }
                    HookOriginInstanceofWrapperParameter
                }

                At.Return -> {
                    kspRequireNotNull(hookDescriptor.returnType) { "687" }
                    kspRequire(type == hookDescriptor.returnType) { "688" }
                    HookOriginValueParameter
                }

                At.Literal -> {
                    val literal = validateLiteral(function)
                    kspRequire(literal !is NullLiteral) { "694" }
                    kspRequire(type == literal.getType(types)) { "695" }
                    HookOriginValueParameter
                }

                At.Field -> {
                    kspRequireNotNull(function.atFieldOp) { "700" }
                    val originDescriptor = validateDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is FieldDescriptor) { "702" }
                    when (function.atFieldOp) {
                        Op.Get -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.FieldGet)) { "705" }
                            HookOriginFieldGetDescriptorWrapperParameter(originDescriptor)
                        }

                        Op.Set -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.FieldSet)) { "710" }
                            HookOriginFieldSetDescriptorWrapperParameter(originDescriptor)
                        }
                    }
                }

                At.Array -> {
                    kspRequireNotNull(function.atArrayOp) { "717" }
                    val originDescriptor = validateDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is FieldDescriptor) { "719" }
                    kspRequireNotNull(originDescriptor.arrayComponentType) { "720" }
                    when (function.atArrayOp) {
                        Op.Get -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.ArrayGet)) { "723" }
                            HookOriginArrayGetDescriptorWrapperParameter(
                                originDescriptor,
                                originDescriptor.arrayComponentType
                            )
                        }

                        Op.Set -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.ArraySet)) { "731" }
                            HookOriginArraySetDescriptorWrapperParameter(
                                originDescriptor,
                                originDescriptor.arrayComponentType
                            )
                        }
                    }
                }

                At.Call -> {
                    val originDescriptor = validateDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is InvokableDescriptor) { "742" }
                    kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Call)) { "743" }
                    HookOriginCallDescriptorWrapperParameter(originDescriptor)
                }
            }

            hasCancelAnnotation -> {
                kspRequire(at != At.Body) { "749" }
                kspRequire(hookDescriptor is MethodDescriptor) { "750" }
                val cancelDescriptor = validateDescriptorReference(cancelGenericTypeClassDeclaration)
                kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Cancel)) { "752" }
                kspRequire(cancelDescriptor == hookDescriptor) { "753" }
                HookCancelDescriptorWrapperParameter(hookDescriptor)
            }

            hasOrdinalAnnotation -> {
                kspRequire(type == types.int) { "758" }
                kspRequire(function.hasOrdinals()) { "759" }
                HookOrdinalParameter
            }

            hasParamAnnotation -> {
                kspRequire(at != At.Body) { "764" }
                if (explicitParamName != null) {
                    kspRequire(explicitParamName.trim().isNotEmpty()) { "766" }
                }
                val parameterName = explicitParamName ?: name
                val parameterIndex = hookDescriptor.parameters.indexOfFirstOrNull { it.name == parameterName }
                kspRequireNotNull(parameterIndex) { "770" }
                val (paramLocalType, isVar) = validateLocalType(type)
                kspRequire(hookDescriptor.parameters[parameterIndex].type == paramLocalType) { "772" }
                HookParamLocalParameter(parameterName, paramLocalType, parameterIndex, isVar)
            }

            hasLocalAnnotation -> {
                kspRequire(at != At.Body) { "777" }
                val (bodyLocalType, isVar) = validateLocalType(type)
                HookBodyLocalParameter(
                    name,
                    bodyLocalType,
                    validateLocal(explicitLocalOrdinal, explicitLocalName, name),
                    isVar,
                )
            }

            hasShareAnnotation -> {
                kspRequire(type.declaration.isBuiltin(SimpleBuiltin.LocalVar)) { "788" }
                val type = kspRequireNotNull(type.findGenericType()) { "789" }
                if (explicitShareKey != null) {
                    kspRequire(explicitShareKey.trim().isNotEmpty()) { "791" }
                }
                HookShareLocalParameter(name, type, explicitShareKey ?: name, isShareExported)
            }

            else -> skipWithError { "796" }
        }
    }

    private fun ParsedPatchFunctionParameter.validateLocalType(type: KSType): Pair<KSType, Boolean> {
        val isVar = type.declaration.isBuiltin(SimpleBuiltin.LocalVar)
        val localType = if (isVar) {
            kspRequireNotNull(type.findGenericType()) { "803" }
        } else {
            kspRequireNotNull(type) { "805" }
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

    @Suppress("unused", "UnusedReceiverParameter")
    @Deprecated(
        message = "Ambiguous call: use kspRequire() for Boolean conditions.",
        replaceWith = ReplaceWith("kspRequire(value, message)"),
        level = DeprecationLevel.ERROR,
    )
    private fun SymbolSource.kspRequireNotNull(value: Boolean?, message: () -> String): Nothing {
        lapisError("kspRequireNotNull() called with a Boolean value. Use kspRequire() for logical conditions.")
    }

    private fun <R> runOrNullOnSkip(block: () -> R): R? =
        try {
            block()
        } catch (_: SkipSignal) {
            null
        }

    private class SkipSignal : Exception()
}
