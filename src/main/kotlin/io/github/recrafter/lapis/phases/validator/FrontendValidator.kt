package io.github.recrafter.lapis.phases.validator

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
        kspRequire(classDeclaration?.isValid == true) { "51" }
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "52" }
        kspRequireNotNull(originJvmClassName) { "53" }
        kspRequire(originClassDeclaration?.isValid == true) { "54" }
        kspRequireNotNull(
            listOf(
                hasSchemaAnnotation,
                hasInnerSchemaAnnotation,
                hasLocalSchemaAnnotation,
                hasAnonymousSchemaAnnotation,
            ).singleOrNull { it }
        ) { "62" }
        if (hasAccessAnnotation) {
            kspRequire(isAccessible) { "64" }
        }
        val qualifiedName = kspRequireNotNull(classDeclaration.qualifiedName?.asString()) { "66" }
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
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "105" }
        kspRequire(superClassDeclaration?.isValid == true) { "106" }
        if (hasAccessAnnotation) {
            kspRequire(isAccessibleSchema) { "108" }
            kspRequire(options.accessWidenerConfigName != null || options.accessTransformerConfigName != null) { "109" }
        }
        val mappingName = if (mappingName != null) {
            kspRequire(mappingName.isNotEmpty()) { "112" }
            mappingName
        } else {
            name
        }
        val receiverType = schemaOriginClassDeclaration.starProjectedType
        if (superClassDeclaration.isBuiltin(SimpleBuiltin.Field)) {
            kspRequire(genericType is ParsedTypeDescriptorGenericType) { "119" }
            kspRequireNotNull(genericType.type) { "120" }
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
        kspRequire(genericType is ParsedFunctionTypeDescriptorGenericType) { "134" }
        kspRequire(genericType.receiverType == null) { "135" }
        val parameters = genericType.parameters.map { parameter ->
            kspRequire(!parameter.type.isFunctionType) { "137" }
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
                kspRequire(!unfinal) { "160" }
                kspRequire(genericType.returnType == null) { "161" }
                kspRequire(!hasMappingNameAnnotation) { "162" }
                ConstructorDescriptor(
                    name = name,
                    classDeclaration = classDeclaration,
                    returnType = receiverType,
                    parameters = parameters,
                    makePublic = hasAccessAnnotation,
                )
            }

            else -> skipWithError { "172" }
        }
    }

    private fun validatePatch(patch: ParsedPatch): Patch = with(patch) {
        kspRequireNotNull(name) { "177" }
        kspRequireNotNull(side) { "178" }
        kspRequireNotNull(initStrategy) { "179" }
        kspRequire(classDeclaration?.isValid == true) { "180" }
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "181" }
        kspRequire(schemaClassDeclaration?.isValid == true) { "182" }
        kspRequire(isTopLevel) { "183" }
        kspRequire(isPublic) { "184" }
        val schema = validSchemas[schemaClassDeclaration.qualifiedName?.asString()]
        kspRequireNotNull(schema) { "186" }
        kspRequire(isClass || isObject) { "187" }
        kspRequire(!isSealed) { "188" }
        val constructor = kspRequireNotNull(patch.constructors.singleOrNull()) { "189" }
        constructor.kspRequire(constructor.isPublic) { "190" }
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
        val allHooks = hooks + companionObjectHooks
        val hasStaticHooksOnly = constructorParameters.isEmpty()
            && bridgeSourceProperties.isEmpty() && bridgeSourceFunctions.isEmpty()
            && allHooks.all { it.descriptor.isStatic }
        if (!hasStaticHooksOnly) {
            kspRequire(isOpen || isAbstract) { "217" }
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
            hooks = allHooks,
        )
    }

    private fun validatePatchCompanionObject(
        companionObject: ParsedPatchCompanionObject,
    ): ParsedPatchCompanionObject = with(companionObject) {
        kspRequire(isPublic) { "241" }
        companionObject
    }

    private fun validatePatchConstructorParameter(
        parameter: ParsedPatchConstructorParameter,
        schema: Schema,
    ): PatchConstructorParameter = with(parameter) {
        when {
            hasOriginAnnotation -> {
                val instanceClassDeclaration = type.toClassDeclaration()
                kspRequire(instanceClassDeclaration == schema.originClassDeclaration) { "252" }
                kspRequire(type.arguments.none { it.variance != Variance.STAR }) { "253" }
                PatchConstructorOriginParameter
            }

            else -> skipWithError { "257" }
        }
    }

    private fun validatePatchExtensionProperty(
        property: ParsedPatchProperty,
        schema: Schema,
    ): PatchExtensionProperty = with(property) {
        kspRequire(schema.isAccessible) { "265" }
        kspRequire(property.isPublic) { "266" }
        kspRequire(!property.isAbstract) { "267" }
        kspRequire(!property.isExtension) { "268" }
        kspRequireNotNull(getterJvmName) { "269" }
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
        kspRequire(schema.isAccessible) { "283" }
        kspRequire(function.isPublic) { "284" }
        kspRequire(!function.isAbstract) { "285" }
        kspRequire(!function.isExtension) { "286" }
        PatchExtensionFunction(
            name = name,
            jvmName = jvmName,
            parameters = function.parameters.map {
                FunctionParameter(
                    name = kspRequireNotNull(it.name) { "292" },
                    type = kspRequireNotNull(it.type) { "293" },
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
        kspRequireNotNull(hookAt) { "305" }
        kspRequire(!function.hasTypeParameters) { "306" }
        val hookDescriptor = validateDescriptorReference(hookDescriptorClassDeclaration)
        kspRequire(hookDescriptor is InvokableDescriptor) { "308" }
        if (hookDescriptor.isStatic) {
            kspRequire(isInObject || isInCompanionObject) { "310" }
        } else {
            kspRequire(!isInObject) { "312" }
            kspRequire(!isInCompanionObject) { "313" }
        }
        val ordinals: (List<Int>) -> List<Int> = { validateOrdinals(it) }
        val parameters: () -> List<HookParameter> = {
            function.parameters.mapNotNull { parameter ->
                runOrNullOnSkip { validateHookParameter(parameter, function, hookAt, hookDescriptor) }
            }
        }
        when (hookAt) {
            At.Head -> {
                kspRequire(returnType == null) { "323" }
                when (hookDescriptor) {
                    is ConstructorDescriptor -> {
                        kspRequire(hasAtConstructorHeadAnnotation) { "326" }
                        ConstructorHeadHook(
                            jvmName = jvmName,
                            descriptor = hookDescriptor,
                            phase = kspRequireNotNull(atConstructorHeadPhase) { "330" },
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
                kspRequire(hookDescriptor is MethodDescriptor) { "346" }
                kspRequire(returnType == hookDescriptor.returnType) { "347" }
                BodyHook(
                    jvmName = jvmName,
                    targetDescriptor = hookDescriptor,
                    returnType = returnType,
                    parameters = parameters(),
                )
            }

            At.Tail -> {
                kspRequire(returnType == null) { "357" }
                TailHook(
                    jvmName = jvmName,
                    descriptor = hookDescriptor,
                    parameters = parameters(),
                )
            }

            At.Local -> {
                kspRequire(hasAtLocalAnnotation) { "366" }
                kspRequireNotNull(atLocalOp) { "367" }
                kspRequireNotNull(atLocalType) { "368" }
                kspRequire(returnType == atLocalType) { "369" }
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
                kspRequire(hasAtInstanceofAnnotation) { "382" }
                kspRequire(atInstanceofTypeClassDeclaration?.isValid == true) { "383" }
                kspRequire(returnType?.toClassName()?.asIrTypeName() == KPBoolean.asIrTypeName()) { "384" }
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
                kspRequire(hasAtReturnAnnotation) { "396" }
                kspRequire(returnType == hookDescriptor.returnType) { "397" }
                ReturnHook(
                    jvmName = jvmName,
                    descriptor = hookDescriptor,
                    type = returnType,
                    ordinals = ordinals(atReturnOrdinals),
                    parameters = parameters(),
                )
            }

            At.Literal -> {
                kspRequire(hasAtLiteralAnnotation) { "408" }
                val literal = validateLiteral(function)
                val type = literal.getType(types)
                if (literal !is NullLiteral) {
                    if (literal !is StringLiteral && literal !is ClassLiteral) {
                        kspRequire(returnType?.isMarkedNullable == false) { "413" }
                    }
                    kspRequire(returnType == type) { "415" }
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
                kspRequire(hasAtFieldAnnotation) { "428" }
                kspRequireNotNull(atFieldOp) { "429" }
                val targetDescriptor = validateDescriptorReference(atFieldDescriptorClassDeclaration)
                kspRequire(targetDescriptor is FieldDescriptor) { "431" }
                when (atFieldOp) {
                    Op.Get -> {
                        kspRequire(returnType?.makeNotNullable() == targetDescriptor.fieldType) { "434" }
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
                        kspRequire(returnType == null) { "446" }
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
                kspRequire(hasAtArrayAnnotation) { "460" }
                kspRequireNotNull(atArrayOp) { "461" }
                val targetDescriptor = validateDescriptorReference(atArrayDescriptorClassDeclaration)
                kspRequire(targetDescriptor is FieldDescriptor) { "463" }
                kspRequireNotNull(targetDescriptor.arrayComponentType) { "464" }
                when (atArrayOp) {
                    Op.Get -> kspRequire(returnType == targetDescriptor.arrayComponentType) { "466" }
                    Op.Set -> kspRequire(returnType == null) { "467" }
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
                kspRequire(hasAtCallAnnotation) { "482" }
                val targetDescriptor = validateDescriptorReference(atCallDescriptorClassDeclaration)
                kspRequire(targetDescriptor is MethodDescriptor) { "484" }
                kspRequire(returnType?.makeNotNullable() == targetDescriptor.returnType) { "485" }
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
                    kspRequire(it != 0) { "503" }
                    IntLiteral(it)
                },
                atLiteralExplicitFloat?.let(::FloatLiteral),
                atLiteralExplicitLong?.let(::LongLiteral),
                atLiteralExplicitDouble?.let(::DoubleLiteral),
                atLiteralExplicitString?.let(::StringLiteral),
                atLiteralExplicitClassType?.let {
                    kspRequireNotNull(atLiteralExplicitClassDeclaration?.isValid) { "511" }
                    ClassLiteral(atLiteralExplicitClassDeclaration)
                },
                atLiteralExplicitNull?.let { NullLiteral },
            ).singleOrNull()
        ) { "516" }
    }

    private fun SymbolSource.validateLocal(
        ordinal: Int?,
        explicitName: String?,
        fallbackName: String? = explicitName
    ): DomainLocal =
        kspRequireNotNull(
            when {
                ordinal != null -> ordinal.takeIf { explicitName == null }?.let {
                    kspRequire(it >= 0) { "527" }
                    PositionalLocal(it)
                }

                explicitName != null -> {
                    kspRequire(explicitName.isNotEmpty()) { "532" }
                    NamedLocal(explicitName)
                }

                fallbackName != null -> NamedLocal(fallbackName)
                else -> null
            }
        ) { "539" }

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
        kspRequire(classDeclaration?.isValid == true) { "553" }
        val qualifiedName = classDeclaration.qualifiedName?.asString()
        if (invalidDescriptors.contains(qualifiedName)) {
            skipWithError { "556" }
        }
        return validDescriptors[qualifiedName] ?: lapisError("Failed to find descriptor for $qualifiedName")
    }

    private fun validateHookParameter(
        parameter: ParsedPatchFunctionParameter,
        function: ParsedPatchFunction,
        at: At,
        hookDescriptor: InvokableDescriptor,
    ): HookParameter = with(parameter) {
        kspRequireNotNull(name) { "567" }
        kspRequireNotNull(type) { "568" }
        kspRequire(!hasDefaultArgument) { "569" }
        when {
            hasOriginAnnotation -> when (at) {
                At.Head, At.Tail -> skipWithError { "572" }

                At.Body -> {
                    val originDescriptor = validateDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is InvokableDescriptor) { "576" }
                    kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Body)) { "577" }
                    HookOriginBodyDescriptorWrapperParameter(originDescriptor)
                }

                At.Local -> {
                    kspRequire(type == function.returnType) { "582" }
                    HookOriginValueParameter
                }

                At.Instanceof -> {
                    kspRequire(type.declaration.isBuiltin(SimpleBuiltin.Instanceof)) { "587" }
                    HookOriginInstanceofWrapperParameter
                }

                At.Return -> {
                    kspRequireNotNull(hookDescriptor.returnType) { "592" }
                    kspRequire(type == hookDescriptor.returnType) { "593" }
                    HookOriginValueParameter
                }

                At.Literal -> {
                    val literal = validateLiteral(function)
                    kspRequire(literal !is NullLiteral) { "599" }
                    kspRequire(type == literal.getType(types)) { "600" }
                    HookOriginValueParameter
                }

                At.Field -> {
                    kspRequireNotNull(function.atFieldOp) { "605" }
                    val originDescriptor = validateDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is FieldDescriptor) { "607" }
                    when (function.atFieldOp) {
                        Op.Get -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.FieldGet)) { "610" }
                            HookOriginFieldGetDescriptorWrapperParameter(originDescriptor)
                        }

                        Op.Set -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.FieldSet)) { "615" }
                            HookOriginFieldSetDescriptorWrapperParameter(originDescriptor)
                        }
                    }
                }

                At.Array -> {
                    kspRequireNotNull(function.atArrayOp) { "622" }
                    val originDescriptor = validateDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is FieldDescriptor) { "624" }
                    kspRequireNotNull(originDescriptor.arrayComponentType) { "625" }
                    when (function.atArrayOp) {
                        Op.Get -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.ArrayGet)) { "628" }
                            HookOriginArrayGetDescriptorWrapperParameter(
                                originDescriptor,
                                originDescriptor.arrayComponentType
                            )
                        }

                        Op.Set -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.ArraySet)) { "636" }
                            HookOriginArraySetDescriptorWrapperParameter(
                                originDescriptor,
                                originDescriptor.arrayComponentType
                            )
                        }
                    }
                }

                At.Call -> {
                    val originDescriptor = validateDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is InvokableDescriptor) { "647" }
                    kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Call)) { "648" }
                    HookOriginCallDescriptorWrapperParameter(originDescriptor)
                }
            }

            hasCancelAnnotation -> {
                kspRequire(at != At.Body) { "654" }
                kspRequire(hookDescriptor is MethodDescriptor) { "655" }
                val cancelDescriptor = validateDescriptorReference(cancelGenericTypeClassDeclaration)
                kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Cancel)) { "657" }
                kspRequire(cancelDescriptor == hookDescriptor) { "658" }
                HookCancelDescriptorWrapperParameter(hookDescriptor)
            }

            hasOrdinalAnnotation -> {
                kspRequire(type == types.int) { "663" }
                kspRequire(function.hasOrdinals()) { "664" }
                HookOrdinalParameter
            }

            hasParamAnnotation -> {
                kspRequire(at != At.Body) { "669" }
                if (explicitParamName != null) {
                    kspRequire(explicitParamName.trim().isNotEmpty()) { "671" }
                }
                val parameterName = explicitParamName ?: name
                val parameterIndex = hookDescriptor.parameters.indexOfFirstOrNull { it.name == parameterName }
                kspRequireNotNull(parameterIndex) { "675" }
                val (paramLocalType, isVar) = validateLocalType(type)
                kspRequire(hookDescriptor.parameters[parameterIndex].type == paramLocalType) { "677" }
                HookParamLocalParameter(parameterName, paramLocalType, parameterIndex, isVar)
            }

            hasLocalAnnotation -> {
                kspRequire(at != At.Body) { "682" }
                val (bodyLocalType, isVar) = validateLocalType(type)
                HookBodyLocalParameter(
                    name,
                    bodyLocalType,
                    validateLocal(explicitLocalOrdinal, explicitLocalName, name),
                    isVar,
                )
            }

            hasShareAnnotation -> {
                kspRequire(type.declaration.isBuiltin(SimpleBuiltin.LocalVar)) { "693" }
                val type = kspRequireNotNull(type.findGenericType()) { "694" }
                if (explicitShareKey != null) {
                    kspRequire(explicitShareKey.trim().isNotEmpty()) { "696" }
                }
                HookShareLocalParameter(name, type, explicitShareKey ?: name, isShareExported)
            }

            else -> skipWithError { "701" }
        }
    }

    private fun ParsedPatchFunctionParameter.validateLocalType(type: KSType): Pair<KSType, Boolean> {
        val isVar = type.declaration.isBuiltin(SimpleBuiltin.LocalVar)
        val localType = if (isVar) {
            kspRequireNotNull(type.findGenericType()) { "708" }
        } else {
            kspRequireNotNull(type) { "710" }
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
