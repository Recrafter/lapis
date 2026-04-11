package io.github.recrafter.lapis.layers.validator

import com.google.devtools.ksp.isAbstract
import io.github.recrafter.lapis.LapisLogger
import io.github.recrafter.lapis.Options
import io.github.recrafter.lapis.annotations.At
import io.github.recrafter.lapis.annotations.AtLiteral
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.ks.*
import io.github.recrafter.lapis.layers.generator.builders.Remapper
import io.github.recrafter.lapis.layers.generator.builtins.Builtin
import io.github.recrafter.lapis.layers.generator.builtins.Builtins
import io.github.recrafter.lapis.layers.generator.builtins.DescBuiltin
import io.github.recrafter.lapis.layers.parser.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class FrontendValidator(
    private val logger: LapisLogger,
    private val options: Options,
    private val builtins: Builtins,
) {
    private val validSchemas: MutableMap<String, Schema> = mutableMapOf()

    private val validDescriptors: MutableMap<String, Desc> = mutableMapOf()
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
        kspRequire(classDecl?.isValid == true) { "39" }
        kspRequire(targetClassDecl?.isValid == true) { "40" }
        kspRequireNotNull(targetBinaryName) { "41" }
        kspRequire(classDecl.typeParameters.isEmpty()) { "42" }
        val qualifiedName = kspRequireNotNull(classDecl.qualifiedName?.asString()) { "43" }

        val isAccessPending = targetClassDecl.qualifiedName?.asString() != targetBinaryName
        val descriptors = if (isAccessPending) {
            emptyList()
        } else {
            parsedSchema.descriptors.mapNotNull { parsedDesc ->
                val descQualifiedName = parsedDesc.classDecl.qualifiedName?.asString() ?: return@mapNotNull null
                val validatedDesc = runOrNullOnSkip { validateDesc(targetClassDecl, parsedDesc) }
                if (validatedDesc != null) {
                    validDescriptors[descQualifiedName] = validatedDesc
                } else {
                    invalidDescriptors += descQualifiedName
                }
                return@mapNotNull validatedDesc
            }
        }
        val schema = Schema(
            source = symbol,
            classDecl = classDecl,
            targetClassDecl = targetClassDecl,
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

    private fun validateDesc(schemaTargetClassDecl: KSClassDecl, desc: ParsedDesc): Desc = with(desc) {
        kspRequireNotNull(name) { "82" }
        kspRequire(classDecl.typeParameters.isEmpty()) { "83" }
        kspRequire(superClassDecl?.isValid == true) { "84" }
        kspRequireNotNull(callable) { "85" }
        if (hasAccessAnnotation) {
            kspRequire(options.accessWidenerConfigName != null || options.accessTransformerConfigName != null) {
                "88"
            }
        }
        val receiverType = schemaTargetClassDecl.asStarProjectedType()
        if (superClassDecl.isInstance(builtins[Builtin.Field])) {
            kspRequire(generic is ParsedTypeDescGeneric) { "93" }
            kspRequireNotNull(generic.type) { "94" }
            if (callable !is PrivateCallable) {
                kspRequire(callable is ParsedFieldDescCallable) { "95" }
            }
            return FieldDesc(
                name = name,
                classDecl = classDecl,
                receiverType = receiverType,
                targetName = kspRequireNotNull(callable.name) { "100" },
                fieldType = generic.type,
                arrayComponentType = generic.arrayComponentType,
                isStatic = hasStaticAnnotation,
                makePublic = hasAccessAnnotation,
                removeFinal = unfinal,
            )
        }
        kspRequire(generic is ParsedFunctionTypeDescGeneric) { "108" }
        val parameters = generic.parameters.map { parameter ->
            kspRequire(!parameter.type.isFunctionType) { "110" }
            FunctionTypeParameter(
                type = parameter.type,
                name = parameter.name,
            )
        }
        return when {
            superClassDecl.isInstance(builtins[Builtin.Method]) -> {
                if (callable !is PrivateCallable) {
                    kspRequire(callable is ParsedMethodDescCallable) { "118" }
                }
                if (!hasStaticAnnotation) {
                    kspRequireNotNull(generic.receiverType) { "120" }
                }
                MethodDesc(
                    name = name,
                    classDecl = classDecl,
                    receiverType = receiverType,
                    returnType = generic.returnType,
                    targetName = kspRequireNotNull(callable.name) { "127" },
                    parameters = parameters,
                    isStatic = hasStaticAnnotation,
                    makePublic = hasAccessAnnotation,
                    removeFinal = unfinal,
                )
            }

            superClassDecl.isInstance(builtins[Builtin.Constructor]) -> {
                if (callable !is PrivateCallable) {
                    kspRequire(callable is ParsedConstructorDescCallable) { "136" }
                }
                kspRequire(!unfinal) { "96" }
                ConstructorDesc(
                    name = name,
                    classDecl = classDecl,
                    returnType = kspRequireNotNull(generic.returnType) { "140" },
                    parameters = parameters,
                    makePublic = hasAccessAnnotation,
                )
            }

            else -> kspError { "147" }
        }
    }

    private fun validatePatch(patch: ParsedPatch): Patch = with(patch) {
        kspRequireNotNull(name) { "152" }
        kspRequire(schemaClassDecl?.isValid == true) { "153" }
        kspRequire(superGenericClassDecl?.isValid == true) { "154" }
        kspRequireNotNull(side) { "155" }
        kspRequire(classDecl?.run { isAbstract() && !isInner && isClass } == true) { "156" }
        kspRequire(classDecl.typeParameters.isEmpty()) { "157" }
        kspRequire(superClassDecl?.isInstance(builtins[Builtin.Patch]) == true) { "158" }
        val schema = validSchemas[schemaClassDecl.qualifiedName?.asString()]
        kspRequire(superGenericClassDecl.isSame(schema?.targetClassDecl)) { "160" }
        return Patch(
            source = symbol,

            name = name,
            side = side,

            classDecl = classDecl,
            targetClassDecl = superGenericClassDecl,

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
                runOrNullOnSkip { validateSharedFunction(it) }
            },
            hooks = functions.filter { it.hasHookAnnotation }.mapNotNull {
                runOrNullOnSkip { validateHook(it) }
            },
        )
    }

    private fun validateSharedFunction(function: ParsedPatchFunction): SharedFunction = with(function) {
        SharedFunction(
            name = name,
            parameters = function.parameters.map {
                FunctionParameter(
                    name = kspRequireNotNull(it.name) { "193" },
                    type = kspRequireNotNull(it.type) { "194" },
                )
            },
            returnType = function.returnType,
        )
    }

    private fun validateHook(function: ParsedPatchFunction): DomainHook = with(function) {
        kspRequireNotNull(hookAt) { "202" }
        kspRequire(!function.hasTypeParameters) { "203" }
        val hookDesc = validateDescReference(hookDescClassDecl)
        kspRequire(hookDesc is InvokableDesc) { "205" }
        if (hookDesc.isStatic) {
            kspRequire(fromCompanionObject) { "207" }
        } else {
            kspRequire(!fromCompanionObject) { "209" }
        }
        val ordinals: Remapper<List<Int>> = { validateOrdinals(it) }
        val parameters: () -> List<HookParameter> = {
            function.parameters.mapNotNull { parameter ->
                runOrNullOnSkip { validateHookParameter(parameter, function, hookAt, hookDesc) }
            }
        }
        when (hookAt) {
            At.Head -> {
                kspRequire(returnType == null) { "219" }
                when (hookDesc) {
                    is ConstructorDesc -> {
                        kspRequire(hasAtConstructorHeadAnnotation) { "222" }
                        ConstructorHeadHook(
                            name = name,
                            desc = hookDesc,
                            phase = kspRequireNotNull(atConstructorHeadPhase) { "226" },
                            parameters = parameters(),
                        )
                    }

                    is MethodDesc -> {
                        MethodHeadHook(
                            name = name,
                            desc = hookDesc,
                            parameters = parameters(),
                        )
                    }
                }
            }

            At.Body -> {
                kspRequire(hookDesc is MethodDesc) { "242" }
                kspRequire(returnType == hookDesc.returnType) { "243" }
                BodyHook(
                    name = name,
                    targetDesc = hookDesc,
                    returnType = returnType,
                    parameters = parameters(),
                )
            }

            At.Tail -> {
                kspRequire(returnType == null) { "253" }
                TailHook(
                    name = name,
                    desc = hookDesc,
                    parameters = parameters(),
                )
            }

            At.Local -> {
                kspRequire(hasAtLocalAnnotation) { "262" }
                kspRequireNotNull(atLocalOp) { "263" }
                kspRequireNotNull(atLocalType) { "264" }
                kspRequire(returnType == atLocalType) { "265" }
                LocalHook(
                    name = name,
                    desc = hookDesc,
                    type = atLocalType,
                    ordinals = ordinals(atReturnOrdinals),
                    local = validateLocal(atLocalOrdinal, atLocalName),
                    isSet = atLocalOp == Op.Set,
                    parameters = parameters(),
                )
            }

            At.Instanceof -> TODO()

            At.Return -> {
                kspRequire(hasAtReturnAnnotation) { "280" }
                kspRequire(returnType == hookDesc.returnType) { "281" }
                ReturnHook(
                    name = name,
                    desc = hookDesc,
                    type = returnType,
                    ordinals = ordinals(atReturnOrdinals),
                    parameters = parameters(),
                )
            }

            At.Literal -> {
                kspRequire(hasAtLiteralAnnotation) { "292" }
                val (_, argType, _) = kspRequireNotNull(atLiteralArguments.singleOrNull()) { "293" }
                kspRequireNotNull(argType) { "294" }
                val literal = validateLiteral(function)
                val kClass = literal.kClass
                if (kClass != null) {
                    if (literal !is StringLiteral && literal !is ClassLiteral) {
                        kspRequire(returnType?.isMarkedNullable == false) { "299" }
                    }
                    kspRequire(returnType?.isSame(kClass) == true) { "301" }
                }
                LiteralHook(
                    name = name,
                    desc = hookDesc,
                    type = argType,
                    literal = literal,
                    ordinals = ordinals(atLiteralOrdinals),
                    parameters = parameters(),
                )
            }

            At.Field -> {
                kspRequire(hasAtFieldAnnotation) { "314" }
                kspRequireNotNull(atFieldOp) { "315" }
                val targetDesc = validateDescReference(atFieldDescClassDecl)
                kspRequire(targetDesc is FieldDesc) { "317" }
                when (atFieldOp) {
                    Op.Get -> {
                        kspRequire(returnType?.makeNotNullable() == targetDesc.fieldType) { "320" }
                        FieldGetHook(
                            name = name,
                            desc = hookDesc,
                            type = targetDesc.fieldType,
                            ordinals = ordinals(atFieldOrdinals),
                            targetDesc = targetDesc,
                            parameters = parameters(),
                        )
                    }

                    Op.Set -> {
                        kspRequire(returnType == null) { "332" }
                        FieldSetHook(
                            name = name,
                            desc = hookDesc,
                            type = targetDesc.fieldType,
                            ordinals = ordinals(atFieldOrdinals),
                            targetDesc = targetDesc,
                            parameters = parameters(),
                        )
                    }
                }
            }

            At.Array -> {
                kspRequire(hasAtArrayAnnotation) { "346" }
                kspRequireNotNull(atArrayOp) { "347" }
                val targetDesc = validateDescReference(atArrayDescClassDecl)
                kspRequire(targetDesc is FieldDesc) { "349" }
                kspRequireNotNull(targetDesc.arrayComponentType) { "350" }
                when (atArrayOp) {
                    Op.Get -> kspRequire(returnType == targetDesc.arrayComponentType) { "353" }
                    Op.Set -> kspRequire(returnType == null) { "353" }
                }
                ArrayHook(
                    name = name,
                    desc = hookDesc,
                    op = atArrayOp,
                    type = targetDesc.fieldType,
                    componentType = targetDesc.arrayComponentType,
                    targetDesc = targetDesc,
                    ordinals = ordinals(atFieldOrdinals),
                    parameters = parameters(),
                )
            }

            At.Call -> {
                kspRequire(hasAtCallAnnotation) { "371" }
                val targetDesc = validateDescReference(atCallDescClassDecl)
                kspRequire(targetDesc is MethodDesc) { "373" }
                kspRequire(returnType?.makeNotNullable() == targetDesc.returnType) { "374" }
                CallHook(
                    name = name,
                    desc = hookDesc,
                    returnType = returnType,
                    targetDesc = targetDesc,
                    ordinals = ordinals(atCallOrdinals),
                    parameters = parameters(),
                )
            }
        }
    }

    private fun validateLiteral(function: ParsedPatchFunction): Literal = with(function) {
        val (argName, argType, argValue) = kspRequireNotNull(atLiteralArguments.singleOrNull()) { "388" }
        kspRequireNotNull(argType) { "389" }
        kspRequireNotNull(argValue) { "390" }
        kspRequireNotNull(argName) { "391" }
        when (argName) {
            AtLiteral::zero.name -> ZeroLiteral(atLiteralZeroConditions)
            AtLiteral::int.name -> {
                kspRequireNotNull(atLiteralInt) { "395" }
                kspRequire(atLiteralInt != 0) { "396" }
                IntLiteral(atLiteralInt)
            }

            AtLiteral::float.name -> FloatLiteral(kspRequireNotNull(atLiteralFloat) { "400" })
            AtLiteral::long.name -> LongLiteral(kspRequireNotNull(atLiteralLong) { "401" })
            AtLiteral::double.name -> DoubleLiteral(kspRequireNotNull(atLiteralDouble) { "402" })
            AtLiteral::string.name -> StringLiteral(kspRequireNotNull(atLiteralString) { "403" })
            AtLiteral::`class`.name -> ClassLiteral(kspRequireNotNull(argType.getClassDecl()) { "404" })
            AtLiteral::`null`.name -> NullLiteral
            else -> kspError { "406" }
        }
    }

    private fun SymbolSource.validateLocal(
        ordinal: Int?,
        explicitName: String?,
        implicitName: String? = explicitName
    ): DomainLocal =
        when {
            ordinal != null && explicitName == null -> {
                kspRequire(ordinal >= 0) { "417" }
                PositionalLocal(ordinal)
            }

            explicitName != null && ordinal == null -> NamedLocal(explicitName)
            ordinal == null && implicitName != null -> NamedLocal(implicitName)

            else -> kspError { "424" }
        }

    private fun SymbolSource.validateOrdinals(ordinals: List<Int>): List<Int> {
        ordinals.forEach {
            kspRequire(it >= 0) { "429" }
        }
        return ordinals.toSortedSet().toList()
    }

    private fun SymbolSource.validateDescReference(classDecl: KSClassDecl?): Desc {
        kspRequire(classDecl?.isValid == true) { "435" }
        val qualifiedName = classDecl.qualifiedName?.asString()
        if (invalidDescriptors.contains(qualifiedName)) {
            kspError { "438" }
        }
        return validDescriptors[qualifiedName] ?: lapisError("Failed to find descriptor by $qualifiedName")
    }

    private fun validateHookParameter(
        parameter: ParsedPatchFunctionParameter,
        function: ParsedPatchFunction,
        at: At,
        hookDesc: InvokableDesc,
    ): HookParameter =
        with(parameter) {
            kspRequireNotNull(name) { "450" }
            kspRequireNotNull(type) { "451" }
            kspRequire(!hasDefaultArgument) { "452" }
            when {
                hasOriginAnnotation -> when (at) {
                    At.Head, At.Tail -> kspError { "455" }

                    At.Body -> {
                        val originDesc = validateDescReference(originGenericClassDecl)
                        kspRequire(originDesc is InvokableDesc) { "459" }
                        kspRequire(type.getClassDecl()?.isInstance(builtins[DescBuiltin.Body]) == true) { "460" }
                        HookOriginDescBodyParameter(originDesc)
                    }

                    At.Local -> {
                        kspRequire(type == function.returnType) { "465" }
                        HookOriginValueParameter()
                    }

                    At.Instanceof -> TODO()

                    At.Return -> {
                        kspRequireNotNull(hookDesc.returnType) { "472" }
                        kspRequire(type == hookDesc.returnType) { "473" }
                        HookOriginValueParameter()
                    }

                    At.Literal -> {
                        val kClass = validateLiteral(function).kClass
                        kspRequireNotNull(kClass) { "479" }
                        kspRequire(type.isSame(kClass)) { "480" }
                        HookOriginValueParameter()
                    }

                    At.Field -> {
                        kspRequireNotNull(function.atFieldOp) { "485" }
                        val originDesc = validateDescReference(originGenericClassDecl)
                        kspRequire(originDesc is FieldDesc) { "487" }
                        when (function.atFieldOp) {
                            Op.Get -> {
                                kspRequire(type.getClassDecl()?.isInstance(builtins[DescBuiltin.FieldGet]) == true) {
                                    "491"
                                }
                                HookOriginDescFieldGetParameter(originDesc)
                            }

                            Op.Set -> {
                                kspRequire(type.getClassDecl()?.isInstance(builtins[DescBuiltin.FieldSet]) == true) {
                                    "498"
                                }
                                HookOriginDescFieldSetParameter(originDesc)
                            }
                        }
                    }

                    At.Array -> {
                        kspRequireNotNull(function.atArrayOp) { "506" }
                        val originDesc = validateDescReference(originGenericClassDecl)
                        kspRequire(originDesc is FieldDesc) { "508" }
                        kspRequireNotNull(originDesc.arrayComponentType) { "509" }
                        when (function.atArrayOp) {
                            Op.Get -> {
                                kspRequire(type.getClassDecl()?.isInstance(builtins[DescBuiltin.ArrayGet]) == true) {
                                    "513"
                                }
                                HookOriginDescArrayGetParameter(originDesc, originDesc.arrayComponentType)
                            }

                            Op.Set -> {
                                kspRequire(type.getClassDecl()?.isInstance(builtins[DescBuiltin.ArraySet]) == true) {
                                    "513"
                                }
                                HookOriginDescArraySetParameter(originDesc, originDesc.arrayComponentType)
                            }
                        }
                    }

                    At.Call -> {
                        val originDesc = validateDescReference(originGenericClassDecl)
                        kspRequire(originDesc is InvokableDesc) { "524" }
                        kspRequire(type.getClassDecl()?.isInstance(builtins[DescBuiltin.Call]) == true) { "525" }
                        HookOriginDescCallParameter(originDesc)
                    }
                }

                hasCancelAnnotation -> {
                    kspRequire(at != At.Body) { "531" }
                    kspRequire(hookDesc is MethodDesc) { "532" }
                    val cancelDesc = validateDescReference(cancelGenericClassDecl)
                    val wrapperClassDecl = type.getClassDecl()
                    kspRequire(wrapperClassDecl?.isInstance(builtins[DescBuiltin.Cancel]) == true) { "535" }
                    kspRequire(cancelDesc == hookDesc) { "536" }
                    HookCancelParameter(hookDesc)
                }

                hasOrdinalAnnotation -> {
                    kspRequire(type.isSame(Int::class)) { "541" }
                    kspRequire(function.hasOrdinals()) { "542" }
                    HookOrdinalParameter
                }

                hasParamAnnotation -> {
                    kspRequire(at != At.Body) { "547" }
                    kspRequireNotNull(paramName) { "548" }
                    val descParameterIndex =
                        hookDesc.parameters.indexOfFirst { it.name == paramName }.takeIf { it != -1 }
                    kspRequireNotNull(descParameterIndex) { "551" }
                    val descParameter = hookDesc.parameters[descParameterIndex]
                    kspRequire(descParameter.type == type) { "553" }
                    HookParamParameter(paramName, descParameterIndex)
                }

                hasLocalAnnotation -> {
                    kspRequire(at != At.Body) { "558" }
                    kspRequireNotNull(type) { "559" }
                    HookLocalParameter(name, type, validateLocal(localOrdinal, localName, name))
                }

                else -> kspError { "563" }
            }
        }

    private fun skip(): Nothing = throw SkipSignal()

    private inline fun SymbolSource.kspInfo(crossinline message: () -> String) {
        logger.info(message(), symbol)
    }

    private inline fun SymbolSource.kspWarn(crossinline message: () -> String) {
        logger.warn(message(), symbol)
    }

    private inline fun SymbolSource.kspError(crossinline message: () -> String): Nothing {
        logger.error(message(), symbol)
        skip()
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun SymbolSource.kspRequire(condition: Boolean, crossinline message: () -> String) {
        contract {
            returns() implies condition
        }
        if (!condition) {
            kspError(message = message)
        }
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun <T> SymbolSource.kspRequireNotNull(value: T?, crossinline message: () -> String): T {
        contract {
            returns() implies (value != null)
        }
        return value ?: kspError(message = message)
    }

    private fun <R> runOrNullOnSkip(block: () -> R): R? =
        try {
            block()
        } catch (_: SkipSignal) {
            null
        }

    private class SkipSignal : Exception()
}
