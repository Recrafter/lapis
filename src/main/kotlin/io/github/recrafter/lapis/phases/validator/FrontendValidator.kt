package io.github.recrafter.lapis.phases.validator

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.recrafter.lapis.annotations.AccessStrategy
import io.github.recrafter.lapis.annotations.Ats
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.common.JavaModifiers
import io.github.recrafter.lapis.common.JvmClassName
import io.github.recrafter.lapis.common.KSBaseTypes
import io.github.recrafter.lapis.common.findArrayComponentType
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.indexOfFirstOrNull
import io.github.recrafter.lapis.extensions.jp.JPModifier
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
                hasSchemaAnnotation,
                hasInnerSchemaAnnotation,
                hasLocalSchemaAnnotation,
                hasAnonymousSchemaAnnotation,
            ).count { it } == 1
        ) { "90" }
        if (hasSchemaAnnotation) {
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
        validateClassDeclaration(superClassDeclaration)
        kspRequire(isObject) { "132" }
        val mappingName = resolveMappingName(explicitMappingName, name)
        val receiverType = schemaOriginClassDeclaration.starProjectedType
        if (superClassDeclaration.isBuiltin(SimpleBuiltin.Field)) {
            kspRequire(genericArgument is ParsedDescriptorGenericArgumentSimpleType) { "136" }
            validateType(genericArgument.type)
            val accessRequest = resolveAccessRequest(
                AccessMember.FIELD,
                hasAccessAnnotation, accessStrategy, isAccessUnfinal, isAccessibleSchema,
                accessFieldOps, emptyList(),
            )
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
                isStatic = hasStaticAnnotation,
                accessRequest = accessRequest,
            )
        }
        kspRequire(genericArgument is ParsedDescriptorGenericArgumentFunctionType) { "160" }
        kspRequire(genericArgument.receiverType == null) { "161" }
        val functionTypeParameters = genericArgument.parameters.map { parameter ->
            kspRequire(!parameter.type.isFunctionType) { "163" }
            FunctionTypeParameter(
                type = parameter.type,
                name = parameter.name,
            )
        }
        val accessRequest = resolveAccessRequest(
            AccessMember.INVOKABLE,
            hasAccessAnnotation, accessStrategy, isAccessUnfinal, isAccessibleSchema,
            emptyList(), functionTypeParameters,
        )
        return when {
            superClassDeclaration.isBuiltin(SimpleBuiltin.Method) -> {
                MethodDescriptor(
                    symbol = symbol,
                    classDeclaration = classDeclaration,

                    name = name,
                    receiverType = receiverType,
                    inaccessibleReceiverJvmClassName = if (isAccessibleSchema) null else schemaOriginJvmClassName,
                    returnType = genericArgument.returnType,
                    mappingName = mappingName,
                    functionTypeParameters = functionTypeParameters,
                    isStatic = hasStaticAnnotation,
                    accessRequest = accessRequest,
                )
            }

            superClassDeclaration.isBuiltin(SimpleBuiltin.Constructor) -> {
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
        val companionObjects = companionObjects.mapNotNull {
            runOrNullOnSkip { it.validate() }
        }
        val companionObjectHooks = companionObjects.flatMap { companionObject ->
            companionObject.functions.filter { it.hasHookAnnotation }.mapNotNull {
                runOrNullOnSkip { it.validateAsHook(isInCompanionObject = true) }
            }
        }
        val (parsedHookFunctions, parsedRegularFunctions) = functions.partition { it.hasHookAnnotation }
        val constructorParameters = constructor.parameters.mapNotNull {
            runOrNullOnSkip { it.validate(originClassDeclaration) }
        }
        val extensionProperties = bodyProperties.filter { it.hasExtensionAnnotation }.mapNotNull {
            runOrNullOnSkip { it.validateAsExtension(isAccessibleTarget, originClassDeclaration) }
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
        val hooks = parsedHookFunctions.mapNotNull {
            runOrNullOnSkip { it.validateAsHook(isInCompanionObject = false) }
        }
        val hasStaticHooksOnly = constructorParameters.isEmpty()
            && extensionProperties.isEmpty() && extensionFunctions.isEmpty()
            && shadowProperties.isEmpty() && shadowFunctions.isEmpty()
            && hooks.all { it.methodDescriptor.isStatic }
        if (!hasStaticHooksOnly) {
            kspRequire(isAbstract) { "273" }
        }
        return Patch(
            symbol = symbol,
            classDeclaration = classDeclaration,

            name = name,
            side = side,
            initStrategy = initStrategy,
            isImplRequired = !hasStaticHooksOnly,
            originClassDeclaration = originClassDeclaration,
            targetJvmClassName = originClassDeclaration?.qualifiedName?.asString()?.let { JvmClassName.of(it) },

            constructorParameters = constructorParameters,
            extensionSources = extensionProperties + extensionFunctions,
            shadowSources = shadowProperties + shadowFunctions,
            hooks = hooks + companionObjectHooks,
            mixinAnnotations = resolveMixinAnnotations(annotations),
        )
    }

    private fun ParsedPatchCompanionObject.validate(): ParsedPatchCompanionObject {
        kspRequire(isPublic) { "295" }
        return this
    }

    private fun ParsedPatchConstructorParameter.validate(
        originClassDeclaration: KSClassDeclaration?
    ): PatchConstructorParameter {
        validateType(type)
        return when {
            hasOriginAnnotation -> {
                validateClassDeclaration(originClassDeclaration)
                val instanceClassDeclaration = type.toClassDeclaration()
                kspRequire(instanceClassDeclaration == originClassDeclaration) { "307" }
                kspRequire(type.arguments.none { it.variance != Variance.STAR }) { "308" }
                PatchConstructorOriginParameter(instanceClassDeclaration)
            }

            else -> skipWithError { "312" }
        }
    }

    private fun ParsedPatchProperty.validateAsExtension(
        isAccessibleTarget: Boolean,
        receiverClassDeclaration: KSClassDeclaration?,
    ): ExtensionProperty {
        validateType(type)
        kspRequireNotNull(getter) { "321" }
        kspRequireNotNull(getter.jvmName) { "322" }
        kspRequire(isPublic) { "323" }
        kspRequire(!hasExtensionReceiver) { "324" }
        kspRequire(isAccessibleTarget) { "325" }
        kspRequire(!isOpen && !isAbstract) { "326" }
        validateClassDeclaration(receiverClassDeclaration)
        return ExtensionProperty(
            name = name,
            getterJvmName = getter.jvmName,
            setterJvmName = if (setter != null) kspRequireNotNull(setter.jvmName) { "331" } else null,
            type = type,
            receiverClassDeclaration = receiverClassDeclaration,
        )
    }

    private fun ParsedPatchFunction.validateAsExtension(
        isAccessibleTarget: Boolean,
        receiverClassDeclaration: KSClassDeclaration?,
    ): ExtensionFunction {
        kspRequire(isPublic) { "341" }
        kspRequireNotNull(jvmName) { "342" }
        kspRequire(!hasExtensionReceiver) { "343" }
        kspRequire(isAccessibleTarget) { "344" }
        kspRequire(!isOpen && !isAbstract) { "345" }
        val parameters = parameters.map {
            FunctionParameter(
                name = kspRequireNotNull(it.name) { "348" },
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
        kspRequire(isPublic) { "364" }
        kspRequire(isAbstract) { "365" }
        kspRequire(!hasExtensionReceiver) { "366" }
        kspRequireNotNull(getter) { "367" }
        kspRequireNotNull(getter.jvmName) { "368" }
        val mappingName = resolveMappingName(explicitMappingName, name)
        val shadowModifiers = resolveModifiers(shadowModifiers, isMethod = false)
        return ShadowProperty(
            name = name,
            getterJvmName = getter.jvmName,
            setterJvmName = if (setter != null) kspRequireNotNull(setter.jvmName) { "374" } else null,
            mappingName = mappingName,
            modifiers = shadowModifiers,
            type = type,
            mixinAnnotations = resolveMixinAnnotations(getter.annotations),
        )
    }

    private fun ParsedPatchFunction.validateAsShadow(): ShadowFunction {
        kspRequire(isPublic) { "383" }
        kspRequireNotNull(jvmName) { "384" }
        kspRequire(isAbstract) { "385" }
        kspRequire(!hasExtensionReceiver) { "386" }
        val parameters = parameters.map {
            FunctionParameter(
                name = kspRequireNotNull(it.name) { "389" },
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

    private fun ParsedPatchFunction.validateAsHook(isInCompanionObject: Boolean): PatchHook {
        kspRequireNotNull(hookAt) { "407" }
        kspRequire(!isOpen) { "408" }
        kspRequire(!hasTypeParameters) { "409" }
        val hookMethodDescriptor = resolveDescriptor(hookDescClassDeclaration)
        kspRequire(hookMethodDescriptor is InvokableDescriptor) { "411" }
        if (hookMethodDescriptor.isStatic) {
            kspRequire(isInCompanionObject) { "413" }
        } else {
            kspRequire(!isInCompanionObject) { "415" }
        }
        kspRequireNotNull(jvmName) { "417" }
        val ordinals: (List<Int>) -> Set<Int> = { resolveOrdinals(it) }
        val parameters: () -> List<HookParameter> = {
            parameters.mapNotNull { parameter ->
                runOrNullOnSkip { parameter.validateAsHookParameter(this@validateAsHook, hookAt, hookMethodDescriptor) }
            }
        }
        return when (hookAt) {
            Ats.Head -> {
                kspRequire(returnType == null) { "426" }
                when (hookMethodDescriptor) {
                    is ConstructorDescriptor -> {
                        kspRequire(hasAtConstructorHeadAnnotation) { "429" }
                        ConstructorHeadHook(
                            jvmName = jvmName,
                            methodDescriptor = hookMethodDescriptor,
                            phase = kspRequireNotNull(atConstructorHeadPhase) { "433" },
                            parameters = parameters(),
                        )
                    }

                    is MethodDescriptor -> MethodHeadHook(
                        jvmName = jvmName,
                        methodDescriptor = hookMethodDescriptor,
                        parameters = parameters(),
                    )
                }
            }

            Ats.Body -> {
                kspRequire(hookMethodDescriptor is MethodDescriptor) { "447" }
                kspRequire(returnType == hookMethodDescriptor.returnType) { "448" }
                BodyHook(
                    jvmName = jvmName,
                    methodDescriptor = hookMethodDescriptor,
                    returnType = returnType,
                    parameters = parameters(),
                )
            }

            Ats.Tail -> {
                kspRequire(returnType == null) { "458" }
                TailHook(
                    jvmName = jvmName,
                    methodDescriptor = hookMethodDescriptor,
                    parameters = parameters(),
                )
            }

            Ats.Local -> {
                kspRequire(hasAtLocalAnnotation) { "467" }
                kspRequireNotNull(atLocalOp) { "468" }
                kspRequire(returnType == validateType(atLocalType)) { "469" }
                LocalHook(
                    jvmName = jvmName,
                    methodDescriptor = hookMethodDescriptor,
                    type = atLocalType,
                    ordinals = ordinals(atLocalOpOrdinals),
                    local = resolveLocal(explicitAtLocalOrdinal, explicitAtLocalName, null),
                    op = atLocalOp,
                    parameters = parameters(),
                )
            }

            Ats.Instanceof -> {
                kspRequire(hasAtInstanceofAnnotation) { "482" }
                validateClassDeclaration(atInstanceofTypeClassDeclaration)
                kspRequire(returnType?.toClassName()?.asIrTypeName() == KPBoolean.asIrTypeName()) { "484" }
                InstanceofHook(
                    jvmName = jvmName,
                    methodDescriptor = hookMethodDescriptor,
                    typeClassDeclaration = atInstanceofTypeClassDeclaration,
                    returnType = returnType,
                    ordinals = ordinals(atInstanceofOrdinals),
                    parameters = parameters(),
                )
            }

            Ats.Return -> {
                kspRequire(hasAtReturnAnnotation) { "496" }
                kspRequire(returnType == hookMethodDescriptor.returnType) { "497" }
                ReturnHook(
                    jvmName = jvmName,
                    methodDescriptor = hookMethodDescriptor,
                    type = returnType,
                    ordinals = ordinals(atReturnOrdinals),
                    parameters = parameters(),
                )
            }

            Ats.Literal -> {
                kspRequire(hasAtLiteralAnnotation) { "508" }
                val literal = resolveLiteral(this@validateAsHook)
                val type = literal.getType(baseTypes)
                if (literal !is NullHookLiteral) {
                    if (literal !is StringHookLiteral && literal !is ClassHookLiteral) {
                        kspRequire(returnType?.isMarkedNullable == false) { "513" }
                    }
                    kspRequire(returnType == type) { "515" }
                }
                LiteralHook(
                    jvmName = jvmName,
                    methodDescriptor = hookMethodDescriptor,
                    type = type,
                    literal = literal,
                    ordinals = ordinals(atLiteralOrdinals),
                    parameters = parameters(),
                )
            }

            Ats.Field -> {
                kspRequire(hasAtFieldAnnotation) { "528" }
                kspRequireNotNull(atFieldOp) { "529" }
                val targetDescriptor = resolveDescriptor(atFieldDescClassDeclaration)
                kspRequire(targetDescriptor is FieldDescriptor) { "531" }
                when (atFieldOp) {
                    Op.Get -> {
                        kspRequire(returnType?.makeNotNullable() == targetDescriptor.fieldType) { "534" }
                        FieldGetHook(
                            jvmName = jvmName,
                            methodDescriptor = hookMethodDescriptor,
                            type = targetDescriptor.fieldType,
                            ordinals = ordinals(atFieldOrdinals),
                            targetDescriptor = targetDescriptor,
                            parameters = parameters(),
                        )
                    }

                    Op.Set -> {
                        kspRequire(returnType == null) { "546" }
                        FieldSetHook(
                            jvmName = jvmName,
                            methodDescriptor = hookMethodDescriptor,
                            type = targetDescriptor.fieldType,
                            ordinals = ordinals(atFieldOrdinals),
                            targetDescriptor = targetDescriptor,
                            parameters = parameters(),
                        )
                    }
                }
            }

            Ats.Array -> {
                kspRequire(hasAtArrayAnnotation) { "560" }
                kspRequireNotNull(atArrayOp) { "561" }
                val targetDescriptor = resolveDescriptor(atArrayDescClassDeclaration)
                kspRequire(targetDescriptor is FieldDescriptor) { "563" }
                kspRequireNotNull(targetDescriptor.arrayComponentType) { "564" }
                validateType(targetDescriptor.arrayComponentType)
                when (atArrayOp) {
                    Op.Get -> kspRequire(returnType == targetDescriptor.arrayComponentType) { "567" }
                    Op.Set -> kspRequire(returnType == null) { "568" }
                }
                ArrayHook(
                    jvmName = jvmName,
                    methodDescriptor = hookMethodDescriptor,
                    op = atArrayOp,
                    type = targetDescriptor.fieldType,
                    componentType = targetDescriptor.arrayComponentType,
                    targetDescriptor = targetDescriptor,
                    ordinals = ordinals(atArrayOrdinals),
                    parameters = parameters(),
                )
            }

            Ats.Call -> {
                kspRequire(hasAtCallAnnotation) { "583" }
                val targetDescriptor = resolveDescriptor(atCallDescClassDeclaration)
                kspRequire(targetDescriptor is MethodDescriptor) { "585" }
                kspRequire(returnType?.makeNotNullable() == targetDescriptor.returnType) { "586" }
                CallHook(
                    jvmName = jvmName,
                    methodDescriptor = hookMethodDescriptor,
                    returnType = returnType,
                    targetDescriptor = targetDescriptor,
                    ordinals = ordinals(atCallOrdinals),
                    parameters = parameters(),
                )
            }
        }
    }

    private fun ParsedPatchFunctionParameter.validateAsHookParameter(
        function: ParsedPatchFunction,
        at: Ats,
        hookDescriptor: InvokableDescriptor,
    ): HookParameter {
        kspRequireNotNull(name) { "604" }
        validateType(type)
        kspRequire(!hasDefaultArgument) { "606" }
        return when {
            hasOriginAnnotation -> when (at) {
                Ats.Head, Ats.Tail -> skipWithError { "609" }

                Ats.Body -> {
                    val originDescriptor = resolveDescriptor(typeArguments.singleOrNull()?.toClassDeclaration())
                    kspRequire(originDescriptor is MethodDescriptor) { "613" }
                    kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Body)) { "614" }
                    HookOriginBodyDescriptorWrapperParameter(originDescriptor)
                }

                Ats.Local -> {
                    kspRequire(type == function.returnType) { "619" }
                    HookOriginValueParameter
                }

                Ats.Instanceof -> {
                    kspRequire(type.declaration.isBuiltin(SimpleBuiltin.Instanceof)) { "624" }
                    HookOriginInstanceofWrapperParameter
                }

                Ats.Return -> {
                    kspRequireNotNull(hookDescriptor.returnType) { "629" }
                    kspRequire(type == hookDescriptor.returnType) { "630" }
                    HookOriginValueParameter
                }

                Ats.Literal -> {
                    val literal = resolveLiteral(function)
                    kspRequire(literal !is NullHookLiteral) { "636" }
                    kspRequire(type == literal.getType(baseTypes)) { "637" }
                    HookOriginValueParameter
                }

                Ats.Field -> {
                    kspRequireNotNull(function.atFieldOp) { "642" }
                    val originDescriptor = resolveDescriptor(typeArguments.singleOrNull()?.toClassDeclaration())
                    kspRequire(originDescriptor is FieldDescriptor) { "644" }
                    when (function.atFieldOp) {
                        Op.Get -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.FieldGet)) { "647" }
                            HookOriginFieldGetDescriptorWrapperParameter(originDescriptor)
                        }

                        Op.Set -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.FieldSet)) { "652" }
                            HookOriginFieldSetDescriptorWrapperParameter(originDescriptor)
                        }
                    }
                }

                Ats.Array -> {
                    kspRequireNotNull(function.atArrayOp) { "659" }
                    val originDescriptor = resolveDescriptor(typeArguments.singleOrNull()?.toClassDeclaration())
                    kspRequire(originDescriptor is FieldDescriptor) { "661" }
                    kspRequireNotNull(originDescriptor.arrayComponentType) { "662" }
                    validateType(originDescriptor.arrayComponentType)
                    when (function.atArrayOp) {
                        Op.Get -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.ArrayGet)) { "666" }
                            HookOriginArrayGetDescriptorWrapperParameter(
                                originDescriptor,
                                originDescriptor.arrayComponentType
                            )
                        }

                        Op.Set -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.ArraySet)) { "674" }
                            HookOriginArraySetDescriptorWrapperParameter(
                                originDescriptor,
                                originDescriptor.arrayComponentType
                            )
                        }
                    }
                }

                Ats.Call -> {
                    val originDescriptor = resolveDescriptor(typeArguments.singleOrNull()?.toClassDeclaration())
                    kspRequire(originDescriptor is InvokableDescriptor) { "685" }
                    kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Call)) { "686" }
                    HookOriginCallDescriptorWrapperParameter(originDescriptor)
                }
            }

            hasCancelAnnotation -> {
                kspRequire(at != Ats.Body) { "692" }
                kspRequire(hookDescriptor is MethodDescriptor) { "693" }
                val cancelDescriptor = resolveDescriptor(typeArguments.singleOrNull()?.toClassDeclaration())
                kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Cancel)) { "695" }
                kspRequire(cancelDescriptor == hookDescriptor) { "696" }
                HookCancelDescriptorWrapperParameter(hookDescriptor)
            }

            hasOrdinalAnnotation -> {
                kspRequire(type == baseTypes.int) { "701" }
                kspRequire(function.hasOrdinals()) { "702" }
                HookOrdinalParameter
            }

            hasParamAnnotation -> {
                kspRequire(at != Ats.Body) { "707" }
                explicitParamName?.let { kspRequire(it.isNotEmpty()) { "708" } }
                val parameterName = explicitParamName ?: name
                val parameterIndex = hookDescriptor.functionTypeParameters.indexOfFirstOrNull {
                    it.name == parameterName
                }
                kspRequireNotNull(parameterIndex) { "713" }
                val (parameterLocalType, isLocalVar) = resolveLocalType(type, typeArguments)
                kspRequire(hookDescriptor.functionTypeParameters[parameterIndex].type == parameterLocalType) { "715" }
                HookParamLocalParameter(parameterName, parameterLocalType, parameterIndex, isLocalVar)
            }

            hasLocalAnnotation -> {
                kspRequire(at != Ats.Body) { "720" }
                val (bodyLocalType, isLocalVar) = resolveLocalType(type, typeArguments)
                HookBodyLocalParameter(
                    name,
                    bodyLocalType,
                    resolveLocal(explicitLocalOrdinal, explicitLocalName, name),
                    isLocalVar,
                )
            }

            hasShareAnnotation -> {
                kspRequire(type.declaration.isBuiltin(SimpleBuiltin.LocalVar)) { "731" }
                val type = validateType(typeArguments.singleOrNull())
                explicitShareKey?.let { kspRequire(it.isNotEmpty()) { "733" } }
                HookShareLocalParameter(name, type, explicitShareKey ?: name, isShareExported)
            }

            else -> skipWithError { "737" }
        }
    }

    @OptIn(ExperimentalContracts::class)
    private fun SymbolSource.validateType(type: KSType?): KSType {
        contract { returns() implies (type != null) }
        kspRequire(type?.isValid == true) { "744" }
        return type
    }

    @OptIn(ExperimentalContracts::class)
    private fun SymbolSource.validateClassDeclaration(classDeclaration: KSClassDeclaration?): KSClassDeclaration {
        contract { returns() implies (classDeclaration != null) }
        kspRequire(classDeclaration?.isValid == true) { "751" }
        return classDeclaration
    }

    private fun SymbolSource.resolveLiteral(function: ParsedPatchFunction): HookLiteral =
        kspRequireNotNull(
            with(function) {
                listOfNotNull(
                    explicitAtLiteralZero?.let { ZeroHookLiteral(atLiteralZeroConditions) },
                    explicitAtLiteralInt?.let {
                        kspRequire(it != 0) { "761" }
                        IntHookLiteral(it)
                    },
                    explicitAtLiteralLong?.let(::LongHookLiteral),
                    explicitAtLiteralFloat?.let(::FloatHookLiteral),
                    explicitAtLiteralDouble?.let(::DoubleHookLiteral),
                    explicitAtLiteralString?.let(::StringHookLiteral),
                    explicitAtLiteralClassType?.let {
                        ClassHookLiteral(validateClassDeclaration(explicitAtLiteralClassDeclaration))
                    },
                    explicitAtLiteralNull?.let { NullHookLiteral },
                ).singleOrNull()
            }
        ) { "774" }

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
        kspRequireNotNull(accessStrategy) { "799" }
        return when (accessStrategy) {
            AccessStrategy.Tweak -> {
                kspRequire(isAccessibleSchema) { "802" }
                kspRequire(options.accessWidenerConfig != null || options.accessTransformerConfig != null) { "803" }
                TweakAccessRequest(isAccessUnfinal)
            }

            AccessStrategy.Mixin -> when (member) {
                AccessMember.CLASS -> skipWithError { "808" }
                AccessMember.FIELD -> {
                    kspRequire(fieldOps.isNotEmpty()) { "810" }
                    MixinFieldAccessRequest(isAccessUnfinal, fieldOps)
                }

                AccessMember.INVOKABLE -> {
                    kspRequire(!isAccessUnfinal) { "815" }
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
                    kspRequire(anonymousParameterIndices.isEmpty()) { "826" }
                    MixinInvokableAccessRequest(parameters)
                }
            }
        }
    }

    private fun SymbolSource.resolveModifiers(modifiers: List<JPModifier>, isMethod: Boolean): Set<JPModifier> {
        val set = modifiers.toSet()
        val allowed = if (isMethod) JavaModifiers.methodAllowed else JavaModifiers.fieldAllowed
        kspRequire(allowed.containsAll(set)) { "836" }
        kspRequire(set.count { it in JavaModifiers.visibilities } <= 1) { "837" }
        if (isMethod) {
            kspRequire(set.count { it in JavaModifiers.methodConflicts } <= 1) { "839" }
            if (JPModifier.ABSTRACT in set) {
                kspRequire(set.none { it in JavaModifiers.abstractIllegals }) { "841" }
            }
            if (JPModifier.NATIVE in set) {
                kspRequire(JPModifier.DEFAULT !in set) { "844" }
            }
        } else {
            if (JPModifier.FINAL in set) {
                kspRequire(JPModifier.VOLATILE !in set) { "848" }
            }
        }
        return set
    }

    private fun SymbolSource.resolveDescriptor(classDeclaration: KSClassDeclaration?): Descriptor {
        validateClassDeclaration(classDeclaration)
        val qualifiedName = classDeclaration.qualifiedName?.asString()
        kspRequire(qualifiedName !in invalidDescriptors) { "857" }
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
                    kspRequire(it >= 0) { "871" }
                    PositionalLocal(it)
                }

                explicitName != null -> {
                    kspRequire(explicitName.isNotEmpty()) { "876" }
                    NamedLocal(explicitName)
                }

                fallbackName != null -> NamedLocal(fallbackName)
                else -> null
            }
        ) { "883" }

    private fun SymbolSource.resolveMappingName(explicitName: String?, implicitName: String): String =
        if (explicitName != null) {
            kspRequire(explicitName.isNotEmpty()) { "887" }
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
