package io.github.recrafter.lapis.layers.validator

import com.google.devtools.ksp.isAbstract
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.recrafter.lapis.LapisLogger
import io.github.recrafter.lapis.Options
import io.github.recrafter.lapis.annotations.At
import io.github.recrafter.lapis.annotations.AtLiteral
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.kp.KPAny
import io.github.recrafter.lapis.extensions.kp.KPBoolean
import io.github.recrafter.lapis.extensions.ks.*
import io.github.recrafter.lapis.layers.generator.builders.Remapper
import io.github.recrafter.lapis.layers.generator.builtins.Builtin
import io.github.recrafter.lapis.layers.generator.builtins.Builtins
import io.github.recrafter.lapis.layers.generator.builtins.DescBuiltin
import io.github.recrafter.lapis.layers.lowering.asIr
import io.github.recrafter.lapis.layers.lowering.asIrTypeName
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
        kspRequire(classDecl?.isValid == true) { "45" }
        kspRequire(targetClassDecl?.isValid == true) { "46" }
        kspRequireNotNull(targetBinaryName) { "47" }
        kspRequire(classDecl.typeParameters.isEmpty()) { "48" }
        val qualifiedName = kspRequireNotNull(classDecl.qualifiedName?.asString()) { "49" }

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
        kspRequireNotNull(name) { "88" }
        kspRequire(classDecl.typeParameters.isEmpty()) { "89" }
        kspRequire(superClassDecl?.isValid == true) { "90" }
        kspRequireNotNull(callable) { "91" }
        if (hasAccessAnnotation) {
            kspRequire(options.accessWidenerConfigName != null || options.accessTransformerConfigName != null) {
                "94"
            }
        }
        val receiverType = schemaTargetClassDecl.asStarProjectedType()
        if (superClassDecl.isInstance(builtins[Builtin.Field])) {
            kspRequire(generic is ParsedTypeDescGeneric) { "99" }
            kspRequireNotNull(generic.type) { "100" }
            if (callable !is PrivateCallable) {
                kspRequire(callable is ParsedFieldDescCallable) { "102" }
            }
            return FieldDesc(
                name = name,
                classDecl = classDecl,
                receiverType = receiverType,
                targetName = kspRequireNotNull(callable.name) { "108" },
                fieldType = generic.type,
                arrayComponentType = generic.arrayComponentType,
                isStatic = hasStaticAnnotation,
                makePublic = hasAccessAnnotation,
                removeFinal = unfinal,
            )
        }
        kspRequire(generic is ParsedFunctionTypeDescGeneric) { "116" }
        val parameters = generic.parameters.map { parameter ->
            kspRequire(!parameter.type.isFunctionType) { "118" }
            FunctionTypeParameter(
                type = parameter.type,
                name = parameter.name,
            )
        }
        return when {
            superClassDecl.isInstance(builtins[Builtin.Method]) -> {
                if (callable !is PrivateCallable) {
                    kspRequire(callable is ParsedMethodDescCallable) { "127" }
                }
                if (!hasStaticAnnotation) {
                    kspRequireNotNull(generic.receiverType) { "130" }
                }
                MethodDesc(
                    name = name,
                    classDecl = classDecl,
                    receiverType = receiverType,
                    returnType = generic.returnType,
                    targetName = kspRequireNotNull(callable.name) { "137" },
                    parameters = parameters,
                    isStatic = hasStaticAnnotation,
                    makePublic = hasAccessAnnotation,
                    removeFinal = unfinal,
                )
            }

            superClassDecl.isInstance(builtins[Builtin.Constructor]) -> {
                if (callable !is PrivateCallable) {
                    kspRequire(callable is ParsedConstructorDescCallable) { "147" }
                }
                kspRequire(!unfinal) { "149" }
                ConstructorDesc(
                    name = name,
                    classDecl = classDecl,
                    returnType = kspRequireNotNull(generic.returnType) { "153" },
                    parameters = parameters,
                    makePublic = hasAccessAnnotation,
                )
            }

            else -> kspError { "159" }
        }
    }

    private fun validatePatch(patch: ParsedPatch): Patch = with(patch) {
        kspRequireNotNull(name) { "164" }
        kspRequire(schemaClassDecl?.isValid == true) { "165" }
        kspRequire(superGenericClassDecl?.isValid == true) { "166" }
        kspRequireNotNull(side) { "167" }
        kspRequire(classDecl?.run { isAbstract() && !isInner && isClass } == true) { "168" }
        kspRequire(classDecl.typeParameters.isEmpty()) { "169" }
        kspRequire(superClassDecl?.isInstance(builtins[Builtin.Patch]) == true) { "170" }
        val schema = validSchemas[schemaClassDecl.qualifiedName?.asString()]
        kspRequire(superGenericClassDecl.isSame(schema?.targetClassDecl)) { "172" }
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
        val hookDesc = validateDescReference(hookDescClassDecl)
        kspRequire(hookDesc is InvokableDesc) { "217" }
        if (hookDesc.isStatic) {
            kspRequire(fromCompanionObject) { "219" }
        } else {
            kspRequire(!fromCompanionObject) { "221" }
        }
        val ordinals: Remapper<List<Int>> = { validateOrdinals(it) }
        val parameters: () -> List<HookParameter> = {
            function.parameters.mapNotNull { parameter ->
                runOrNullOnSkip { validateHookParameter(parameter, function, hookAt, hookDesc) }
            }
        }
        when (hookAt) {
            At.Head -> {
                kspRequire(returnType == null) { "231" }
                when (hookDesc) {
                    is ConstructorDesc -> {
                        kspRequire(hasAtConstructorHeadAnnotation) { "234" }
                        ConstructorHeadHook(
                            name = name,
                            desc = hookDesc,
                            phase = kspRequireNotNull(atConstructorHeadPhase) { "238" },
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
                kspRequire(hookDesc is MethodDesc) { "254" }
                kspRequire(returnType == hookDesc.returnType) { "255" }
                BodyHook(
                    name = name,
                    targetDesc = hookDesc,
                    returnType = returnType,
                    parameters = parameters(),
                )
            }

            At.Tail -> {
                kspRequire(returnType == null) { "265" }
                TailHook(
                    name = name,
                    desc = hookDesc,
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
                    desc = hookDesc,
                    type = atLocalType,
                    ordinals = ordinals(atLocalOpOrdinals),
                    local = validateLocal(atLocalOrdinal, atLocalName),
                    isSet = atLocalOp == Op.Set,
                    parameters = parameters(),
                )
            }

            At.Instanceof -> {
                kspRequire(hasAtInstanceofAnnotation) { "290" }
                kspRequire(atInstanceofTypeClassDecl?.isValid == true) { "291" }
                kspRequire(returnType?.toClassName()?.asIrTypeName() == KPBoolean.asIrTypeName()) { "292" }
                InstanceofHook(
                    name = name,
                    desc = hookDesc,
                    classDecl = atInstanceofTypeClassDecl,
                    returnType = returnType,
                    ordinals = ordinals(atInstanceofOrdinals),
                    parameters = parameters(),
                )
            }

            At.Return -> {
                kspRequire(hasAtReturnAnnotation) { "304" }
                kspRequire(returnType == hookDesc.returnType) { "305" }
                ReturnHook(
                    name = name,
                    desc = hookDesc,
                    type = returnType,
                    ordinals = ordinals(atReturnOrdinals),
                    parameters = parameters(),
                )
            }

            At.Literal -> {
                kspRequire(hasAtLiteralAnnotation) { "316" }
                val (_, argType, _) = kspRequireNotNull(atLiteralArguments.singleOrNull()) { "317" }
                kspRequireNotNull(argType) { "318" }
                val literal = validateLiteral(function)
                val kClass = literal.kClass
                if (kClass != null) {
                    if (literal !is StringLiteral && literal !is ClassLiteral) {
                        kspRequire(returnType?.isMarkedNullable == false) { "323" }
                    }
                    kspRequire(returnType?.isSame(kClass) == true) { "325" }
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
                kspRequire(hasAtFieldAnnotation) { "338" }
                kspRequireNotNull(atFieldOp) { "339" }
                val targetDesc = validateDescReference(atFieldDescClassDecl)
                kspRequire(targetDesc is FieldDesc) { "341" }
                when (atFieldOp) {
                    Op.Get -> {
                        kspRequire(returnType?.makeNotNullable() == targetDesc.fieldType) { "344" }
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
                        kspRequire(returnType == null) { "356" }
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
                kspRequire(hasAtArrayAnnotation) { "370" }
                kspRequireNotNull(atArrayOp) { "371" }
                val targetDesc = validateDescReference(atArrayDescClassDecl)
                kspRequire(targetDesc is FieldDesc) { "373" }
                kspRequireNotNull(targetDesc.arrayComponentType) { "374" }
                when (atArrayOp) {
                    Op.Get -> kspRequire(returnType == targetDesc.arrayComponentType) { "376" }
                    Op.Set -> kspRequire(returnType == null) { "377" }
                }
                ArrayHook(
                    name = name,
                    desc = hookDesc,
                    op = atArrayOp,
                    type = targetDesc.fieldType,
                    componentType = targetDesc.arrayComponentType,
                    targetDesc = targetDesc,
                    ordinals = ordinals(atArrayOrdinals),
                    parameters = parameters(),
                )
            }

            At.Call -> {
                kspRequire(hasAtCallAnnotation) { "392" }
                val targetDesc = validateDescReference(atCallDescClassDecl)
                kspRequire(targetDesc is MethodDesc) { "394" }
                kspRequire(returnType?.makeNotNullable() == targetDesc.returnType) { "395" }
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
        val (argName, argType, argValue) = kspRequireNotNull(atLiteralArguments.singleOrNull()) { "409" }
        kspRequireNotNull(argType) { "410" }
        kspRequireNotNull(argValue) { "411" }
        kspRequireNotNull(argName) { "412" }
        when (argName) {
            AtLiteral::zero.name -> ZeroLiteral(atLiteralZeroConditions)
            AtLiteral::int.name -> {
                kspRequireNotNull(atLiteralInt) { "416" }
                kspRequire(atLiteralInt != 0) { "417" }
                IntLiteral(atLiteralInt)
            }

            AtLiteral::float.name -> FloatLiteral(kspRequireNotNull(atLiteralFloat) { "421" })
            AtLiteral::long.name -> LongLiteral(kspRequireNotNull(atLiteralLong) { "422" })
            AtLiteral::double.name -> DoubleLiteral(kspRequireNotNull(atLiteralDouble) { "423" })
            AtLiteral::string.name -> StringLiteral(kspRequireNotNull(atLiteralString) { "424" })
            AtLiteral::`class`.name -> ClassLiteral(kspRequireNotNull(argType.getClassDecl()) { "425" })
            AtLiteral::`null`.name -> NullLiteral
            else -> kspError { "427" }
        }
    }

    private fun SymbolSource.validateLocal(
        ordinal: Int?,
        explicitName: String?,
        implicitName: String? = explicitName
    ): DomainLocal =
        when {
            ordinal != null && explicitName == null -> {
                kspRequire(ordinal >= 0) { "438" }
                PositionalLocal(ordinal)
            }

            explicitName != null && ordinal == null -> NamedLocal(explicitName)
            ordinal == null && implicitName != null -> NamedLocal(implicitName)

            else -> kspError { "445" }
        }

    private fun SymbolSource.validateOrdinals(ordinals: List<Int>): List<Int> {
        ordinals.forEach {
            kspRequire(it >= 0) { "450" }
        }
        return ordinals.toSortedSet().toList()
    }

    private fun SymbolSource.validateDescReference(classDecl: KSClassDecl?): Desc {
        kspRequire(classDecl?.isValid == true) { "456" }
        val qualifiedName = classDecl.qualifiedName?.asString()
        if (invalidDescriptors.contains(qualifiedName)) {
            kspError { "459" }
        }
        return validDescriptors[qualifiedName] ?: lapisError("461")
    }

    private fun validateHookParameter(
        parameter: ParsedPatchFunctionParameter,
        function: ParsedPatchFunction,
        at: At,
        hookDesc: InvokableDesc,
    ): HookParameter =
        with(parameter) {
            kspRequireNotNull(name) { "471" }
            kspRequireNotNull(type) { "472" }
            kspRequire(!hasDefaultArgument) { "473" }
            when {
                hasOriginAnnotation -> when (at) {
                    At.Head, At.Tail -> kspError { "476" }

                    At.Body -> {
                        val originDesc = validateDescReference(originGenericClassDecl)
                        kspRequire(originDesc is InvokableDesc) { "480" }
                        kspRequire(type.getClassDecl()?.isInstance(builtins[DescBuiltin.Body]) == true) { "481" }
                        HookOriginDescBodyParameter(originDesc)
                    }

                    At.Local -> {
                        kspRequire(type == function.returnType) { "486" }
                        HookOriginValueParameter()
                    }

                    At.Instanceof -> {
                        kspRequire(type.getClassDecl()?.isInstance(builtins[Builtin.Instanceof]) == true) { "491" }
                        HookOriginInstanceofParameter()
                    }

                    At.Return -> {
                        kspRequireNotNull(hookDesc.returnType) { "496" }
                        kspRequire(type == hookDesc.returnType) { "497" }
                        HookOriginValueParameter()
                    }

                    At.Literal -> {
                        val kClass = validateLiteral(function).kClass
                        kspRequireNotNull(kClass) { "503" }
                        kspRequire(type.isSame(kClass)) { "504" }
                        HookOriginValueParameter()
                    }

                    At.Field -> {
                        kspRequireNotNull(function.atFieldOp) { "509" }
                        val originDesc = validateDescReference(originGenericClassDecl)
                        kspRequire(originDesc is FieldDesc) { "511" }
                        when (function.atFieldOp) {
                            Op.Get -> {
                                kspRequire(type.getClassDecl()?.isInstance(builtins[DescBuiltin.FieldGet]) == true) {
                                    "515"
                                }
                                HookOriginDescFieldGetParameter(originDesc)
                            }

                            Op.Set -> {
                                kspRequire(type.getClassDecl()?.isInstance(builtins[DescBuiltin.FieldSet]) == true) {
                                    "522"
                                }
                                HookOriginDescFieldSetParameter(originDesc)
                            }
                        }
                    }

                    At.Array -> {
                        kspRequireNotNull(function.atArrayOp) { "530" }
                        val originDesc = validateDescReference(originGenericClassDecl)
                        kspRequire(originDesc is FieldDesc) { "532" }
                        kspRequireNotNull(originDesc.arrayComponentType) { "533" }
                        when (function.atArrayOp) {
                            Op.Get -> {
                                kspRequire(type.getClassDecl()?.isInstance(builtins[DescBuiltin.ArrayGet]) == true) {
                                    "537"
                                }
                                HookOriginDescArrayGetParameter(originDesc, originDesc.arrayComponentType)
                            }

                            Op.Set -> {
                                kspRequire(type.getClassDecl()?.isInstance(builtins[DescBuiltin.ArraySet]) == true) {
                                    "544"
                                }
                                HookOriginDescArraySetParameter(originDesc, originDesc.arrayComponentType)
                            }
                        }
                    }

                    At.Call -> {
                        val originDesc = validateDescReference(originGenericClassDecl)
                        kspRequire(originDesc is InvokableDesc) { "553" }
                        kspRequire(type.getClassDecl()?.isInstance(builtins[DescBuiltin.Call]) == true) { "554" }
                        HookOriginDescCallParameter(originDesc)
                    }
                }

                hasCancelAnnotation -> {
                    kspRequire(at != At.Body) { "560" }
                    kspRequire(hookDesc is MethodDesc) { "561" }
                    val cancelDesc = validateDescReference(cancelGenericClassDecl)
                    val wrapperClassDecl = type.getClassDecl()
                    kspRequire(wrapperClassDecl?.isInstance(builtins[DescBuiltin.Cancel]) == true) { "564" }
                    kspRequire(cancelDesc == hookDesc) { "565" }
                    HookCancelParameter(hookDesc)
                }

                hasOrdinalAnnotation -> {
                    kspRequire(type.isSame(Int::class)) { "570" }
                    kspRequire(function.hasOrdinals()) { "571" }
                    HookOrdinalParameter
                }

                hasParamAnnotation -> {
                    kspRequire(at != At.Body) { "576" }
                    kspRequireNotNull(paramName) { "577" }
                    val descParameterIndex =
                        hookDesc.parameters.indexOfFirst { it.name == paramName }.takeIf { it != -1 }
                    kspRequireNotNull(descParameterIndex) { "580" }
                    val descParameter = hookDesc.parameters[descParameterIndex]
                    kspRequire(descParameter.type == type) { "582" }
                    HookParamParameter(paramName, descParameterIndex)
                }

                hasLocalAnnotation -> {
                    kspRequire(at != At.Body) { "587" }
                    kspRequireNotNull(type) { "588" }
                    HookLocalParameter(name, type, validateLocal(localOrdinal, localName, name))
                }

                else -> kspError { "592" }
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
