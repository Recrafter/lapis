package io.github.recrafter.lapis.phases.validator

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.recrafter.lapis.LapisLogger
import io.github.recrafter.lapis.Options
import io.github.recrafter.lapis.annotations.Accessor
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
        kspRequire(classDeclaration?.isValid == true) { "52" }
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "53" }
        kspRequireNotNull(originJvmClassName) { "54" }
        kspRequire(originClassDeclaration?.isValid == true) { "55" }
        kspRequireNotNull(
            listOf(
                hasSchemaAnnotation,
                hasInnerSchemaAnnotation,
                hasLocalSchemaAnnotation,
                hasAnonymousSchemaAnnotation,
            ).singleOrNull { it }
        ) { "63" }
        val accessRequest = if (hasAccessAnnotation) {
            kspRequire(isAccessible) { "65" }
            kspRequire(accessor == Accessor.Tweaker) { "66" }
            AccessRequest(accessor, isAccessUnfinal)
        } else null
        val qualifiedName = kspRequireNotNull(classDeclaration.qualifiedName?.asString()) { "69" }
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
        isAccessibleSchema: Boolean,
    ): Descriptor = with(descriptor) {
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "107" }
        kspRequire(superClassDeclaration?.isValid == true) { "108" }
        val accessor = if (hasAccessAnnotation) {
            kspRequire(isAccessibleSchema) { "110" }
            kspRequireNotNull(accessor) { "111" }
        } else null
        if (accessor == Accessor.Tweaker) {
            kspRequire(options.accessWidenerConfig != null || options.accessTransformerConfig != null) { "114" }
        }
        val mappingName = if (mappingName != null) {
            kspRequire(mappingName.isNotEmpty()) { "117" }
            mappingName
        } else {
            name
        }
        val receiverType = schemaOriginClassDeclaration.starProjectedType
        if (superClassDeclaration.isBuiltin(SimpleBuiltin.Field)) {
            kspRequire(genericType is ParsedTypeDescriptorGenericType) { "124" }
            kspRequireNotNull(genericType.type) { "125" }
            return FieldDescriptor(
                name = name,
                classDeclaration = classDeclaration,
                receiverType = receiverType,
                inaccessibleReceiverJvmClassName = if (isAccessibleSchema) null else schemaOriginJvmClassName,
                mappingName = mappingName,
                fieldType = genericType.type,
                arrayComponentType = genericType.arrayComponentType,
                isStatic = hasStaticAnnotation,
                accessRequest = accessor?.let { AccessRequest(it, isAccessUnfinal) },
            )
        }
        kspRequire(genericType is ParsedFunctionTypeDescriptorGenericType) { "138" }
        kspRequire(genericType.receiverType == null) { "139" }
        val parameters = genericType.parameters.map { parameter ->
            kspRequire(!parameter.type.isFunctionType) { "141" }
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
                    accessRequest = accessor?.let { AccessRequest(it, isAccessUnfinal) },
                )
            }

            superClassDeclaration.isBuiltin(SimpleBuiltin.Constructor) -> {
                kspRequire(!isAccessUnfinal) { "163" }
                kspRequire(genericType.returnType == null) { "164" }
                kspRequire(!hasMappingNameAnnotation) { "165" }
                ConstructorDescriptor(
                    name = name,
                    classDeclaration = classDeclaration,
                    returnType = receiverType,
                    parameters = parameters,
                    accessRequest = accessor?.let { AccessRequest(it, false) },
                )
            }

            else -> skipWithError { "175" }
        }
    }

    private fun validatePatch(patch: ParsedPatch): Patch = with(patch) {
        kspRequireNotNull(name) { "180" }
        kspRequireNotNull(side) { "181" }
        kspRequireNotNull(initStrategy) { "182" }
        kspRequire(classDeclaration?.isValid == true) { "183" }
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "184" }
        kspRequire(schemaClassDeclaration?.isValid == true) { "185" }
        kspRequire(isTopLevel) { "186" }
        kspRequire(isPublic) { "187" }
        val schema = validSchemas[schemaClassDeclaration.qualifiedName?.asString()]
        kspRequireNotNull(schema) { "189" }
        kspRequire(isClass || isObject) { "190" }
        kspRequire(!isSealed) { "191" }
        kspRequire(!isOpen) { "192" }
        val constructor = kspRequireNotNull(patch.constructors.singleOrNull()) { "193" }
        constructor.kspRequire(constructor.isPublic) { "194" }
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
        val bridgeSourceProperties = properties.filter { it.hasExtensionAnnotation }.mapNotNull {
            runOrNullOnSkip { validatePatchExtensionProperty(it, schema) }
        }
        val bridgeSourceFunctions = parsedRegularFunctions.filter { it.hasExtensionAnnotation }.mapNotNull {
            runOrNullOnSkip { validatePatchExtensionFunction(it, schema) }
        }
        val hooks = parsedHookFunctions.mapNotNull {
            runOrNullOnSkip { validatePatchHook(it, isObject, isInCompanionObject = false) }
        }
        val hasStaticHooksOnly = constructorParameters.isEmpty()
            && bridgeSourceProperties.isEmpty() && bridgeSourceFunctions.isEmpty()
            && hooks.all { it.descriptor.isStatic }
        if (!hasStaticHooksOnly) {
            kspRequire(isAbstract) { "220" }
        }
        return Patch(
            source = symbol,

            name = name,
            side = side,
            initStrategy = initStrategy,
            isObject = isObject,
            hasStaticHooksOnly = hasStaticHooksOnly,

            classDeclaration = classDeclaration,

            schema = schema,

            constructorParameters = constructorParameters,
            bridgeSources = bridgeSourceProperties + bridgeSourceFunctions,
            hooks = hooks + companionObjectHooks,
        )
    }

    private fun validatePatchCompanionObject(
        companionObject: ParsedPatchCompanionObject,
    ): ParsedPatchCompanionObject = with(companionObject) {
        kspRequire(isPublic) { "244" }
        companionObject
    }

    private fun validatePatchConstructorParameter(
        parameter: ParsedPatchConstructorParameter,
        schema: Schema,
    ): PatchConstructorParameter = with(parameter) {
        when {
            hasOriginAnnotation -> {
                val instanceClassDeclaration = type.toClassDeclaration()
                kspRequire(instanceClassDeclaration == schema.originClassDeclaration) { "255" }
                kspRequire(type.arguments.none { it.variance != Variance.STAR }) { "256" }
                PatchConstructorOriginParameter
            }

            else -> skipWithError { "260" }
        }
    }

    private fun validatePatchExtensionProperty(
        property: ParsedPatchProperty,
        schema: Schema,
    ): PatchExtensionProperty = with(property) {
        kspRequire(schema.isAccessible) { "268" }
        kspRequire(property.isPublic) { "269" }
        kspRequire(!property.isOpen && !property.isAbstract) { "270" }
        kspRequire(!property.isExtension) { "271" }
        kspRequireNotNull(getterJvmName) { "272" }
        PatchExtensionProperty(
            name = name,
            getterJvmName = getterJvmName,
            setterJvmName = setterJvmName,
            type = type,
            isMutable = isMutable,
        )
    }

    private fun validatePatchExtensionFunction(
        function: ParsedPatchFunction,
        schema: Schema,
    ): PatchExtensionFunction = with(function) {
        kspRequire(schema.isAccessible) { "286" }
        kspRequire(function.isPublic) { "287" }
        kspRequire(!function.isOpen && !function.isAbstract) { "288" }
        kspRequire(!function.isExtension) { "289" }
        PatchExtensionFunction(
            name = name,
            jvmName = jvmName,
            parameters = function.parameters.map {
                FunctionParameter(
                    name = kspRequireNotNull(it.name) { "295" },
                    type = kspRequireNotNull(it.type) { "296" },
                )
            },
            returnType = function.returnType,
        )
    }

    private fun validatePatchHook(
        function: ParsedPatchFunction,
        isInObject: Boolean,
        isInCompanionObject: Boolean,
    ): PatchHook = with(function) {
        kspRequireNotNull(hookAt) { "308" }
        kspRequire(!function.isOpen) { "309" }
        kspRequire(!function.hasTypeParameters) { "310" }
        val hookDescriptor = validateDescriptorReference(hookDescriptorClassDeclaration)
        kspRequire(hookDescriptor is InvokableDescriptor) { "312" }
        if (hookDescriptor.isStatic) {
            kspRequire(isInObject || isInCompanionObject) { "314" }
        } else {
            kspRequire(!isInObject) { "316" }
            kspRequire(!isInCompanionObject) { "317" }
        }
        val ordinals: (List<Int>) -> List<Int> = { validateOrdinals(it) }
        val parameters: () -> List<HookParameter> = {
            function.parameters.mapNotNull { parameter ->
                runOrNullOnSkip { validateHookParameter(parameter, function, hookAt, hookDescriptor) }
            }
        }
        when (hookAt) {
            At.Head -> {
                kspRequire(returnType == null) { "327" }
                when (hookDescriptor) {
                    is ConstructorDescriptor -> {
                        kspRequire(hasAtConstructorHeadAnnotation) { "330" }
                        ConstructorHeadHook(
                            jvmName = jvmName,
                            descriptor = hookDescriptor,
                            phase = kspRequireNotNull(atConstructorHeadPhase) { "334" },
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
                kspRequire(hookDescriptor is MethodDescriptor) { "350" }
                kspRequire(returnType == hookDescriptor.returnType) { "351" }
                BodyHook(
                    jvmName = jvmName,
                    targetDescriptor = hookDescriptor,
                    returnType = returnType,
                    parameters = parameters(),
                )
            }

            At.Tail -> {
                kspRequire(returnType == null) { "361" }
                TailHook(
                    jvmName = jvmName,
                    descriptor = hookDescriptor,
                    parameters = parameters(),
                )
            }

            At.Local -> {
                kspRequire(hasAtLocalAnnotation) { "370" }
                kspRequireNotNull(atLocalOp) { "371" }
                kspRequireNotNull(atLocalType) { "372" }
                kspRequire(returnType == atLocalType) { "373" }
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
                kspRequire(hasAtInstanceofAnnotation) { "386" }
                kspRequire(atInstanceofTypeClassDeclaration?.isValid == true) { "387" }
                kspRequire(returnType?.toClassName()?.asIrTypeName() == KPBoolean.asIrTypeName()) { "388" }
                InstanceofHook(
                    jvmName = jvmName,
                    descriptor = hookDescriptor,
                    classDeclaration = atInstanceofTypeClassDeclaration,
                    returnType = returnType,
                    ordinals = ordinals(atInstanceofOrdinals),
                    parameters = parameters(),
                )
            }

            At.Return -> {
                kspRequire(hasAtReturnAnnotation) { "400" }
                kspRequire(returnType == hookDescriptor.returnType) { "401" }
                ReturnHook(
                    jvmName = jvmName,
                    descriptor = hookDescriptor,
                    type = returnType,
                    ordinals = ordinals(atReturnOrdinals),
                    parameters = parameters(),
                )
            }

            At.Literal -> {
                kspRequire(hasAtLiteralAnnotation) { "412" }
                val literal = validateLiteral(function)
                val type = literal.getType(types)
                if (literal !is NullLiteral) {
                    if (literal !is StringLiteral && literal !is ClassLiteral) {
                        kspRequire(returnType?.isMarkedNullable == false) { "417" }
                    }
                    kspRequire(returnType == type) { "419" }
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
                kspRequire(hasAtFieldAnnotation) { "432" }
                kspRequireNotNull(atFieldOp) { "433" }
                val targetDescriptor = validateDescriptorReference(atFieldDescriptorClassDeclaration)
                kspRequire(targetDescriptor is FieldDescriptor) { "435" }
                when (atFieldOp) {
                    Op.Get -> {
                        kspRequire(returnType?.makeNotNullable() == targetDescriptor.fieldType) { "438" }
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
                        kspRequire(returnType == null) { "450" }
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
                kspRequire(hasAtArrayAnnotation) { "464" }
                kspRequireNotNull(atArrayOp) { "465" }
                val targetDescriptor = validateDescriptorReference(atArrayDescriptorClassDeclaration)
                kspRequire(targetDescriptor is FieldDescriptor) { "467" }
                kspRequireNotNull(targetDescriptor.arrayComponentType) { "468" }
                when (atArrayOp) {
                    Op.Get -> kspRequire(returnType == targetDescriptor.arrayComponentType) { "470" }
                    Op.Set -> kspRequire(returnType == null) { "471" }
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
                kspRequire(hasAtCallAnnotation) { "486" }
                val targetDescriptor = validateDescriptorReference(atCallDescriptorClassDeclaration)
                kspRequire(targetDescriptor is MethodDescriptor) { "488" }
                kspRequire(returnType?.makeNotNullable() == targetDescriptor.returnType) { "489" }
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
                    kspRequire(it != 0) { "507" }
                    IntLiteral(it)
                },
                atLiteralExplicitFloat?.let(::FloatLiteral),
                atLiteralExplicitLong?.let(::LongLiteral),
                atLiteralExplicitDouble?.let(::DoubleLiteral),
                atLiteralExplicitString?.let(::StringLiteral),
                atLiteralExplicitClassType?.let {
                    kspRequireNotNull(atLiteralExplicitClassDeclaration?.isValid) { "515" }
                    ClassLiteral(atLiteralExplicitClassDeclaration)
                },
                atLiteralExplicitNull?.let { NullLiteral },
            ).singleOrNull()
        ) { "520" }
    }

    private fun SymbolSource.validateLocal(
        ordinal: Int?,
        explicitName: String?,
        fallbackName: String? = explicitName
    ): DomainLocal =
        kspRequireNotNull(
            when {
                ordinal != null -> ordinal.takeIf { explicitName == null }?.let {
                    kspRequire(it >= 0) { "531" }
                    PositionalLocal(it)
                }

                explicitName != null -> {
                    kspRequire(explicitName.isNotEmpty()) { "536" }
                    NamedLocal(explicitName)
                }

                fallbackName != null -> NamedLocal(fallbackName)
                else -> null
            }
        ) { "543" }

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
        kspRequire(classDeclaration?.isValid == true) { "557" }
        val qualifiedName = classDeclaration.qualifiedName?.asString()
        if (invalidDescriptors.contains(qualifiedName)) {
            skipWithError { "560" }
        }
        return validDescriptors[qualifiedName] ?: lapisError("Failed to find descriptor for $qualifiedName")
    }

    private fun validateHookParameter(
        parameter: ParsedPatchFunctionParameter,
        function: ParsedPatchFunction,
        at: At,
        hookDescriptor: InvokableDescriptor,
    ): HookParameter = with(parameter) {
        kspRequireNotNull(name) { "571" }
        kspRequireNotNull(type) { "572" }
        kspRequire(!hasDefaultArgument) { "573" }
        when {
            hasOriginAnnotation -> when (at) {
                At.Head, At.Tail -> skipWithError { "576" }

                At.Body -> {
                    val originDescriptor = validateDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is InvokableDescriptor) { "580" }
                    kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Body)) { "581" }
                    HookOriginBodyDescriptorWrapperParameter(originDescriptor)
                }

                At.Local -> {
                    kspRequire(type == function.returnType) { "586" }
                    HookOriginValueParameter
                }

                At.Instanceof -> {
                    kspRequire(type.declaration.isBuiltin(SimpleBuiltin.Instanceof)) { "591" }
                    HookOriginInstanceofWrapperParameter
                }

                At.Return -> {
                    kspRequireNotNull(hookDescriptor.returnType) { "596" }
                    kspRequire(type == hookDescriptor.returnType) { "597" }
                    HookOriginValueParameter
                }

                At.Literal -> {
                    val literal = validateLiteral(function)
                    kspRequire(literal !is NullLiteral) { "603" }
                    kspRequire(type == literal.getType(types)) { "604" }
                    HookOriginValueParameter
                }

                At.Field -> {
                    kspRequireNotNull(function.atFieldOp) { "609" }
                    val originDescriptor = validateDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is FieldDescriptor) { "611" }
                    when (function.atFieldOp) {
                        Op.Get -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.FieldGet)) { "614" }
                            HookOriginFieldGetDescriptorWrapperParameter(originDescriptor)
                        }

                        Op.Set -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.FieldSet)) { "619" }
                            HookOriginFieldSetDescriptorWrapperParameter(originDescriptor)
                        }
                    }
                }

                At.Array -> {
                    kspRequireNotNull(function.atArrayOp) { "626" }
                    val originDescriptor = validateDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is FieldDescriptor) { "628" }
                    kspRequireNotNull(originDescriptor.arrayComponentType) { "629" }
                    when (function.atArrayOp) {
                        Op.Get -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.ArrayGet)) { "632" }
                            HookOriginArrayGetDescriptorWrapperParameter(
                                originDescriptor,
                                originDescriptor.arrayComponentType
                            )
                        }

                        Op.Set -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.ArraySet)) { "640" }
                            HookOriginArraySetDescriptorWrapperParameter(
                                originDescriptor,
                                originDescriptor.arrayComponentType
                            )
                        }
                    }
                }

                At.Call -> {
                    val originDescriptor = validateDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is InvokableDescriptor) { "651" }
                    kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Call)) { "652" }
                    HookOriginCallDescriptorWrapperParameter(originDescriptor)
                }
            }

            hasCancelAnnotation -> {
                kspRequire(at != At.Body) { "658" }
                kspRequire(hookDescriptor is MethodDescriptor) { "659" }
                val cancelDescriptor = validateDescriptorReference(cancelGenericTypeClassDeclaration)
                kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Cancel)) { "661" }
                kspRequire(cancelDescriptor == hookDescriptor) { "662" }
                HookCancelDescriptorWrapperParameter(hookDescriptor)
            }

            hasOrdinalAnnotation -> {
                kspRequire(type == types.int) { "667" }
                kspRequire(function.hasOrdinals()) { "668" }
                HookOrdinalParameter
            }

            hasParamAnnotation -> {
                kspRequire(at != At.Body) { "673" }
                if (explicitParamName != null) {
                    kspRequire(explicitParamName.trim().isNotEmpty()) { "675" }
                }
                val parameterName = explicitParamName ?: name
                val parameterIndex = hookDescriptor.parameters.indexOfFirstOrNull { it.name == parameterName }
                kspRequireNotNull(parameterIndex) { "679" }
                val (paramLocalType, isVar) = validateLocalType(type)
                kspRequire(hookDescriptor.parameters[parameterIndex].type == paramLocalType) { "681" }
                HookParamLocalParameter(parameterName, paramLocalType, parameterIndex, isVar)
            }

            hasLocalAnnotation -> {
                kspRequire(at != At.Body) { "686" }
                val (bodyLocalType, isVar) = validateLocalType(type)
                HookBodyLocalParameter(
                    name,
                    bodyLocalType,
                    validateLocal(explicitLocalOrdinal, explicitLocalName, name),
                    isVar,
                )
            }

            hasShareAnnotation -> {
                kspRequire(type.declaration.isBuiltin(SimpleBuiltin.LocalVar)) { "697" }
                val type = kspRequireNotNull(type.findGenericType()) { "698" }
                if (explicitShareKey != null) {
                    kspRequire(explicitShareKey.trim().isNotEmpty()) { "700" }
                }
                HookShareLocalParameter(name, type, explicitShareKey ?: name, isShareExported)
            }

            else -> skipWithError { "705" }
        }
    }

    private fun ParsedPatchFunctionParameter.validateLocalType(type: KSType): Pair<KSType, Boolean> {
        val isVar = type.declaration.isBuiltin(SimpleBuiltin.LocalVar)
        val localType = if (isVar) {
            kspRequireNotNull(type.findGenericType()) { "712" }
        } else {
            kspRequireNotNull(type) { "714" }
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
