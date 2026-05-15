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
import io.github.recrafter.lapis.phases.common.JavaModifiers
import io.github.recrafter.lapis.phases.common.JvmClassName
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.parser.*
import io.github.recrafter.lapis.phases.validator.models.ValidatorResult
import io.github.recrafter.lapis.phases.validator.models.common.FunctionParameter
import io.github.recrafter.lapis.phases.validator.models.patches.*
import io.github.recrafter.lapis.phases.validator.models.patches.hooks.*
import io.github.recrafter.lapis.phases.validator.models.schemas.*
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
        kspRequire(classDeclaration?.isValid == true) { "60" }
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "61" }
        kspRequireNotNull(originJvmClassName) { "62" }
        kspRequire(originClassDeclaration?.isValid == true) { "63" }
        val hasSingleSchemaAnnotation = listOf(
            hasSchemaAnnotation,
            hasInnerSchemaAnnotation,
            hasLocalSchemaAnnotation,
            hasAnonymousSchemaAnnotation,
        ).count { it } == 1
        kspRequire(hasSingleSchemaAnnotation) { "70" }
        if (hasSchemaAnnotation) {
            kspRequire(isTopLevel) { "72" }
        }
        kspRequire(hasPackageName) { "74" }
        val accessRequest = resolveAccessRequest(
            AccessRequestSource.SCHEMA,
            hasAccessAnnotation,
            accessStrategy,
            isAccessUnfinal,
            isAccessible,
            emptyList(),
            emptyList(),
        )
        val qualifiedName = kspRequireNotNull(classDeclaration.qualifiedName?.asString()) { "84" }
        val descriptors = descriptors.mapNotNull { parsedDescriptor ->
            val descriptorQualifiedName = parsedDescriptor.classDeclaration.qualifiedName?.asString()
                ?: return@mapNotNull null
            val validatedDescriptor = runOrNullOnSkip {
                parsedDescriptor.validate(originClassDeclaration, originJvmClassName, isAccessible)
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
            side = kspRequireNotNull(side) { "104" },
            isAccessible = isAccessible,
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
        isAccessibleSchema: Boolean,
    ): Descriptor {
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "123" }
        kspRequire(superClassDeclaration?.isValid == true) { "124" }
        kspRequire(isObject) { "125" }
        val mappingName = resolveMappingName(explicitMappingName, name)
        val receiverType = schemaOriginClassDeclaration.starProjectedType
        val isField = superClassDeclaration.isBuiltin(SimpleBuiltin.Field)
        val accessRequest = resolveAccessRequest(
            if (isField) AccessRequestSource.FIELD else AccessRequestSource.INVOKABLE,
            hasAccessAnnotation,
            accessStrategy,
            isAccessUnfinal,
            isAccessibleSchema,
            accessFieldOps,
            emptyList(),
        )
        if (superClassDeclaration.isBuiltin(SimpleBuiltin.Field)) {
            kspRequire(genericType is ParsedTypeDescriptorGenericType) { "139" }
            kspRequireNotNull(genericType.type) { "140" }
            return FieldDescriptor(
                symbol = symbol,
                classDeclaration = classDeclaration,

                name = name,
                receiverType = receiverType,
                inaccessibleReceiverJvmClassName = if (isAccessibleSchema) null else schemaOriginJvmClassName,
                mappingName = mappingName,
                fieldType = genericType.type,
                arrayComponentType = genericType.arrayComponentType,
                isStatic = hasStaticAnnotation,
                accessRequest = accessRequest,
            )
        }
        kspRequire(genericType is ParsedFunctionTypeDescriptorGenericType) { "155" }
        kspRequire(genericType.receiverType == null) { "156" }
        val parameters = genericType.parameters.map { parameter ->
            kspRequire(!parameter.type.isFunctionType) { "158" }
            FunctionTypeParameter(
                type = parameter.type,
                name = parameter.name,
            )
        }
        return when {
            superClassDeclaration.isBuiltin(SimpleBuiltin.Method) -> {
                MethodDescriptor(
                    symbol = symbol,
                    classDeclaration = classDeclaration,

                    name = name,
                    receiverType = receiverType,
                    inaccessibleReceiverJvmClassName = if (isAccessibleSchema) null else schemaOriginJvmClassName,
                    returnType = genericType.returnType,
                    mappingName = mappingName,
                    parameters = parameters,
                    isStatic = hasStaticAnnotation,
                    accessRequest = accessRequest,
                )
            }

            superClassDeclaration.isBuiltin(SimpleBuiltin.Constructor) -> {
                kspRequire(genericType.returnType == null) { "182" }
                kspRequire(!hasMappingNameAnnotation) { "183" }
                if (accessRequest is MixinAccessRequest) {
                    kspRequire(isAccessibleSchema) { "185" }
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

            else -> skipWithError { "198" }
        }
    }

    private fun ParsedPatch.validate(): Patch {
        kspRequireNotNull(name) { "203" }
        kspRequireNotNull(side) { "204" }
        kspRequireNotNull(initStrategy) { "205" }
        kspRequire(classDeclaration?.isValid == true) { "206" }
        kspRequire(classDeclaration.typeParameters.isEmpty()) { "207" }
        kspRequire(schemaClassDeclaration?.isValid == true) { "208" }
        kspRequire(isTopLevel) { "209" }
        kspRequire(hasPackageName) { "210" }
        kspRequire(isPublic) { "211" }
        val schema = validSchemas[schemaClassDeclaration.qualifiedName?.asString()]
        kspRequireNotNull(schema) { "213" }
        kspRequire(isClass) { "214" }
        kspRequire(!isObject) { "215" }
        kspRequire(!isSealed) { "216" }
        kspRequire(!isOpen) { "217" }
        val constructor = kspRequireNotNull(constructors.singleOrNull()) { "218" }
        constructor.kspRequire(constructor.isPublic) { "219" }
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
            && hooks.all { it.methodDescriptor.isStatic }
        if (!hasStaticHooksOnly) {
            kspRequire(isAbstract) { "252" }
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
        kspRequire(isPublic) { "272" }
        return this
    }

    private fun ParsedPatchConstructorParameter.validate(schema: Schema): PatchConstructorParameter =
        when {
            hasOriginAnnotation -> {
                val instanceClassDeclaration = type.toClassDeclaration()
                kspRequire(instanceClassDeclaration == schema.originClassDeclaration) { "280" }
                kspRequire(type.arguments.none { it.variance != Variance.STAR }) { "281" }
                PatchConstructorOriginParameter
            }

            else -> skipWithError { "285" }
        }

    private fun ParsedPatchProperty.validateAsExtension(schema: Schema): ExtensionProperty {
        kspRequireNotNull(getterJvmName) { "289" }
        kspRequire(isPublic) { "290" }
        kspRequire(!isExtension) { "291" }
        kspRequire(schema.isAccessible) { "292" }
        kspRequire(!isOpen && !isAbstract) { "293" }
        return ExtensionProperty(
            name = name,
            getterJvmName = getterJvmName,
            setterJvmName = if (isMutable) kspRequireNotNull(setterJvmName) { "297" } else null,
            type = type,
        )
    }

    private fun ParsedPatchFunction.validateAsExtension(isAccessibleSchema: Boolean): ExtensionFunction {
        kspRequire(isPublic) { "303" }
        kspRequire(!hasExtensionReceiver) { "304" }
        kspRequire(isAccessibleSchema) { "305" }
        kspRequire(!isOpen && !isAbstract) { "306" }
        val parameters = parameters.map {
            FunctionParameter(
                name = kspRequireNotNull(it.name) { "309" },
                type = kspRequireNotNull(it.type) { "310" },
            )
        }
        return ExtensionFunction(
            name = name,
            jvmName = jvmName,
            parameters = parameters,
            returnType = returnType,
        )
    }

    private fun ParsedPatchProperty.validateAsShadow(): ShadowProperty {
        kspRequireNotNull(getterJvmName) { "322" }
        kspRequire(isPublic) { "323" }
        kspRequire(!isExtension) { "324" }
        kspRequire(isAbstract) { "325" }
        val mappingName = resolveMappingName(explicitMappingName, name)
        val shadowModifiers = resolveModifiers(shadowModifiers, isMethod = false)
        return ShadowProperty(
            name = name,
            getterJvmName = getterJvmName,
            setterJvmName = if (isMutable) kspRequireNotNull(setterJvmName) { "331" } else null,
            mappingName = mappingName,
            modifiers = shadowModifiers,
            type = type,
        )
    }

    private fun ParsedPatchFunction.validateAsShadow(): ShadowFunction {
        kspRequire(isPublic) { "339" }
        kspRequire(isAbstract) { "340" }
        kspRequire(!hasExtensionReceiver) { "341" }
        val parameters = parameters.map {
            FunctionParameter(
                name = kspRequireNotNull(it.name) { "344" },
                type = kspRequireNotNull(it.type) { "345" },
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
            modifiers = shadowModifiers,
        )
    }

    private fun ParsedPatchFunction.validateAsHook(isInCompanionObject: Boolean): PatchHook {
        kspRequireNotNull(hookAt) { "361" }
        kspRequire(!isOpen) { "362" }
        kspRequire(!hasTypeParameters) { "363" }
        val hookMethodDescriptor = resolveDescriptorReference(hookDescClassDeclaration)
        kspRequire(hookMethodDescriptor is InvokableDescriptor) { "365" }
        if (hookMethodDescriptor.isStatic) {
            kspRequire(isInCompanionObject) { "367" }
        } else {
            kspRequire(!isInCompanionObject) { "369" }
        }
        val ordinals: (List<Int>) -> List<Int> = { resolveOrdinals(it) }
        val parameters: () -> List<HookParameter> = {
            parameters.mapNotNull { parameter ->
                runOrNullOnSkip { parameter.validateAsHookParameter(this@validateAsHook, hookAt, hookMethodDescriptor) }
            }
        }
        return when (hookAt) {
            At.Head -> {
                kspRequire(returnType == null) { "379" }
                when (hookMethodDescriptor) {
                    is ConstructorDescriptor -> {
                        kspRequire(hasAtConstructorHeadAnnotation) { "382" }
                        ConstructorHeadHook(
                            jvmName = jvmName,
                            methodDescriptor = hookMethodDescriptor,
                            phase = kspRequireNotNull(atConstructorHeadPhase) { "386" },
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

            At.Body -> {
                kspRequire(hookMethodDescriptor is MethodDescriptor) { "400" }
                kspRequire(returnType == hookMethodDescriptor.returnType) { "401" }
                BodyHook(
                    jvmName = jvmName,
                    methodDescriptor = hookMethodDescriptor,
                    returnType = returnType,
                    parameters = parameters(),
                )
            }

            At.Tail -> {
                kspRequire(returnType == null) { "411" }
                TailHook(
                    jvmName = jvmName,
                    methodDescriptor = hookMethodDescriptor,
                    parameters = parameters(),
                )
            }

            At.Local -> {
                kspRequire(hasAtLocalAnnotation) { "420" }
                kspRequireNotNull(atLocalOp) { "421" }
                kspRequireNotNull(atLocalType) { "422" }
                kspRequire(returnType == atLocalType) { "423" }
                LocalHook(
                    jvmName = jvmName,
                    methodDescriptor = hookMethodDescriptor,
                    type = atLocalType,
                    ordinals = ordinals(atLocalOpOrdinals),
                    local = resolveLocal(atLocalExplicitOrdinal, atLocalExplicitName, null),
                    op = atLocalOp,
                    parameters = parameters(),
                )
            }

            At.Instanceof -> {
                kspRequire(hasAtInstanceofAnnotation) { "436" }
                kspRequire(atInstanceofTypeClassDeclaration?.isValid == true) { "437" }
                kspRequire(returnType?.toClassName()?.asIrTypeName() == KPBoolean.asIrTypeName()) { "438" }
                InstanceofHook(
                    jvmName = jvmName,
                    methodDescriptor = hookMethodDescriptor,
                    typeClassDeclaration = atInstanceofTypeClassDeclaration,
                    returnType = returnType,
                    ordinals = ordinals(atInstanceofOrdinals),
                    parameters = parameters(),
                )
            }

            At.Return -> {
                kspRequire(hasAtReturnAnnotation) { "450" }
                kspRequire(returnType == hookMethodDescriptor.returnType) { "451" }
                ReturnHook(
                    jvmName = jvmName,
                    methodDescriptor = hookMethodDescriptor,
                    type = returnType,
                    ordinals = ordinals(atReturnOrdinals),
                    parameters = parameters(),
                )
            }

            At.Literal -> {
                kspRequire(hasAtLiteralAnnotation) { "462" }
                val literal = resolveLiteral(this@validateAsHook)
                val type = literal.getType(types)
                if (literal !is NullHookLiteral) {
                    if (literal !is StringHookLiteral && literal !is ClassHookLiteral) {
                        kspRequire(returnType?.isMarkedNullable == false) { "467" }
                    }
                    kspRequire(returnType == type) { "469" }
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

            At.Field -> {
                kspRequire(hasAtFieldAnnotation) { "482" }
                kspRequireNotNull(atFieldOp) { "483" }
                val targetDescriptor = resolveDescriptorReference(atFieldDescClassDeclaration)
                kspRequire(targetDescriptor is FieldDescriptor) { "485" }
                when (atFieldOp) {
                    Op.Get -> {
                        kspRequire(returnType?.makeNotNullable() == targetDescriptor.fieldType) { "488" }
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
                        kspRequire(returnType == null) { "500" }
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

            At.Array -> {
                kspRequire(hasAtArrayAnnotation) { "514" }
                kspRequireNotNull(atArrayOp) { "515" }
                val targetDescriptor = resolveDescriptorReference(atArrayDescClassDeclaration)
                kspRequire(targetDescriptor is FieldDescriptor) { "517" }
                kspRequireNotNull(targetDescriptor.arrayComponentType) { "518" }
                when (atArrayOp) {
                    Op.Get -> kspRequire(returnType == targetDescriptor.arrayComponentType) { "520" }
                    Op.Set -> kspRequire(returnType == null) { "521" }
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

            At.Call -> {
                kspRequire(hasAtCallAnnotation) { "536" }
                val targetDescriptor = resolveDescriptorReference(atCallDescClassDeclaration)
                kspRequire(targetDescriptor is MethodDescriptor) { "538" }
                kspRequire(returnType?.makeNotNullable() == targetDescriptor.returnType) { "539" }
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
        at: At,
        hookDescriptor: InvokableDescriptor,
    ): HookParameter {
        kspRequireNotNull(name) { "557" }
        kspRequireNotNull(type) { "558" }
        kspRequire(!hasDefaultArgument) { "559" }
        return when {
            hasOriginAnnotation -> when (at) {
                At.Head, At.Tail -> skipWithError { "562" }

                At.Body -> {
                    val originDescriptor = resolveDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is MethodDescriptor) { "566" }
                    kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Body)) { "567" }
                    HookOriginBodyDescriptorWrapperParameter(originDescriptor)
                }

                At.Local -> {
                    kspRequire(type == function.returnType) { "572" }
                    HookOriginValueParameter
                }

                At.Instanceof -> {
                    kspRequire(type.declaration.isBuiltin(SimpleBuiltin.Instanceof)) { "577" }
                    HookOriginInstanceofWrapperParameter
                }

                At.Return -> {
                    kspRequireNotNull(hookDescriptor.returnType) { "582" }
                    kspRequire(type == hookDescriptor.returnType) { "583" }
                    HookOriginValueParameter
                }

                At.Literal -> {
                    val literal = resolveLiteral(function)
                    kspRequire(literal !is NullHookLiteral) { "589" }
                    kspRequire(type == literal.getType(types)) { "590" }
                    HookOriginValueParameter
                }

                At.Field -> {
                    kspRequireNotNull(function.atFieldOp) { "595" }
                    val originDescriptor = resolveDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is FieldDescriptor) { "597" }
                    when (function.atFieldOp) {
                        Op.Get -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.FieldGet)) { "600" }
                            HookOriginFieldGetDescriptorWrapperParameter(originDescriptor)
                        }

                        Op.Set -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.FieldSet)) { "605" }
                            HookOriginFieldSetDescriptorWrapperParameter(originDescriptor)
                        }
                    }
                }

                At.Array -> {
                    kspRequireNotNull(function.atArrayOp) { "612" }
                    val originDescriptor = resolveDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is FieldDescriptor) { "614" }
                    kspRequireNotNull(originDescriptor.arrayComponentType) { "615" }
                    when (function.atArrayOp) {
                        Op.Get -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.ArrayGet)) { "618" }
                            HookOriginArrayGetDescriptorWrapperParameter(
                                originDescriptor,
                                originDescriptor.arrayComponentType
                            )
                        }

                        Op.Set -> {
                            kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.ArraySet)) { "626" }
                            HookOriginArraySetDescriptorWrapperParameter(
                                originDescriptor,
                                originDescriptor.arrayComponentType
                            )
                        }
                    }
                }

                At.Call -> {
                    val originDescriptor = resolveDescriptorReference(originGenericTypeClassDeclaration)
                    kspRequire(originDescriptor is InvokableDescriptor) { "637" }
                    kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Call)) { "638" }
                    HookOriginCallDescriptorWrapperParameter(originDescriptor)
                }
            }

            hasCancelAnnotation -> {
                kspRequire(at != At.Body) { "644" }
                kspRequire(hookDescriptor is MethodDescriptor) { "645" }
                val cancelDescriptor = resolveDescriptorReference(cancelGenericTypeClassDeclaration)
                kspRequire(type.declaration.isBuiltin(DescriptorWrapperBuiltin.Cancel)) { "647" }
                kspRequire(cancelDescriptor == hookDescriptor) { "648" }
                HookCancelDescriptorWrapperParameter(hookDescriptor)
            }

            hasOrdinalAnnotation -> {
                kspRequire(type == types.int) { "653" }
                kspRequire(function.hasOrdinals()) { "654" }
                HookOrdinalParameter
            }

            hasParamAnnotation -> {
                kspRequire(at != At.Body) { "659" }
                explicitParamName?.let { kspRequire(it.isNotEmpty()) { "660" } }
                val parameterName = explicitParamName ?: name
                val parameterIndex =
                    hookDescriptor.functionTypeParameters.indexOfFirstOrNull { it.name == parameterName }
                kspRequireNotNull(parameterIndex) { "664" }
                val (paramLocalType, isLocalVar) = resolveLocalType(type)
                kspRequire(hookDescriptor.functionTypeParameters[parameterIndex].type == paramLocalType) { "666" }
                HookParamLocalParameter(parameterName, paramLocalType, parameterIndex, isLocalVar)
            }

            hasLocalAnnotation -> {
                kspRequire(at != At.Body) { "671" }
                val (bodyLocalType, isLocalVar) = resolveLocalType(type)
                HookBodyLocalParameter(
                    name,
                    bodyLocalType,
                    resolveLocal(explicitLocalOrdinal, explicitLocalName, name),
                    isLocalVar,
                )
            }

            hasShareAnnotation -> {
                kspRequire(type.declaration.isBuiltin(SimpleBuiltin.LocalVar)) { "682" }
                val type = kspRequireNotNull(type.findGenericType()) { "683" }
                explicitShareKey?.let { kspRequire(it.isNotEmpty()) { "684" } }
                HookShareLocalParameter(name, type, explicitShareKey ?: name, isShareExported)
            }

            else -> skipWithError { "688" }
        }
    }

    private fun SymbolSource.resolveLiteral(function: ParsedPatchFunction): HookLiteral =
        kspRequireNotNull(
            with(function) {
                listOfNotNull(
                    atLiteralExplicitZero?.let { ZeroHookLiteral(atLiteralZeroConditions) },
                    atLiteralExplicitInt?.let {
                        kspRequire(it != 0) { "698" }
                        IntHookLiteral(it)
                    },
                    atLiteralExplicitFloat?.let(::FloatHookLiteral),
                    atLiteralExplicitLong?.let(::LongHookLiteral),
                    atLiteralExplicitDouble?.let(::DoubleHookLiteral),
                    atLiteralExplicitString?.let(::StringHookLiteral),
                    atLiteralExplicitClassType?.let {
                        kspRequire(atLiteralExplicitClassDeclaration?.isValid == true) { "706" }
                        ClassHookLiteral(atLiteralExplicitClassDeclaration)
                    },
                    atLiteralExplicitNull?.let { NullHookLiteral },
                ).singleOrNull()
            }
        ) { "712" }

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

    private enum class AccessRequestSource { SCHEMA, FIELD, INVOKABLE }

    private fun SymbolSource.resolveAccessRequest(
        source: AccessRequestSource,
        hasAccessAnnotation: Boolean,
        accessStrategy: AccessStrategy?,
        isAccessUnfinal: Boolean,
        isAccessibleSchema: Boolean,
        fieldOps: List<Op>,
        functionTypeParameters: List<FunctionTypeParameter>,
    ): AccessRequest? {
        if (!hasAccessAnnotation) return null
        kspRequireNotNull(accessStrategy) { "737" }
        if (source == AccessRequestSource.SCHEMA) {
            kspRequire(accessStrategy == AccessStrategy.Tweak) { "739" }
        }
        if (accessStrategy == AccessStrategy.Tweak) {
            kspRequire(isAccessibleSchema) { "742" }
            kspRequire(options.accessWidenerConfig != null || options.accessTransformerConfig != null) { "743" }
            return TweakAccessRequest(isAccessUnfinal)
        }
        if (source == AccessRequestSource.FIELD) {
            kspRequire(fieldOps.isNotEmpty()) { "747" }
            return MixinFieldAccessRequest(isAccessUnfinal, fieldOps)
        }
        kspRequire(!isAccessUnfinal) { "750" }
        val parameters = mutableListOf<IrParameter>()
        val anonymousParameterIndices = mutableListOf<Int>()
        functionTypeParameters.forEachIndexed { index, functionTypeParameter ->
            if (functionTypeParameter.name != null) {
                parameters += IrParameter(functionTypeParameter.name, functionTypeParameter.typeName)
            } else {
                anonymousParameterIndices += index
            }
        }
        kspRequire(anonymousParameterIndices.isEmpty()) { "760" }
        return MixinInvokableAccessRequest(parameters)
    }

    private fun SymbolSource.resolveModifiers(modifiers: List<JPModifier>, isMethod: Boolean): List<JPModifier> {
        val set = modifiers.toSet()
        val allowed = if (isMethod) JavaModifiers.methodAllowed else JavaModifiers.fieldAllowed
        kspRequire(allowed.containsAll(set)) { "767" }
        kspRequire(set.count { it in JavaModifiers.visibilities } <= 1) { "768" }
        if (isMethod) {
            kspRequire(set.count { it in JavaModifiers.methodConflicts } <= 1) { "770" }
            if (JPModifier.ABSTRACT in set) {
                kspRequire(set.none { it in JavaModifiers.abstractIllegals }) { "772" }
            }
            if (JPModifier.NATIVE in set) {
                kspRequire(JPModifier.DEFAULT !in set) { "775" }
            }
        } else {
            if (JPModifier.FINAL in set) {
                kspRequire(JPModifier.VOLATILE !in set) { "779" }
            }
        }
        return set.toList()
    }

    private fun SymbolSource.resolveDescriptorReference(classDeclaration: KSClassDeclaration?): Descriptor {
        kspRequire(classDeclaration?.isValid == true) { "786" }
        val qualifiedName = classDeclaration.qualifiedName?.asString()
        if (qualifiedName in invalidDescriptors) {
            skipWithError { "789" }
        }
        return validDescriptors[qualifiedName] ?: lapisError("Descriptor cannot be null")
    }

    private fun SymbolSource.resolveLocalType(type: KSType): Pair<KSType, Boolean> {
        val isLocalVar = type.declaration.isBuiltin(SimpleBuiltin.LocalVar)
        val localType = if (isLocalVar) {
            kspRequireNotNull(type.findGenericType()) { "797" }
        } else {
            kspRequireNotNull(type) { "799" }
        }
        return localType to isLocalVar
    }

    private fun SymbolSource.resolveLocal(ordinal: Int?, explicitName: String?, fallbackName: String?): HookLocal =
        kspRequireNotNull(
            when {
                ordinal != null -> ordinal.takeIf { explicitName == null }?.let {
                    kspRequire(it >= 0) { "808" }
                    PositionalLocal(it)
                }

                explicitName != null -> {
                    kspRequire(explicitName.isNotEmpty()) { "813" }
                    NamedLocal(explicitName)
                }

                fallbackName != null -> NamedLocal(fallbackName)
                else -> null
            }
        ) { "820" }

    private fun SymbolSource.resolveMappingName(explicitName: String?, fallbackName: String): String =
        if (explicitName != null) {
            kspRequire(explicitName.isNotEmpty()) { "824" }
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
