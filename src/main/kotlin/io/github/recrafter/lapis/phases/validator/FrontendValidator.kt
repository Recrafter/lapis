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
import io.github.recrafter.lapis.extensions.jp.JPModifier
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
import io.github.recrafter.lapis.phases.generator.models.JavaModifiers
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
                runOrNullOnSkip { rootSchema.validate() } ?: emptyList()
            },
            patches = result.patches.mapNotNull {
                runOrNullOnSkip { it.validate() }
            },
        )

    private fun ParsedSchema.validate(): List<Schema> {
        kspRequire(classDeclaration?.isValid == true) { "54" }
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "55" }
        kspRequireNotNull(originJvmClassName) { "56" }
        kspRequire(originClassDeclaration?.isValid == true) { "57" }
        val hasSingleSchemaAnnotation = listOf(
            hasSchemaAnnotation,
            hasInnerSchemaAnnotation,
            hasLocalSchemaAnnotation,
            hasAnonymousSchemaAnnotation,
        ).count { it } == 1
        kspRequire(hasSingleSchemaAnnotation) { "64" }
        if (hasSchemaAnnotation) {
            kspRequire(isTopLevel) { "66" }
        }
        kspRequire(hasPackageName) { "68" }
        val accessRequest = resolveAccessRequest(
            hasAccessAnnotation, true, isResolvable, accessStrategy, isAccessUnfinal, emptyList()
        )
        val qualifiedName = kspRequireNotNull(classDeclaration.qualifiedName?.asString()) { "72" }
        val descriptors = descriptors.mapNotNull { parsedDescriptor ->
            val descriptorQualifiedName = parsedDescriptor.classDeclaration.qualifiedName?.asString()
                ?: return@mapNotNull null
            val validatedDescriptor = runOrNullOnSkip {
                parsedDescriptor.validate(originClassDeclaration, originJvmClassName, isResolvable)
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
            side = kspRequireNotNull(side) { "92" },
            isAccessible = isResolvable,
            accessRequest = accessRequest,
            descriptors = descriptors,
        )
        validSchemas[qualifiedName] = schema
        return buildList {
            add(schema)
            addAll(nestedSchemas.flatMap {
                runOrNullOnSkip { it.validate() } ?: emptyList()
            })
        }
    }

    private fun ParsedDescriptor.validate(
        schemaOriginClassDeclaration: KSClassDeclaration,
        schemaOriginJvmClassName: JvmClassName,
        isResolvable: Boolean,
    ): Descriptor {
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "111" }
        kspRequire(superClassDeclaration?.isValid == true) { "112" }
        kspRequire(isObject) { "113" }
        val accessRequest = resolveAccessRequest(
            hasAccessAnnotation, false, isResolvable, accessStrategy, isAccessUnfinal, accessFieldOps,
        )
        val mappingName = resolveMappingName(explicitMappingName, name)
        val receiverType = schemaOriginClassDeclaration.starProjectedType
        if (superClassDeclaration.isBuiltin(SimpleBuiltin.Field)) {
            kspRequire(genericType is ParsedTypeDescriptorGenericType) { "120" }
            kspRequireNotNull(genericType.type) { "121" }
            if (accessRequest is MixinAccessRequest) {
                kspRequire(accessRequest.fieldOps.isNotEmpty()) { "123" }
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
        kspRequire(genericType is ParsedFunctionTypeDescriptorGenericType) { "139" }
        kspRequire(genericType.receiverType == null) { "140" }
        val parameters = genericType.parameters.map { parameter ->
            kspRequire(!parameter.type.isFunctionType) { "142" }
            FunctionTypeParameter(
                type = parameter.type,
                name = parameter.name,
            )
        }
        if (accessRequest != null) {
            kspRequire(parameters.all { it.name != null }) { "149" }
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
                kspRequire(!isAccessUnfinal) { "169" }
                kspRequire(genericType.returnType == null) { "170" }
                kspRequire(!hasMappingNameAnnotation) { "171" }
                if (accessRequest is MixinAccessRequest) {
                    kspRequire(isResolvable) { "173" }
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

            else -> skipWithError { "186" }
        }
    }

    private fun ParsedPatch.validate(): Patch {
        kspRequireNotNull(name) { "191" }
        kspRequireNotNull(side) { "192" }
        kspRequireNotNull(initStrategy) { "193" }
        kspRequire(classDeclaration?.isValid == true) { "194" }
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "195" }
        kspRequire(schemaClassDeclaration?.isValid == true) { "196" }
        kspRequire(isTopLevel) { "197" }
        kspRequire(hasPackageName) { "198" }
        kspRequire(isPublic) { "199" }
        val schema = validSchemas[schemaClassDeclaration.qualifiedName?.asString()]
        kspRequireNotNull(schema) { "201" }
        kspRequire(isClass) { "202" }
        kspRequire(!isObject) { "203" }
        kspRequire(!isSealed) { "204" }
        kspRequire(!isOpen) { "205" }
        val constructor = kspRequireNotNull(constructors.singleOrNull()) { "206" }
        constructor.kspRequire(constructor.isPublic) { "207" }
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
            runOrNullOnSkip { it.validate(schema) }
        }
        val extensionProperties = bodyProperties.filter { it.hasExtensionAnnotation }.mapNotNull {
            runOrNullOnSkip { it.validateAsExtension(schema) }
        }
        val extensionFunctions = parsedRegularFunctions.filter { it.hasExtensionAnnotation }.mapNotNull {
            runOrNullOnSkip { it.validateAsExtension(schema.isAccessible) }
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
            && hooks.all { it.descriptor.isStatic }
        if (!hasStaticHooksOnly) {
            kspRequire(isAbstract) { "240" }
        }
        return Patch(
            symbol = symbol,
            classDeclaration = classDeclaration,

            name = name,
            side = side,
            initStrategy = initStrategy,
            isImplRequired = !hasStaticHooksOnly,
            schema = schema,

            constructorParameters = constructorParameters,
            extensionSources = extensionProperties + extensionFunctions,
            shadowSources = shadowProperties + shadowFunctions,
            hooks = hooks + companionObjectHooks,
        )
    }

    private fun ParsedPatchCompanionObject.validate(): ParsedPatchCompanionObject {
        kspRequire(isPublic) { "260" }
        return this
    }

    private fun ParsedPatchConstructorParameter.validate(schema: Schema): PatchConstructorParameter =
        when {
            hasOriginAnnotation -> {
                val instanceClassDeclaration = type.toClassDeclaration()
                kspRequire(instanceClassDeclaration == schema.originClassDeclaration) { "268" }
                kspRequire(type.arguments.none { it.variance != Variance.STAR }) { "269" }
                PatchConstructorOriginParameter
            }

            else -> skipWithError { "273" }
        }

    private fun ParsedPatchProperty.validateAsExtension(
        schema: Schema,
    ): PatchExtensionProperty {
        kspRequireNotNull(getterJvmName) { "279" }
        kspRequire(isPublic) { "280" }
        kspRequire(!isExtension) { "281" }
        kspRequire(schema.isAccessible) { "282" }
        kspRequire(!isOpen && !isAbstract) { "283" }
        return PatchExtensionProperty(
            name = name,
            getterJvmName = getterJvmName,
            setterJvmName = if (isMutable) kspRequireNotNull(setterJvmName) { "287" } else null,
            type = type,
        )
    }

    private fun ParsedPatchFunction.validateAsExtension(
        isResolvable: Boolean,
    ): PatchExtensionFunction {
        kspRequire(isPublic) { "295" }
        kspRequire(!hasExtensionReceiver) { "296" }
        kspRequire(isResolvable) { "297" }
        kspRequire(!isOpen && !isAbstract) { "298" }
        val parameters = parameters.map {
            FunctionParameter(
                name = kspRequireNotNull(it.name) { "301" },
                type = kspRequireNotNull(it.type) { "302" },
            )
        }
        return PatchExtensionFunction(
            name = name,
            jvmName = jvmName,
            parameters = parameters,
            returnType = returnType,
        )
    }

    private fun ParsedPatchProperty.validateAsShadow(): PatchShadowProperty {
        kspRequireNotNull(getterJvmName) { "314" }
        kspRequire(isPublic) { "315" }
        kspRequire(!isExtension) { "316" }
        kspRequire(isAbstract) { "317" }
        val mappingName = resolveMappingName(explicitMappingName, name)
        val shadowModifiers = resolveModifiers(shadowModifiers.toSet(), isMethod = false)
        return PatchShadowProperty(
            name = name,
            getterJvmName = getterJvmName,
            setterJvmName = if (isMutable) kspRequireNotNull(setterJvmName) { "323" } else null,
            mappingName = mappingName,
            modifiers = shadowModifiers,
            type = type,
        )
    }

    private fun ParsedPatchFunction.validateAsShadow(): PatchShadowFunction {
        kspRequire(isPublic) { "331" }
        kspRequire(isAbstract) { "332" }
        kspRequire(!hasExtensionReceiver) { "333" }
        val parameters = parameters.map {
            FunctionParameter(
                name = kspRequireNotNull(it.name) { "336" },
                type = kspRequireNotNull(it.type) { "337" },
            )
        }
        val mappingName = resolveMappingName(explicitMappingName, name)
        val shadowModifiers = resolveModifiers(shadowModifiers.toSet(), isMethod = true)
        return PatchShadowFunction(
            name = name,
            jvmName = jvmName,
            parameters = parameters,
            returnType = returnType,
            mappingName = mappingName,
            modifiers = shadowModifiers,
        )
    }

    private fun ParsedPatchFunction.validateAsHook(isInCompanionObject: Boolean): PatchHook {
        kspRequireNotNull(hookAt) { "353" }
        kspRequire(!isOpen) { "354" }
        kspRequire(!hasTypeParameters) { "355" }
        val hookDescriptor = resolveDescriptorReference(hookDescriptorClassDeclaration)
        kspRequire(hookDescriptor is InvokableDescriptor) { "357" }
        if (hookDescriptor.isStatic) {
            kspRequire(isInCompanionObject) { "359" }
        } else {
            kspRequire(!isInCompanionObject) { "361" }
        }
        val ordinals: (List<Int>) -> List<Int> = { resolveOrdinals(it) }
        val parameters: () -> List<HookParameter> = {
            parameters.mapNotNull { parameter ->
                runOrNullOnSkip { parameter.validateAsHookParameter(this@validateAsHook, hookAt, hookDescriptor) }
            }
        }
        return when (hookAt) {
            At.Head -> {
                kspRequire(returnType == null) { "371" }
                when (hookDescriptor) {
                    is ConstructorDescriptor -> {
                        kspRequire(hasAtConstructorHeadAnnotation) { "374" }
                        ConstructorHeadHook(
                            jvmName = jvmName,
                            descriptor = hookDescriptor,
                            phase = kspRequireNotNull(atConstructorHeadPhase) { "378" },
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
                kspRequire(hookDescriptor is MethodDescriptor) { "394" }
                kspRequire(returnType == hookDescriptor.returnType) { "395" }
                BodyHook(
                    jvmName = jvmName,
                    targetDescriptor = hookDescriptor,
                    returnType = returnType,
                    parameters = parameters(),
                )
            }

            At.Tail -> {
                kspRequire(returnType == null) { "405" }
                TailHook(
                    jvmName = jvmName,
                    descriptor = hookDescriptor,
                    parameters = parameters(),
                )
            }

            At.Local -> {
                kspRequire(hasAtLocalAnnotation) { "414" }
                kspRequireNotNull(atLocalOp) { "415" }
                kspRequireNotNull(atLocalType) { "416" }
                kspRequire(returnType == atLocalType) { "417" }
                LocalHook(
                    jvmName = jvmName,
                    descriptor = hookDescriptor,
                    type = atLocalType,
                    ordinals = ordinals(atLocalOpOrdinals),
                    local = resolveLocal(atLocalExplicitOrdinal, atLocalExplicitName, null),
                    op = atLocalOp,
                    parameters = parameters(),
                )
            }

            At.Instanceof -> {
                kspRequire(hasAtInstanceofAnnotation) { "430" }
                kspRequire(atInstanceofTypeClassDeclaration?.isValid == true) { "431" }
                kspRequire(returnType?.toClassName()?.asIrTypeName() == KPBoolean.asIrTypeName()) { "432" }
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
                kspRequire(hasAtReturnAnnotation) { "444" }
                kspRequire(returnType == hookDescriptor.returnType) { "445" }
                ReturnHook(
                    jvmName = jvmName,
                    descriptor = hookDescriptor,
                    type = returnType,
                    ordinals = ordinals(atReturnOrdinals),
                    parameters = parameters(),
                )
            }

            At.Literal -> {
                kspRequire(hasAtLiteralAnnotation) { "456" }
                val literal = resolveLiteral(this@validateAsHook)
                val type = literal.getType(types)
                if (literal !is NullLiteral) {
                    if (literal !is StringLiteral && literal !is ClassLiteral) {
                        kspRequire(returnType?.isMarkedNullable == false) { "461" }
                    }
                    kspRequire(returnType == type) { "463" }
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
                kspRequire(hasAtFieldAnnotation) { "476" }
                kspRequireNotNull(atFieldOp) { "477" }
                val targetDescriptor = resolveDescriptorReference(atFieldDescriptorClassDeclaration)
                kspRequire(targetDescriptor is FieldDescriptor) { "479" }
                when (atFieldOp) {
                    Op.Get -> {
                        kspRequire(returnType?.makeNotNullable() == targetDescriptor.fieldType) { "482" }
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
                        kspRequire(returnType == null) { "494" }
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
                kspRequire(hasAtArrayAnnotation) { "508" }
                kspRequireNotNull(atArrayOp) { "509" }
                val targetDescriptor = resolveDescriptorReference(atArrayDescriptorClassDeclaration)
                kspRequire(targetDescriptor is FieldDescriptor) { "511" }
                kspRequireNotNull(targetDescriptor.arrayComponentType) { "512" }
                when (atArrayOp) {
                    Op.Get -> kspRequire(returnType == targetDescriptor.arrayComponentType) { "514" }
                    Op.Set -> kspRequire(returnType == null) { "515" }
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
                kspRequire(hasAtCallAnnotation) { "530" }
                val targetDescriptor = resolveDescriptorReference(atCallDescriptorClassDeclaration)
                kspRequire(targetDescriptor is MethodDescriptor) { "532" }
                kspRequire(returnType?.makeNotNullable() == targetDescriptor.returnType) { "533" }
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

    private fun ParsedPatchFunctionParameter.validateAsHookParameter(
        function: ParsedPatchFunction,
        at: At,
        hookDescriptor: InvokableDescriptor,
    ): HookParameter {
        kspRequireNotNull(name) { "551" }
        kspRequireNotNull(type) { "552" }
        kspRequire(!hasDefaultArgument) { "553" }
        return when {
            hasOriginAnnotation -> when (at) {
                At.Head, At.Tail -> skipWithError { "556" }

                At.Body -> {
                    val originDescriptor = resolveDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is InvokableDescriptor) { "560" }
                    kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Body)) { "561" }
                    HookOriginBodyDescriptorWrapperParameter(originDescriptor)
                }

                At.Local -> {
                    kspRequire(type == function.returnType) { "566" }
                    HookOriginValueParameter
                }

                At.Instanceof -> {
                    kspRequire(type.declaration.isBuiltin(SimpleBuiltin.Instanceof)) { "571" }
                    HookOriginInstanceofWrapperParameter
                }

                At.Return -> {
                    kspRequireNotNull(hookDescriptor.returnType) { "576" }
                    kspRequire(type == hookDescriptor.returnType) { "577" }
                    HookOriginValueParameter
                }

                At.Literal -> {
                    val literal = resolveLiteral(function)
                    kspRequire(literal !is NullLiteral) { "583" }
                    kspRequire(type == literal.getType(types)) { "584" }
                    HookOriginValueParameter
                }

                At.Field -> {
                    kspRequireNotNull(function.atFieldOp) { "589" }
                    val originDescriptor = resolveDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is FieldDescriptor) { "591" }
                    when (function.atFieldOp) {
                        Op.Get -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.FieldGet)) { "594" }
                            HookOriginFieldGetDescriptorWrapperParameter(originDescriptor)
                        }

                        Op.Set -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.FieldSet)) { "599" }
                            HookOriginFieldSetDescriptorWrapperParameter(originDescriptor)
                        }
                    }
                }

                At.Array -> {
                    kspRequireNotNull(function.atArrayOp) { "606" }
                    val originDescriptor = resolveDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is FieldDescriptor) { "608" }
                    kspRequireNotNull(originDescriptor.arrayComponentType) { "609" }
                    when (function.atArrayOp) {
                        Op.Get -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.ArrayGet)) { "612" }
                            HookOriginArrayGetDescriptorWrapperParameter(
                                originDescriptor,
                                originDescriptor.arrayComponentType
                            )
                        }

                        Op.Set -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.ArraySet)) { "620" }
                            HookOriginArraySetDescriptorWrapperParameter(
                                originDescriptor,
                                originDescriptor.arrayComponentType
                            )
                        }
                    }
                }

                At.Call -> {
                    val originDescriptor = resolveDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is InvokableDescriptor) { "631" }
                    kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Call)) { "632" }
                    HookOriginCallDescriptorWrapperParameter(originDescriptor)
                }
            }

            hasCancelAnnotation -> {
                kspRequire(at != At.Body) { "638" }
                kspRequire(hookDescriptor is MethodDescriptor) { "639" }
                val cancelDescriptor = resolveDescriptorReference(cancelGenericTypeClassDeclaration)
                kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Cancel)) { "641" }
                kspRequire(cancelDescriptor == hookDescriptor) { "642" }
                HookCancelDescriptorWrapperParameter(hookDescriptor)
            }

            hasOrdinalAnnotation -> {
                kspRequire(type == types.int) { "647" }
                kspRequire(function.hasOrdinals()) { "648" }
                HookOrdinalParameter
            }

            hasParamAnnotation -> {
                kspRequire(at != At.Body) { "653" }
                explicitParamName?.let { kspRequire(it.isNotEmpty()) { "654" } }
                val parameterName = explicitParamName ?: name
                val parameterIndex = hookDescriptor.parameters.indexOfFirstOrNull { it.name == parameterName }
                kspRequireNotNull(parameterIndex) { "657" }
                val (paramLocalType, isLocalVar) = resolveLocalType(type)
                kspRequire(hookDescriptor.parameters[parameterIndex].type == paramLocalType) { "659" }
                HookParamLocalParameter(parameterName, paramLocalType, parameterIndex, isLocalVar)
            }

            hasLocalAnnotation -> {
                kspRequire(at != At.Body) { "664" }
                val (bodyLocalType, isLocalVar) = resolveLocalType(type)
                HookBodyLocalParameter(
                    name,
                    bodyLocalType,
                    resolveLocal(explicitLocalOrdinal, explicitLocalName, name),
                    isLocalVar,
                )
            }

            hasShareAnnotation -> {
                kspRequire(type.declaration.isBuiltin(SimpleBuiltin.LocalVar)) { "675" }
                val type = kspRequireNotNull(type.findGenericType()) { "676" }
                explicitShareKey?.let { kspRequire(it.isNotEmpty()) { "677" } }
                HookShareLocalParameter(name, type, explicitShareKey ?: name, isShareExported)
            }

            else -> skipWithError { "681" }
        }
    }

    private fun SymbolSource.resolveLiteral(function: ParsedPatchFunction): Literal =
        kspRequireNotNull(
            with(function) {
                listOfNotNull(
                    atLiteralExplicitZero?.let { ZeroLiteral(atLiteralZeroConditions) },
                    atLiteralExplicitInt?.let {
                        kspRequire(it != 0) { "691" }
                        IntLiteral(it)
                    },
                    atLiteralExplicitFloat?.let(::FloatLiteral),
                    atLiteralExplicitLong?.let(::LongLiteral),
                    atLiteralExplicitDouble?.let(::DoubleLiteral),
                    atLiteralExplicitString?.let(::StringLiteral),
                    atLiteralExplicitClassType?.let {
                        kspRequire(atLiteralExplicitClassDeclaration?.isValid == true) { "699" }
                        ClassLiteral(atLiteralExplicitClassDeclaration)
                    },
                    atLiteralExplicitNull?.let { NullLiteral },
                ).singleOrNull()
            }
        ) { "705" }

    private fun SymbolSource.resolveOrdinals(ordinals: List<Int>): List<Int> {
        val invalidOrdinals = ordinals.filter { it < 0 }
        if (invalidOrdinals.isNotEmpty()) {
            invalidOrdinals.forEach {
                kspError { "Ordinal cannot be negative, but found: $it" }
            }
            skipSymbol()
        }
        return ordinals.toSet().toList()
    }

    private fun SymbolSource.resolveAccessRequest(
        hasAccessAnnotation: Boolean,
        isSchema: Boolean,
        isResolvable: Boolean,
        strategy: AccessStrategy?,
        unfinal: Boolean,
        fieldOps: List<Op>,
    ): AccessRequest? {
        if (!hasAccessAnnotation) return null
        kspRequireNotNull(strategy) { "727" }
        if (isSchema) {
            kspRequire(strategy == AccessStrategy.Tweak) { "729" }
        }
        if (strategy == AccessStrategy.Tweak) {
            kspRequire(isResolvable) { "732" }
            kspRequire(options.accessWidenerConfig != null || options.accessTransformerConfig != null) { "733" }
            return TweakAccessRequest(unfinal)
        }
        return MixinAccessRequest(unfinal, fieldOps)
    }

    private fun SymbolSource.resolveModifiers(modifiers: Set<JPModifier>, isMethod: Boolean): List<JPModifier> {
        val allowed = if (isMethod) JavaModifiers.methodAllowed else JavaModifiers.fieldAllowed
        kspRequire(allowed.containsAll(modifiers)) { "741" }
        kspRequire(modifiers.count { it in JavaModifiers.visibilities } <= 1) { "742" }
        if (isMethod) {
            kspRequire(modifiers.count { it in JavaModifiers.methodConflicts } <= 1) { "744" }
            if (JPModifier.ABSTRACT in modifiers) {
                kspRequire(modifiers.none { it in JavaModifiers.abstractIllegals }) { "746" }
            }
            if (JPModifier.NATIVE in modifiers) {
                kspRequire(JPModifier.DEFAULT !in modifiers) { "749" }
            }
        } else {
            if (JPModifier.FINAL in modifiers) {
                kspRequire(JPModifier.VOLATILE !in modifiers) { "753" }
            }
        }
        return modifiers.toList()
    }

    private fun SymbolSource.resolveDescriptorReference(classDeclaration: KSClassDeclaration?): Descriptor {
        kspRequire(classDeclaration?.isValid == true) { "760" }
        val qualifiedName = classDeclaration.qualifiedName?.asString()
        if (qualifiedName in invalidDescriptors) {
            skipWithError { "763" }
        }
        return validDescriptors[qualifiedName] ?: lapisError("Descriptor cannot be null")
    }

    private fun SymbolSource.resolveLocalType(type: KSType): Pair<KSType, Boolean> {
        val isLocalVar = type.declaration.isBuiltin(SimpleBuiltin.LocalVar)
        val localType = if (isLocalVar) {
            kspRequireNotNull(type.findGenericType()) { "771" }
        } else {
            kspRequireNotNull(type) { "773" }
        }
        return localType to isLocalVar
    }

    private fun SymbolSource.resolveLocal(ordinal: Int?, explicitName: String?, fallbackName: String?): DomainLocal =
        kspRequireNotNull(
            when {
                ordinal != null -> ordinal.takeIf { explicitName == null }?.let {
                    kspRequire(it >= 0) { "782" }
                    PositionalLocal(it)
                }

                explicitName != null -> {
                    kspRequire(explicitName.isNotEmpty()) { "787" }
                    NamedLocal(explicitName)
                }

                fallbackName != null -> NamedLocal(fallbackName)
                else -> null
            }
        ) { "794" }

    private fun SymbolSource.resolveMappingName(explicitName: String?, fallbackName: String): String =
        if (explicitName != null) {
            kspRequire(explicitName.isNotEmpty()) { "798" }
            explicitName
        } else {
            fallbackName
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
        } catch (_: SkipSymbolSignal) {
            null
        }

    private class SkipSymbolSignal : Exception()
}
