package io.github.recrafter.lapis.phases.validator

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.diskria.poetesse.java.JPModifier
import io.github.recrafter.lapis.annotations.AccessStrategy
import io.github.recrafter.lapis.annotations.Ats
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.common.JavaModifiers
import io.github.recrafter.lapis.common.JvmClassName
import io.github.recrafter.lapis.common.KSBaseTypes
import io.github.recrafter.lapis.common.findArrayComponentType
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.indexOfFirstOrNull
import io.github.recrafter.lapis.extensions.kp.KPBoolean
import io.github.recrafter.lapis.extensions.ks.isValid
import io.github.recrafter.lapis.extensions.ks.starProjectedType
import io.github.recrafter.lapis.extensions.ks.toClassDeclaration
import io.github.recrafter.lapis.logging.Logger
import io.github.recrafter.lapis.phases.bootstrap.Options
import io.github.recrafter.lapis.phases.builtins.Builtin
import io.github.recrafter.lapis.phases.builtins.Builtins
import io.github.recrafter.lapis.phases.builtins.DescriptorWrapperBuiltin
import io.github.recrafter.lapis.phases.builtins.SimpleBuiltin
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.parser.models.ParserResult
import io.github.recrafter.lapis.phases.parser.models.common.*
import io.github.recrafter.lapis.phases.parser.models.patches.*
import io.github.recrafter.lapis.phases.parser.models.schemas.ParsedDescriptor
import io.github.recrafter.lapis.phases.parser.models.schemas.ParsedDescriptorGenericArgumentFunctionType
import io.github.recrafter.lapis.phases.parser.models.schemas.ParsedDescriptorGenericArgumentSimpleType
import io.github.recrafter.lapis.phases.parser.models.schemas.ParsedSchema
import io.github.recrafter.lapis.phases.validator.models.ValidatorResult
import io.github.recrafter.lapis.phases.validator.models.common.*
import io.github.recrafter.lapis.phases.validator.models.patches.*
import io.github.recrafter.lapis.phases.validator.models.patches.hooks.*
import io.github.recrafter.lapis.phases.validator.models.schemas.*
import javax.lang.model.element.Modifier
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class FrontendValidator(
    private val logger: Logger,
    private val options: Options,
    private val builtins: Builtins,
    private val baseTypes: KSBaseTypes,
) {
    private val validSchemas: MutableMap<String, Schema> = mutableMapOf()
    private val invalidSchemas: MutableList<String> = mutableListOf()

    private val validDescriptors: MutableMap<String, Descriptor> = mutableMapOf()
    private val invalidDescriptors: MutableList<String> = mutableListOf()

    fun validate(result: ParserResult): ValidatorResult =
        ValidatorResult(
            schemas = validateSchemas(result.schemas),
            patches = result.patches.mapNotNull {
                runOrNullOnSkip { it.validate() }
            },
        )

    private fun validateSchemas(parsedSchemas: List<ParsedSchema>): List<Schema> =
        parsedSchemas.flatMap { parsedSchema ->
            val qualifiedName = parsedSchema.classDeclaration.qualifiedName?.asString() ?: return@flatMap emptyList()
            val schema = runOrNullOnSkip { parsedSchema.validate() }
            return@flatMap if (schema != null) {
                validSchemas[qualifiedName] = schema
                listOf(schema) + validateSchemas(parsedSchema.nestedSchemas)
            } else {
                invalidSchemas += qualifiedName
                emptyList()
            }
        }

    private fun ParsedSchema.validate(): Schema {
        validateClassDeclaration(classDeclaration)
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "80" }
        kspRequireNotNull(originJvmClassName) { "81" }
        validateClassDeclaration(originClassDeclaration)
        kspRequire(
            listOf(
                hasClassAnnotation,
                hasInnerClassAnnotation,
                hasLocalClassAnnotation,
                hasAnonymousClassAnnotation,
            ).count { it } == 1
        ) { "90" }
        if (hasClassAnnotation) {
            kspRequire(isTopLevel) { "92" }
        }
        kspRequire(hasPackageName) { "94" }
        val accessRequest = resolveAccessRequest(
            AccessMember.CLASS,
            hasAccessAnnotation, accessStrategy, isAccessUnfinal, isAccessible,
            emptyList(), emptyList(),
        )
        val descriptors = descriptors.mapNotNull { parsedDescriptor ->
            val qualifiedName = parsedDescriptor.classDeclaration.qualifiedName?.asString() ?: return@mapNotNull null
            val descriptor = runOrNullOnSkip {
                parsedDescriptor.validate(originClassDeclaration, originJvmClassName, isAccessible)
            }
            if (descriptor != null) {
                validDescriptors[qualifiedName] = descriptor
            } else {
                invalidDescriptors += qualifiedName
            }
            return@mapNotNull descriptor
        }
        return Schema(
            symbol = symbol,
            classDeclaration = classDeclaration,

            originJvmClassName = originJvmClassName,
            originClassDeclaration = originClassDeclaration,
            side = side,
            isAccessible = isAccessible,
            accessRequest = accessRequest,
            descriptors = descriptors,
        )
    }

    private fun ParsedDescriptor.validate(
        schemaOriginClassDeclaration: KSClassDeclaration,
        schemaOriginJvmClassName: JvmClassName,
        isAccessibleSchema: Boolean,
    ): Descriptor {
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "130" }
        kspRequire(
            listOf(
                hasFieldAnnotation,
                hasMethodAnnotation,
                hasConstructorAnnotation,
            ).count { it } == 1
        ) { "139" }
        val mappingName = resolveMappingName(explicitMappingName, name)
        val receiverType = schemaOriginClassDeclaration.starProjectedType
        if (hasFieldAnnotation) {
            kspRequire(genericArgument is ParsedDescriptorGenericArgumentSimpleType) { "136" }
            validateType(genericArgument.type)
            val accessRequest = resolveAccessRequest(
                AccessMember.FIELD,
                hasAccessAnnotation, accessStrategy, isAccessUnfinal, isAccessibleSchema,
                accessFieldOps, emptyList(),
            )
            if (accessRequest != null && accessRequest !is TweakAccessRequest && (isStatic || !isAccessibleSchema)) {
                kspRequire(isObject) { "195" }
            }
            return FieldDescriptor(
                symbol = symbol,
                classDeclaration = classDeclaration,

                name = name,
                receiverType = receiverType,
                inaccessibleReceiverJvmClassName = if (isAccessibleSchema) null else schemaOriginJvmClassName,
                mappingName = mappingName,
                fieldType = genericArgument.type,
                arrayComponentType = genericArgument.type.findArrayComponentType(
                    baseTypes,
                    genericArgument.typeArguments.filterNotNull()
                ),
                isStatic = isStatic,
                accessRequest = accessRequest,
            )
        }
        kspRequire(genericArgument is ParsedDescriptorGenericArgumentFunctionType) { "160" }
        kspRequire(genericArgument.receiverType == null) { "161" }
        val functionTypeParameters = genericArgument.parameters.map { parameter ->
            val type = kspRequireNotNull(parameter.type) { "170" }
            kspRequire(!type.isFunctionType) { "163" }
            FunctionTypeParameter(
                type = type,
                name = parameter.name,
            )
        }
        val accessRequest = resolveAccessRequest(
            AccessMember.INVOKABLE,
            hasAccessAnnotation, accessStrategy, isAccessUnfinal, isAccessibleSchema,
            emptyList(), functionTypeParameters,
        )
        if (accessRequest != null && accessRequest !is TweakAccessRequest &&
            (hasConstructorAnnotation || isStatic || !isAccessibleSchema)
        ) {
            kspRequire(isObject) { "195" }
        }
        return when {
            hasMethodAnnotation -> {
                MethodDescriptor(
                    symbol = symbol,
                    classDeclaration = classDeclaration,

                    name = name,
                    receiverType = receiverType,
                    inaccessibleReceiverJvmClassName = if (isAccessibleSchema) null else schemaOriginJvmClassName,
                    returnType = genericArgument.returnType,
                    mappingName = mappingName,
                    functionTypeParameters = functionTypeParameters,
                    isStatic = isStatic,
                    accessRequest = accessRequest,
                )
            }

            hasConstructorAnnotation -> {
                kspRequire(genericArgument.returnType == null) { "192" }
                kspRequire(!hasMappingNameAnnotation) { "193" }
                if (accessRequest is MixinAccessRequest) {
                    kspRequire(isAccessibleSchema) { "195" }
                }
                ConstructorDescriptor(
                    symbol = symbol,
                    classDeclaration = classDeclaration,

                    name = name,
                    returnType = receiverType,
                    functionTypeParameters = functionTypeParameters,
                    accessRequest = accessRequest,
                )
            }

            else -> skipWithError { "208" }
        }
    }

    private fun ParsedPatch.validate(): Patch {
        kspRequireNotNull(name) { "213" }
        kspRequireNotNull(initStrategy) { "214" }
        validateClassDeclaration(classDeclaration)
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "216" }
        kspRequire(isTopLevel) { "217" }
        kspRequire(hasPackageName) { "218" }
        kspRequire(isPublic) { "219" }
        val mixinAnnotations = resolveMixinAnnotations(annotations)
        val (isAccessibleTarget, originClassDeclaration) = if (targetClassDeclaration != null) {
            validateClassDeclaration(targetClassDeclaration)
            val qualifiedName = targetClassDeclaration.qualifiedName?.asString()
            val schema = validSchemas[qualifiedName]
            if (schema != null) {
                schema.isAccessible to schema.originClassDeclaration
            } else {
                kspRequire(qualifiedName !in invalidSchemas) { "228" }
                true to targetClassDeclaration
            }
        } else {
            kspRequire(mixinAnnotations.isNotEmpty()) { "232" }
            false to null
        }
        kspRequire(isClass) { "235" }
        kspRequire(!isObject) { "236" }
        kspRequire(!isSealed) { "237" }
        kspRequire(!isOpen) { "238" }
        val constructor = kspRequireNotNull(constructors.singleOrNull()) { "239" }
        constructor.kspRequire(constructor.isPublic) { "240" }
        val constructorParameters = constructor.parameters.mapNotNull {
            runOrNullOnSkip { it.validate(originClassDeclaration) }
        }
        val extensionProperties = bodyProperties.filter { it.hasExtensionAnnotation }.mapNotNull {
            runOrNullOnSkip { it.validateAsExtension(isAccessibleTarget, originClassDeclaration) }
        }
        val (parsedInjectionFunctions, parsedRegularFunctions) = functions.partition {
            it.hasHookAnnotation || resolveMixinAnnotations(it.annotations).isNotEmpty()
        }
        val extensionFunctions = parsedRegularFunctions.filter { it.hasExtensionAnnotation }.mapNotNull {
            runOrNullOnSkip { it.validateAsExtension(isAccessibleTarget, originClassDeclaration) }
        }
        val shadowProperties = bodyProperties.filter { it.hasShadowAnnotation }.mapNotNull {
            runOrNullOnSkip { it.validateAsShadow() }
        }
        val shadowFunctions = parsedRegularFunctions.filter { it.hasShadowAnnotation }.mapNotNull {
            runOrNullOnSkip { it.validateAsShadow() }
        }
        val injections = parsedInjectionFunctions.mapNotNull {
            runOrNullOnSkip { it.validateAsInjection(isInCompanionObject = false, originClassDeclaration) }
        }
        val companionObjects = companionObjects.mapNotNull {
            runOrNullOnSkip { it.validate() }
        }
        val companionObjectInjections = companionObjects.flatMap { companionObject ->
            companionObject.functions.mapNotNull {
                runOrNullOnSkip { it.validateAsInjection(isInCompanionObject = true, originClassDeclaration) }
            }
        }
        val hasStaticHooksOnly = constructorParameters.isEmpty()
            && extensionProperties.isEmpty() && extensionFunctions.isEmpty()
            && shadowProperties.isEmpty() && shadowFunctions.isEmpty()
            && injections.all { it.isStatic }
        if (!hasStaticHooksOnly) {
            kspRequire(isAbstract) { "275" }
        }
        return Patch(
            symbol = symbol,
            classDeclaration = classDeclaration,

            name = name,
            side = side,
            initStrategy = initStrategy,
            isImplRequired = !hasStaticHooksOnly,
            targetJvmClassName = originClassDeclaration?.qualifiedName?.asString()?.let { JvmClassName.of(it) },

            constructorParameters = constructorParameters,
            extensionSources = extensionProperties + extensionFunctions,
            shadowSources = shadowProperties + shadowFunctions,
            injections = injections + companionObjectInjections,
            mixinAnnotations = resolveMixinAnnotations(annotations),
        )
    }

    private fun ParsedPatchCompanionObject.validate(): ParsedPatchCompanionObject {
        kspRequire(isPublic) { "296" }
        return this
    }

    private fun ParsedPatchConstructorParameter.validate(
        originClassDeclaration: KSClassDeclaration?
    ): PatchConstructorParameter {
        validateType(type)
        return when {
            hasOriginAnnotation -> {
                val typeClassDeclaration = type.toClassDeclaration()
                validateClassDeclaration(typeClassDeclaration)
                if (originClassDeclaration != null) {
                    kspRequire(typeClassDeclaration == originClassDeclaration) { "308" }
                }
                kspRequire(type.arguments.none { it.variance != Variance.STAR }) { "309" }
                PatchConstructorOriginParameter(typeClassDeclaration)
            }

            else -> skipWithError { "313" }
        }
    }

    private fun ParsedPatchProperty.validateAsExtension(
        isAccessibleTarget: Boolean,
        receiverClassDeclaration: KSClassDeclaration?,
    ): ExtensionProperty {
        validateType(type)
        kspRequireNotNull(getter) { "322" }
        kspRequireNotNull(getter.jvmName) { "323" }
        kspRequire(isPublic) { "324" }
        kspRequire(!hasExtensionReceiver) { "325" }
        kspRequire(isAccessibleTarget) { "326" }
        kspRequire(!isOpen && !isAbstract) { "327" }
        validateClassDeclaration(receiverClassDeclaration)
        return ExtensionProperty(
            name = name,
            getterJvmName = getter.jvmName,
            setterJvmName = if (setter != null) kspRequireNotNull(setter.jvmName) { "332" } else null,
            type = type,
            receiverClassDeclaration = receiverClassDeclaration,
        )
    }

    private fun ParsedPatchFunction.validateAsExtension(
        isAccessibleTarget: Boolean,
        receiverClassDeclaration: KSClassDeclaration?,
    ): ExtensionFunction {
        kspRequire(isPublic) { "342" }
        kspRequireNotNull(jvmName) { "343" }
        if (receiverClassDeclaration != null) {
            kspRequire(extensionReceiverClassDeclaration == receiverClassDeclaration) { "308" }
        }
        kspRequire(isAccessibleTarget) { "345" }
        kspRequire(!isOpen && !isAbstract) { "346" }
        val parameters = parameters.map {
            FunctionParameter(
                name = kspRequireNotNull(it.name) { "349" },
                type = validateType(it.type),
            )
        }
        validateClassDeclaration(receiverClassDeclaration)
        return ExtensionFunction(
            name = name,
            jvmName = jvmName,
            parameters = parameters,
            returnType = returnType,
            receiverClassDeclaration = receiverClassDeclaration,
        )
    }

    private fun ParsedPatchProperty.validateAsShadow(): ShadowProperty {
        validateType(type)
        kspRequire(isPublic) { "365" }
        kspRequire(isAbstract) { "366" }
        kspRequire(!hasExtensionReceiver) { "367" }
        kspRequireNotNull(getter) { "368" }
        kspRequireNotNull(getter.jvmName) { "369" }
        val mappingName = resolveMappingName(explicitMappingName, name)
        val shadowModifiers = resolveModifiers(shadowModifiers, isMethod = false)
        return ShadowProperty(
            name = name,
            getterJvmName = getter.jvmName,
            setterJvmName = if (setter != null) kspRequireNotNull(setter.jvmName) { "375" } else null,
            mappingName = mappingName,
            modifiers = shadowModifiers,
            type = type,
            mixinAnnotations = resolveMixinAnnotations(getter.annotations),
        )
    }

    private fun ParsedPatchFunction.validateAsShadow(): ShadowFunction {
        kspRequire(isPublic) { "384" }
        kspRequireNotNull(jvmName) { "385" }
        kspRequire(isAbstract) { "386" }
        kspRequire(extensionReceiverClassDeclaration == null) { "387" }
        val parameters = parameters.map {
            FunctionParameter(
                name = kspRequireNotNull(it.name) { "390" },
                type = validateType(it.type),
            )
        }
        val mappingName = resolveMappingName(explicitMappingName, name)
        val shadowModifiers = resolveModifiers(shadowModifiers, isMethod = true)
        return ShadowFunction(
            name = name,
            jvmName = jvmName,
            parameters = parameters,
            returnType = returnType,
            mappingName = mappingName,
            mixinAnnotations = resolveMixinAnnotations(annotations),
            modifiers = shadowModifiers,
        )
    }

    private fun ParsedPatchFunction.validateAsInjection(
        isInCompanionObject: Boolean,
        originClassDeclaration: KSClassDeclaration?,
    ): PatchInjection {
        kspRequireNotNull(jvmName) { "408" }
        kspRequire(!hasTypeParameters) { "409" }
        kspRequire(!isOpen) { "410" }
        val mixinAnnotations = resolveMixinAnnotations(annotations)
        if (hasHookAnnotation) {
            kspRequire(mixinAnnotations.isEmpty()) { "413" }
        }
        if (isInCompanionObject) {
            kspRequire(extensionReceiverClassDeclaration == null) { "438" }
        } else if (originClassDeclaration != null && extensionReceiverClassDeclaration != null) {
            kspRequire(extensionReceiverClassDeclaration == originClassDeclaration) { "441" }
        }
        if (mixinAnnotations.isNotEmpty()) {
            kspRequire(!hasHookAnnotation) { "416" }
            return PatchNativeInjection(
                jvmName = jvmName,
                extensionReceiver = extensionReceiverClassDeclaration,
                mixinAnnotations = mixinAnnotations,
                isStatic = isInCompanionObject,
                parameters = parameters.map { it.validateAsNativeInjectionParameter() },
                returnType = returnType,
            )
        }
        kspRequireNotNull(hookAt) { "425" }
        val hookMethodDescriptor = resolveDescriptor(hookDescClassDeclaration)
        kspRequire(hookMethodDescriptor is InvokableDescriptor) { "427" }
        if (hookMethodDescriptor.isStatic) {
            kspRequire(isInCompanionObject) { "429" }
        } else {
            kspRequire(!isInCompanionObject) { "431" }
        }
        val parameters: () -> List<HookParameter> = {
            parameters.mapNotNull { parameter ->
                runOrNullOnSkip {
                    parameter.validateAsHookParameter(
                        this@validateAsInjection,
                        hookAt,
                        hookMethodDescriptor,
                    )
                }
            }
        }
        return when (hookAt) {
            Ats.Head -> {
                kspRequire(returnType == null) { "446" }
                when (hookMethodDescriptor) {
                    is ConstructorDescriptor -> {
                        kspRequire(hasAtConstructorHeadAnnotation) { "449" }
                        ConstructorHeadHook(
                            jvmName = jvmName,
                            extensionReceiver = extensionReceiverClassDeclaration,
                            methodDescriptor = hookMethodDescriptor,
                            phase = kspRequireNotNull(atConstructorHeadPhase) { "453" },
                            parameters = parameters(),
                        )
                    }

                    is MethodDescriptor -> MethodHeadHook(
                        jvmName = jvmName,
                        extensionReceiver = extensionReceiverClassDeclaration,
                        methodDescriptor = hookMethodDescriptor,
                        parameters = parameters(),
                    )
                }
            }

            Ats.Body -> {
                kspRequire(hookMethodDescriptor is MethodDescriptor) { "467" }
                kspRequire(returnType == hookMethodDescriptor.returnType) { "468" }
                BodyHook(
                    jvmName = jvmName,
                    extensionReceiver = extensionReceiverClassDeclaration,
                    methodDescriptor = hookMethodDescriptor,
                    returnType = returnType,
                    parameters = parameters(),
                )
            }

            Ats.Tail -> {
                kspRequire(returnType == null) { "478" }
                TailHook(
                    jvmName = jvmName,
                    extensionReceiver = extensionReceiverClassDeclaration,
                    methodDescriptor = hookMethodDescriptor,
                    parameters = parameters(),
                )
            }

            Ats.Local -> {
                kspRequire(hasAtLocalAnnotation) { "487" }
                kspRequireNotNull(atLocalOp) { "488" }
                kspRequire(returnType == validateType(atLocalType)) { "489" }
                LocalHook(
                    jvmName = jvmName,
                    extensionReceiver = extensionReceiverClassDeclaration,
                    methodDescriptor = hookMethodDescriptor,
                    type = atLocalType,
                    ordinals = resolveOrdinals(atLocalOpOrdinals),
                    local = resolveLocal(explicitAtLocalOrdinal, explicitAtLocalName, null),
                    op = atLocalOp,
                    parameters = parameters(),
                )
            }

            Ats.Instanceof -> {
                kspRequire(hasAtInstanceofAnnotation) { "502" }
                validateClassDeclaration(atInstanceofTypeClassDeclaration)
                kspRequire(returnType?.toClassName()?.asIrTypeName() == KPBoolean.asIrTypeName()) { "504" }
                InstanceofHook(
                    jvmName = jvmName,
                    extensionReceiver = extensionReceiverClassDeclaration,
                    methodDescriptor = hookMethodDescriptor,
                    typeClassDeclaration = atInstanceofTypeClassDeclaration,
                    returnType = returnType,
                    ordinals = resolveOrdinals(atInstanceofOrdinals),
                    parameters = parameters(),
                )
            }

            Ats.Return -> {
                kspRequire(hasAtReturnAnnotation) { "516" }
                kspRequire(returnType == hookMethodDescriptor.returnType) { "517" }
                ReturnHook(
                    jvmName = jvmName,
                    extensionReceiver = extensionReceiverClassDeclaration,
                    methodDescriptor = hookMethodDescriptor,
                    type = returnType,
                    ordinals = resolveOrdinals(atReturnOrdinals),
                    parameters = parameters(),
                )
            }

            Ats.Literal -> {
                kspRequire(hasAtLiteralAnnotation) { "528" }
                val literal = resolveLiteral(this@validateAsInjection)
                val type = literal.getType(baseTypes)
                if (literal !is NullHookLiteral) {
                    if (literal !is StringHookLiteral && literal !is ClassHookLiteral) {
                        kspRequire(returnType?.isMarkedNullable == false) { "533" }
                    }
                    kspRequire(returnType == type) { "535" }
                }
                LiteralHook(
                    jvmName = jvmName,
                    extensionReceiver = extensionReceiverClassDeclaration,
                    methodDescriptor = hookMethodDescriptor,
                    type = type,
                    literal = literal,
                    ordinals = resolveOrdinals(atLiteralOrdinals),
                    parameters = parameters(),
                )
            }

            Ats.Field -> {
                kspRequire(hasAtFieldAnnotation) { "548" }
                kspRequireNotNull(atFieldOp) { "549" }
                val targetDescriptor = resolveDescriptor(atFieldDescClassDeclaration)
                kspRequire(targetDescriptor is FieldDescriptor) { "551" }
                when (atFieldOp) {
                    Op.Get -> {
                        kspRequire(returnType?.makeNotNullable() == targetDescriptor.fieldType) { "554" }
                        FieldGetHook(
                            jvmName = jvmName,
                            extensionReceiver = extensionReceiverClassDeclaration,
                            methodDescriptor = hookMethodDescriptor,
                            type = targetDescriptor.fieldType,
                            ordinals = resolveOrdinals(atFieldOrdinals),
                            targetDescriptor = targetDescriptor,
                            parameters = parameters(),
                        )
                    }

                    Op.Set -> {
                        kspRequire(returnType == null) { "566" }
                        FieldSetHook(
                            jvmName = jvmName,
                            extensionReceiver = extensionReceiverClassDeclaration,
                            methodDescriptor = hookMethodDescriptor,
                            type = targetDescriptor.fieldType,
                            ordinals = resolveOrdinals(atFieldOrdinals),
                            targetDescriptor = targetDescriptor,
                            parameters = parameters(),
                        )
                    }
                }
            }

            Ats.Array -> {
                kspRequire(hasAtArrayAnnotation) { "580" }
                kspRequireNotNull(atArrayOp) { "581" }
                val targetDescriptor = resolveDescriptor(atArrayDescClassDeclaration)
                kspRequire(targetDescriptor is FieldDescriptor) { "583" }
                kspRequireNotNull(targetDescriptor.arrayComponentType) { "584" }
                validateType(targetDescriptor.arrayComponentType)
                when (atArrayOp) {
                    Op.Get -> kspRequire(returnType == targetDescriptor.arrayComponentType) { "587" }
                    Op.Set -> kspRequire(returnType == null) { "588" }
                }
                ArrayHook(
                    jvmName = jvmName,
                    extensionReceiver = extensionReceiverClassDeclaration,
                    methodDescriptor = hookMethodDescriptor,
                    op = atArrayOp,
                    type = targetDescriptor.fieldType,
                    componentType = targetDescriptor.arrayComponentType,
                    targetDescriptor = targetDescriptor,
                    ordinals = resolveOrdinals(atArrayOrdinals),
                    parameters = parameters(),
                )
            }

            Ats.Call -> {
                kspRequire(hasAtCallAnnotation) { "603" }
                val targetDescriptor = resolveDescriptor(atCallDescClassDeclaration)
                kspRequire(targetDescriptor is MethodDescriptor) { "605" }
                kspRequire(returnType?.makeNotNullable() == targetDescriptor.returnType) { "606" }
                CallHook(
                    jvmName = jvmName,
                    extensionReceiver = extensionReceiverClassDeclaration,
                    methodDescriptor = hookMethodDescriptor,
                    returnType = returnType,
                    targetDescriptor = targetDescriptor,
                    ordinals = resolveOrdinals(atCallOrdinals),
                    parameters = parameters(),
                )
            }
        }
    }

    private fun ParsedPatchFunctionParameter.validateAsNativeInjectionParameter(): PatchNativeInjectionParameter {
        kspRequireNotNull(name) { "620" }
        kspRequireNotNull(type) { "621" }
        return PatchNativeInjectionParameter(name, type, resolveMixinAnnotations(annotations))
    }

    private fun ParsedPatchFunctionParameter.validateAsHookParameter(
        function: ParsedPatchFunction,
        at: Ats,
        hookDescriptor: InvokableDescriptor,
    ): HookParameter {
        kspRequireNotNull(name) { "630" }
        validateType(type)
        kspRequire(!hasDefaultArgument) { "632" }
        return when {
            hasOriginAnnotation -> when (at) {
                Ats.Head, Ats.Tail -> skipWithError { "635" }

                Ats.Body -> {
                    val originDescriptor = resolveDescriptor(typeArguments.singleOrNull()?.toClassDeclaration())
                    kspRequire(originDescriptor is MethodDescriptor) { "639" }
                    kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Body)) { "640" }
                    HookOriginBodyDescriptorWrapperParameter(originDescriptor)
                }

                Ats.Local -> {
                    kspRequire(type == function.returnType) { "645" }
                    HookOriginValueParameter
                }

                Ats.Instanceof -> {
                    kspRequire(type.declaration.isBuiltin(SimpleBuiltin.Instanceof)) { "650" }
                    HookOriginInstanceofWrapperParameter
                }

                Ats.Return -> {
                    kspRequireNotNull(hookDescriptor.returnType) { "655" }
                    kspRequire(type == hookDescriptor.returnType) { "656" }
                    HookOriginValueParameter
                }

                Ats.Literal -> {
                    val literal = resolveLiteral(function)
                    kspRequire(literal !is NullHookLiteral) { "662" }
                    kspRequire(type == literal.getType(baseTypes)) { "663" }
                    HookOriginValueParameter
                }

                Ats.Field -> {
                    kspRequireNotNull(function.atFieldOp) { "668" }
                    val originDescriptor = resolveDescriptor(typeArguments.singleOrNull()?.toClassDeclaration())
                    kspRequire(originDescriptor is FieldDescriptor) { "670" }
                    when (function.atFieldOp) {
                        Op.Get -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.FieldGet)) { "673" }
                            HookOriginFieldGetDescriptorWrapperParameter(originDescriptor)
                        }

                        Op.Set -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.FieldSet)) { "678" }
                            HookOriginFieldSetDescriptorWrapperParameter(originDescriptor)
                        }
                    }
                }

                Ats.Array -> {
                    kspRequireNotNull(function.atArrayOp) { "685" }
                    val originDescriptor = resolveDescriptor(typeArguments.singleOrNull()?.toClassDeclaration())
                    kspRequire(originDescriptor is FieldDescriptor) { "687" }
                    kspRequireNotNull(originDescriptor.arrayComponentType) { "688" }
                    validateType(originDescriptor.arrayComponentType)
                    when (function.atArrayOp) {
                        Op.Get -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.ArrayGet)) { "692" }
                            HookOriginArrayGetDescriptorWrapperParameter(
                                originDescriptor,
                                originDescriptor.arrayComponentType
                            )
                        }

                        Op.Set -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.ArraySet)) { "700" }
                            HookOriginArraySetDescriptorWrapperParameter(
                                originDescriptor,
                                originDescriptor.arrayComponentType
                            )
                        }
                    }
                }

                Ats.Call -> {
                    val originDescriptor = resolveDescriptor(typeArguments.singleOrNull()?.toClassDeclaration())
                    kspRequire(originDescriptor is InvokableDescriptor) { "711" }
                    kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Call)) { "712" }
                    HookOriginCallDescriptorWrapperParameter(originDescriptor)
                }
            }

            hasCancelAnnotation -> {
                kspRequire(at != Ats.Body) { "718" }
                kspRequire(hookDescriptor is MethodDescriptor) { "719" }
                val cancelDescriptor = resolveDescriptor(typeArguments.singleOrNull()?.toClassDeclaration())
                kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Cancel)) { "721" }
                kspRequire(cancelDescriptor == hookDescriptor) { "722" }
                HookCancelDescriptorWrapperParameter(hookDescriptor)
            }

            hasOrdinalAnnotation -> {
                kspRequire(type == baseTypes.int) { "727" }
                kspRequire(function.hasOrdinals()) { "728" }
                HookOrdinalParameter
            }

            hasParamAnnotation -> {
                kspRequire(at != Ats.Body) { "733" }
                explicitParamName?.let { kspRequire(it.isNotEmpty()) { "734" } }
                val parameterName = explicitParamName ?: name
                val parameterIndex = hookDescriptor.functionTypeParameters.indexOfFirstOrNull {
                    it.name == parameterName
                }
                kspRequireNotNull(parameterIndex) { "739" }
                val (parameterLocalType, isLocalVar) = resolveLocalType(type, typeArguments)
                kspRequire(hookDescriptor.functionTypeParameters[parameterIndex].type == parameterLocalType) { "741" }
                HookParamLocalParameter(parameterName, parameterLocalType, parameterIndex, isLocalVar)
            }

            hasLocalAnnotation -> {
                kspRequire(at != Ats.Body) { "746" }
                val (bodyLocalType, isLocalVar) = resolveLocalType(type, typeArguments)
                HookBodyLocalParameter(
                    name,
                    bodyLocalType,
                    resolveLocal(explicitLocalOrdinal, explicitLocalName, name),
                    isLocalVar,
                )
            }

            hasShareAnnotation -> {
                kspRequire(type.declaration.isBuiltin(SimpleBuiltin.LocalVar)) { "757" }
                val type = validateType(typeArguments.singleOrNull())
                explicitShareKey?.let { kspRequire(it.isNotEmpty()) { "759" } }
                HookShareLocalParameter(name, type, explicitShareKey ?: name, isShareExported)
            }

            else -> skipWithError { "763" }
        }
    }

    @OptIn(ExperimentalContracts::class)
    private fun SymbolSource.validateType(type: KSType?): KSType {
        contract { returns() implies (type != null) }
        kspRequire(type?.isValid == true) { "770" }
        return type
    }

    @OptIn(ExperimentalContracts::class)
    private fun SymbolSource.validateClassDeclaration(classDeclaration: KSClassDeclaration?): KSClassDeclaration {
        contract { returns() implies (classDeclaration != null) }
        kspRequire(classDeclaration?.isValid == true) { "777" }
        return classDeclaration
    }

    private fun SymbolSource.resolveLiteral(function: ParsedPatchFunction): HookLiteral =
        kspRequireNotNull(
            with(function) {
                listOfNotNull(
                    explicitAtLiteralZero?.let { ZeroHookLiteral(atLiteralZeroConditions) },
                    explicitAtLiteralInt?.let {
                        kspRequire(it != 0) { "787" }
                        IntHookLiteral(it)
                    },
                    explicitAtLiteralLong?.let(::LongHookLiteral),
                    explicitAtLiteralFloat?.let(::FloatHookLiteral),
                    explicitAtLiteralDouble?.let(::DoubleHookLiteral),
                    explicitAtLiteralString?.let(::StringHookLiteral),
                    explicitAtLiteralType?.let {
                        ClassHookLiteral(validateClassDeclaration(explicitAtLiteralTypeClassDeclaration))
                    },
                    if (isExplicitAtLiteralNull) NullHookLiteral else null,
                ).singleOrNull()
            }
        ) { "800" }

    private fun SymbolSource.resolveOrdinals(ordinals: List<Int>): Set<Int> {
        val invalidOrdinals = ordinals.filter { it < 0 }
        if (invalidOrdinals.isNotEmpty()) {
            invalidOrdinals.forEach {
                kspError { "Ordinal cannot be negative, but found: $it" }
            }
            skipSymbol()
        }
        return ordinals.toSet()
    }

    private enum class AccessMember { CLASS, FIELD, INVOKABLE }

    private fun SymbolSource.resolveAccessRequest(
        member: AccessMember,
        hasAccessAnnotation: Boolean,
        accessStrategy: AccessStrategy?,
        isAccessUnfinal: Boolean,
        isAccessibleSchema: Boolean,
        fieldOps: List<Op>,
        functionTypeParameters: List<FunctionTypeParameter>,
    ): AccessRequest? {
        if (!hasAccessAnnotation) return null
        kspRequireNotNull(accessStrategy) { "825" }
        return when (accessStrategy) {
            AccessStrategy.Tweak -> {
                kspRequire(isAccessibleSchema) { "828" }
                kspRequire(options.accessWidenerConfig != null || options.accessTransformerConfig != null) { "829" }
                TweakAccessRequest(isAccessUnfinal)
            }

            AccessStrategy.Mixin -> when (member) {
                AccessMember.CLASS -> skipWithError { "834" }
                AccessMember.FIELD -> {
                    kspRequire(fieldOps.isNotEmpty()) { "836" }
                    MixinFieldAccessRequest(isAccessUnfinal, fieldOps)
                }

                AccessMember.INVOKABLE -> {
                    kspRequire(!isAccessUnfinal) { "841" }
                    val parameters = mutableListOf<IrParameter>()
                    val anonymousParameterIndices = mutableListOf<Int>()
                    functionTypeParameters.forEachIndexed { index, functionTypeParameter ->
                        val name = functionTypeParameter.name
                        if (name != null) {
                            parameters += IrParameter(name, functionTypeParameter.typeName)
                        } else {
                            anonymousParameterIndices += index
                        }
                    }
                    kspRequire(anonymousParameterIndices.isEmpty()) { "852" }
                    MixinInvokableAccessRequest(parameters)
                }
            }

            AccessStrategy.Reflection -> TODO()
        }
    }

    private fun SymbolSource.resolveModifiers(modifiers: List<Modifier>, isMethod: Boolean): Set<Modifier> {
        val set = modifiers.toSet()
        val allowed = if (isMethod) JavaModifiers.methodAllowed else JavaModifiers.fieldAllowed
        kspRequire(allowed.containsAll(set)) { "862" }
        kspRequire(set.count { it in JavaModifiers.visibilities } <= 1) { "863" }
        if (isMethod) {
            kspRequire(set.count { it in JavaModifiers.methodConflicts } <= 1) { "865" }
            if (JPModifier.ABSTRACT in set) {
                kspRequire(set.none { it in JavaModifiers.abstractIllegals }) { "867" }
            }
            if (Modifier.NATIVE in set) {
                kspRequire(Modifier.DEFAULT !in set) { "870" }
            }
        } else {
            if (Modifier.FINAL in set) {
                kspRequire(Modifier.VOLATILE !in set) { "874" }
            }
        }
        return set
    }

    private fun SymbolSource.resolveDescriptor(classDeclaration: KSClassDeclaration?): Descriptor {
        validateClassDeclaration(classDeclaration)
        val qualifiedName = classDeclaration.qualifiedName?.asString()
        kspRequire(qualifiedName !in invalidDescriptors) { "883" }
        return validDescriptors[qualifiedName] ?: lapisError("Descriptor cannot be null")
    }

    private fun SymbolSource.resolveLocalType(type: KSType, typeArguments: List<KSType?>): Pair<KSType, Boolean> {
        val isLocalVar = type.declaration.isBuiltin(SimpleBuiltin.LocalVar)
        val localType = validateType(if (isLocalVar) typeArguments.singleOrNull() else type)
        return localType to isLocalVar
    }

    private fun SymbolSource.resolveLocal(ordinal: Int?, explicitName: String?, fallbackName: String?): HookLocal =
        kspRequireNotNull(
            when {
                ordinal != null -> ordinal.takeIf { explicitName == null }?.let {
                    kspRequire(it >= 0) { "897" }
                    PositionalLocal(it)
                }

                explicitName != null -> {
                    kspRequire(explicitName.isNotEmpty()) { "902" }
                    NamedLocal(explicitName)
                }

                fallbackName != null -> NamedLocal(fallbackName)
                else -> null
            }
        ) { "909" }

    private fun SymbolSource.resolveMappingName(explicitName: String?, implicitName: String): String =
        if (explicitName != null) {
            kspRequire(explicitName.isNotEmpty()) { "913" }
            explicitName
        } else {
            implicitName
        }

    private fun SymbolSource.resolveMixinAnnotations(annotations: List<ParsedAnnotation>): List<MixinAnnotation> =
        annotations.filterNot { it.isSourceRetention }.mapNotNull {
            runOrNullOnSkip { resolveMixinAnnotation(it) }
        }

    private fun SymbolSource.resolveMixinAnnotation(annotation: ParsedAnnotation): MixinAnnotation {
        validateClassDeclaration(annotation.typeClassDeclaration)

        fun ParsedAnnotationArgumentValue.resolveValue(): MixinAnnotationArgumentValue = when (this) {
            is ParsedAnnotationBooleanArgumentValue -> MixinAnnotationBooleanArgumentValue(boolean)
            is ParsedAnnotationByteArgumentValue -> MixinAnnotationByteArgumentValue(byte)
            is ParsedAnnotationShortArgumentValue -> MixinAnnotationShortArgumentValue(short)
            is ParsedAnnotationIntArgumentValue -> MixinAnnotationIntArgumentValue(int)
            is ParsedAnnotationLongArgumentValue -> MixinAnnotationLongArgumentValue(long)
            is ParsedAnnotationCharArgumentValue -> MixinAnnotationCharArgumentValue(char)
            is ParsedAnnotationFloatArgumentValue -> MixinAnnotationFloatArgumentValue(float)
            is ParsedAnnotationDoubleArgumentValue -> MixinAnnotationDoubleArgumentValue(double)
            is ParsedAnnotationStringArgumentValue -> MixinAnnotationStringArgumentValue(string)
            is ParsedAnnotationClassTypeArgumentValue -> MixinAnnotationClassTypeArgumentValue(validateType(type))
            is ParsedAnnotationEnumArgumentValue -> {
                MixinAnnotationEnumArgumentValue(validateClassDeclaration(entryClassDeclaration))
            }

            is ParsedAnnotationEmbeddedAnnotationArgumentValue -> {
                MixinAnnotationEmbeddedAnnotationArgumentValue(resolveMixinAnnotation(embeddedAnnotation))
            }
        }
        return MixinAnnotation(
            typeClassDeclaration = annotation.typeClassDeclaration,
            arguments = annotation.arguments.filter { it.isExplicit }.map { argument ->
                when (argument) {
                    is ParsedAnnotationSingleArgument -> {
                        MixinAnnotationSingleArgument(argument.name, argument.value.resolveValue())
                    }

                    is ParsedAnnotationArrayArgument -> {
                        MixinAnnotationArrayArgument(argument.name, argument.values.map { it.resolveValue() })
                    }
                }
            }
        )
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

    @Suppress("UnusedReceiverParameter")
    private fun SymbolSource.skipSymbol(): Nothing = throw SkipSymbolSignal()

    private inline fun SymbolSource.skipWithError(crossinline message: () -> String): Nothing {
        kspError(message)
        skipSymbol()
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun SymbolSource.kspRequire(condition: Boolean, crossinline message: () -> String) {
        contract { returns() implies condition }
        if (!condition) {
            skipWithError(message = message)
        }
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun <T> SymbolSource.kspRequireNotNull(value: T?, crossinline message: () -> String): T {
        contract { returns() implies (value != null) }
        return value ?: skipWithError(message = message)
    }

    @Suppress("unused", "UnusedReceiverParameter")
    @Deprecated(
        message = "This call is redundant because the passed value is already non-nullable.",
        level = DeprecationLevel.ERROR
    )
    private inline fun <T : Any> SymbolSource.kspRequireNotNull(value: T, crossinline message: () -> String): Nothing {
        lapisError("kspRequireNotNull() called with a non-nullable value.")
    }

    @Suppress("unused", "UnusedReceiverParameter")
    @Deprecated(
        message = "Ambiguous call: use kspRequire() for Boolean conditions.",
        replaceWith = ReplaceWith("kspRequire(value, message)"),
        level = DeprecationLevel.ERROR,
    )
    private fun SymbolSource.kspRequireNotNull(value: Boolean?, message: () -> String): Nothing {
        lapisError("kspRequireNotNull() called with a Boolean value. Use kspRequire() instead.")
    }

    private fun <R> runOrNullOnSkip(block: () -> R): R? =
        try {
            block()
        } catch (_: SkipSymbolSignal) {
            null
        }

    private class SkipSymbolSignal : Exception()
}
