package io.github.recrafter.lapis.layers.validator

import com.google.devtools.ksp.isAbstract
import io.github.recrafter.lapis.Options
import io.github.recrafter.lapis.annotations.AtField
import io.github.recrafter.lapis.annotations.AtLiteral
import io.github.recrafter.lapis.annotations.Hook
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.ksp.*
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.layers.JavaMemberKind
import io.github.recrafter.lapis.layers.generator.builtins.Builtin
import io.github.recrafter.lapis.layers.generator.builtins.Builtins
import io.github.recrafter.lapis.layers.generator.builtins.DescBuiltin
import io.github.recrafter.lapis.layers.parser.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.reflect.KClass

class FrontendValidator(
    private val logger: KSPLogger,
    private val options: Options,
    private val builtins: Builtins,
) {
    private val descByQualifiedName: MutableMap<String, Desc> = mutableMapOf()

    fun validate(parserResult: ParserResult): ValidatorResult =
        ValidatorResult(
            schemas = parserResult.schemas.flatMap { rootSchema ->
                runCatchingOrNull { validateSchema(rootSchema) } ?: emptyList()
            },
            patches = parserResult.patches.mapNotNull {
                runCatchingOrNull { validatePatch(it) }
            },
        )

    private fun validateSchema(parsedSchema: ParsedSchema): List<Schema> = with(parsedSchema) {
        kspRequireNotNull(classDecl) { "10.0" }
        kspRequireNotNull(targetClassDecl) { "10.1" }
        kspRequire(classDecl.typeParameters.isEmpty()) { "10.2" }
        access?.let {
            kspRequire(targetClassDecl.qualifiedName?.asString() == it) { "10.3" }
        }
        val schema = Schema(
            source = source,
            classDecl = classDecl,
            targetClassDecl = targetClassDecl,
            hasAccess = hasAccess,
            isMarkedAsFinal = parsedSchema.isMarkedAsFinal,
            descriptors = parsedSchema.descriptors.mapNotNull { parsedDesc ->
                runCatchingOrNull { validateDesc(targetClassDecl, parsedDesc) }?.also { desc ->
                    val qualifiedName = parsedDesc.classDecl?.qualifiedName?.asString() ?: return@also
                    descByQualifiedName[qualifiedName] = desc
                }
            },
        )
        return listOf(schema) + nestedSchemas.flatMap {
            runCatchingOrNull { validateSchema(it) } ?: emptyList()
        }
    }

    private fun validateDesc(targetClassDecl: KSPClassDecl, desc: ParsedDesc): Desc = with(desc) {
        kspRequireNotNull(name) { "20.0" }
        kspRequireNotNull(classDecl) { "20.1" }
        kspRequire(classDecl.typeParameters.isEmpty()) { "20.2" }
        kspRequire(superClassDecl?.isInstance(builtins[Builtin.Desc]) == true) { "20.3" }
        kspRequire(isFunctionType) { "20.4" }
        kspRequireNotNull(callableReference) { "20.5" }
        val memberKind = kspRequireNotNull(memberKinds.singleOrNull()) { "20.6" }

        val receiverType = if (hasStaticAnnotation || memberKind == JavaMemberKind.CONSTRUCTOR) {
            targetClassDecl.asStarProjectedType()
        } else {
            kspRequireNotNull(receiverType) { "20.7" }
            kspRequire(!receiverType.isFunctionType) { "20.8" }
            receiverType
        }
        val parameters = parameters.map { parameter ->
            kspRequire(!parameter.type.isFunctionType) { "20.9" }
            FunctionTypeParameter(
                type = parameter.type,
                name = parameter.name,
            )
        }
        kspRequire(returnType == null || !returnType.isFunctionType) { "20.10" }
        if (hasAccessAnnotation) {
            kspRequire(options.accessWidenerConfigName != null || options.accessTransformerConfigName != null) {
                "20.11"
            }
        }

        when (memberKind) {
            JavaMemberKind.CONSTRUCTOR -> {
                kspRequire(!hasStaticAnnotation) { "20.12" }
                ConstructorDesc(
                    name = name,
                    classDecl = classDecl,
                    returnType = kspRequireNotNull(returnType) { "20.13" },
                    parameters = parameters,
                    makePublic = hasAccessAnnotation,
                    removeFinal = isMarkedAsFinal,
                )
            }

            JavaMemberKind.METHOD -> {
                if (hasStaticAnnotation) {
                    kspRequire(!hasReceiver) { "20.14" }
                } else {
                    kspRequire(hasReceiver) { "20.15" }
                    kspRequire(functionTypeReceiverName == callableReference.left) { "20.16" }
                }
                MethodDesc(
                    name = name,
                    classDecl = classDecl,
                    receiverType = receiverType,
                    returnType = returnType,
                    targetName = kspRequireNotNull(callableReference.right) { "20.17" },
                    parameters = parameters,
                    isStatic = hasStaticAnnotation,
                    makePublic = hasAccessAnnotation,
                    removeFinal = isMarkedAsFinal,
                )
            }

            JavaMemberKind.FIELD -> {
                if (hasStaticAnnotation) {
                    kspRequire(!hasReceiver) { "20.18" }
                } else {
                    kspRequire(hasReceiver) { "20.19" }
                    kspRequire(functionTypeReceiverName == callableReference.left) { "20.20" }
                }
                FieldDesc(
                    name = name,
                    classDecl = classDecl,
                    receiverType = receiverType,
                    targetName = kspRequireNotNull(callableReference.right) { "20.21" },
                    fieldType = kspRequireNotNull(returnType) { "20.22" },
                    isStatic = hasStaticAnnotation,
                    makePublic = hasAccessAnnotation,
                    removeFinal = isMarkedAsFinal,
                )
            }
        }
    }

    private fun validatePatch(patch: ParsedPatch): Patch = with(patch) {
        kspRequireNotNull(name) { "30.0" }
        kspRequireNotNull(schemaClassDecl) { "30.1" }
        kspRequireNotNull(superGenericClassDecl) { "30.15" }
        kspRequireNotNull(side) { "30.2" }
        kspRequire(classDecl?.run { isAbstract() && !isInner && isClass } == true) { "30.3" }
        kspRequire(classDecl.typeParameters.isEmpty()) { "30.4" }
        kspRequire(superClassDecl?.isInstance(builtins[Builtin.Patch]) == true) { "30.5" }
//        kspRequire(superGenericClassType.isSame()) { "30.6" }
        return Patch(
            source = source,

            name = name,
            classDecl = classDecl,
            targetClassDecl = superGenericClassDecl,
            side = side,
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
                runCatchingOrNull { validateSharedFunction(it) }
            },
            hooks = functions.filter { it.hasHookAnnotation }.mapNotNull {
                runCatchingOrNull { validateHook(it) }
            },
        )
    }

    private fun validateSharedFunction(function: ParsedPatchFunction): SharedFunction = with(function) {
        SharedFunction(
            name = name,
            parameters = function.parameters.map {
                FunctionParameter(
                    name = kspRequireNotNull(it.name) { "40.0" },
                    type = kspRequireNotNull(it.type) { "40.1" },
                )
            },
            returnType = function.returnType,
        )
    }

    private fun validateHook(function: ParsedPatchFunction): HookModel = with(function) {
        kspRequireNotNull(hookAt) { "50.0" }
        kspRequire(!function.hasTypeParameters) { "50.1" }
        kspRequireNotNull(hookDescClassDecl) { "50.15" }
        val desc = findDesc(hookDescClassDecl)
        kspRequire(desc is InvokableDesc) { "50.2" }
        val parameters = parameters.map {
            validateHookParameter(hookAt, desc, it)
        }
        when (hookAt) {
            Hook.At.Body -> {
                kspRequire(desc is MethodDesc) { "50.4" }
//                kspRequire(returnType?.isSame(desc.returnType) == true) { "50.5" }
                BodyHook(
                    name = name,
                    targetDesc = desc,
                    returnType = returnType,
                    parameters = parameters,
                )
            }

            Hook.At.Literal -> {
                val (argName, argType, argValue) = kspRequireNotNull(atLiteralArguments.singleOrNull()) { "50.8" }
                kspRequireNotNull(argType) { "50.89" }
                kspRequireNotNull(argValue) { "50.92" }
                val literal = when (kspRequireNotNull(argName) { "50.81" }) {
                    AtLiteral::zero.name -> ZeroLiteral(atLiteralZeroConditions)
                    AtLiteral::int.name -> IntLiteral(kspRequireNotNull(atLiteralInt) { "50.82" })
                    AtLiteral::float.name -> FloatLiteral(kspRequireNotNull(atLiteralFloat) { "50.83" })
                    AtLiteral::long.name -> LongLiteral(kspRequireNotNull(atLiteralLong) { "50.84" })
                    AtLiteral::double.name -> DoubleLiteral(kspRequireNotNull(atLiteralDouble) { "50.85" })
                    AtLiteral::string.name -> StringLiteral(kspRequireNotNull(atLiteralString) { "50.86" })
                    AtLiteral::`class`.name -> ClassLiteral(kspRequireNotNull(argType.toClassDeclOrNull()) { "50.87" })
                    AtLiteral::`null`.name -> NullLiteral
                    else -> kspError { "50.88" }
                }
                val kClass = when (literal) {
                    is ZeroLiteral, is IntLiteral -> Int::class
                    is FloatLiteral -> Float::class
                    is LongLiteral -> Long::class
                    is DoubleLiteral -> Double::class
                    is StringLiteral -> String::class
                    is ClassLiteral -> KClass::class
                    else -> null
                }
                if (kClass != null) {
                    if (literal !is StringLiteral && literal !is ClassLiteral) {
                        kspRequire(returnType?.isMarkedNullable == false) { "50.91" }
                    }
                    kspRequire(returnType?.isSame(kClass) == true) { "50.9" }
                }
                LiteralHook(
                    name = name,
                    desc = desc,
                    parameters = parameters,
                    type = argType,
                    literal = literal,
                    ordinals = atLiteralOrdinals,
                )
            }

            Hook.At.Field -> {
                kspRequire(hasAtFieldAnnotation) { "50.32" }
                kspRequireNotNull(atFieldDescClassDecl) { "50.43" }
                kspRequireNotNull(atFieldOp) { "50.43" }
                val targetDesc = findDesc(atFieldDescClassDecl)
                kspRequire(targetDesc is FieldDesc) { "50.13" }
                when (atFieldOp) {
                    AtField.Op.Get -> {
                        kspRequire(returnType?.isSame(targetDesc.fieldType) == true) { "50.14" }
                        FieldGetHook(
                            name = name,
                            desc = desc,
                            type = targetDesc.fieldType,
                            ordinals = atFieldOrdinals,
                            targetDesc = targetDesc,
                            parameters = parameters,
                        )
                    }

                    AtField.Op.Write -> {
                        kspRequire(returnType == null) { "50.16" }
                        FieldWriteHook(
                            name = name,
                            desc = desc,
                            type = targetDesc.fieldType,
                            ordinals = atFieldOrdinals,
                            targetDesc = targetDesc,
                            parameters = parameters,
                        )
                    }
                }
            }

            Hook.At.Call -> {
                kspRequire(hasAtCallAnnotation) { "50.3" }
                kspRequireNotNull(atCallDescClassDecl) { "50.3" }
                val targetDesc = findDesc(atCallDescClassDecl)
                kspRequire(targetDesc is MethodDesc) { "50.6" }
//                kspRequire(returnType?.isSame(targetDesc.returnType) == true) { "50.7" }
                CallHook(
                    name = name,
                    desc = desc,
                    returnType = returnType,
                    targetDesc = targetDesc,
                    ordinals = atCallOrdinals,
                    parameters = parameters,
                )
            }

            else -> TODO("[LAPIS] The @At for ${hookAt.name} is not implemented.")
        }
    }

    private fun validateHookParameter(
        at: Hook.At,
        desc: InvokableDesc,
        parameter: ParsedPatchFunctionParameter,
    ): HookParameter = with(parameter) {
        kspRequireNotNull(name) { "60.0" }
        kspRequireNotNull(type) { "60.1" }
        when {
            hasOriginAnnotation -> when (at) {
                Hook.At.Body, Hook.At.Field, Hook.At.Array, Hook.At.Call -> {
                    kspRequireNotNull(originGenericClassDecl) { "60.25" }
                    val originDesc = findDesc(originGenericClassDecl)
                    val wrapperClassDecl = type.toClassDeclOrNull()
                    when {
                        wrapperClassDecl?.isInstance(builtins[DescBuiltin.Call]) == true -> {
                            kspRequire(originDesc is InvokableDesc) { "60.26" }
                            HookOriginCallParameter(originDesc)
                        }

                        wrapperClassDecl?.isInstance(builtins[DescBuiltin.FieldGet]) == true -> {
                            TODO()
                        }

                        wrapperClassDecl?.isInstance(builtins[DescBuiltin.FieldWrite]) == true -> {
                            TODO()
                        }

                        else -> kspError { "60.6" }
                    }
                }

                Hook.At.Literal -> HookOriginValueParameter()

                else -> {
                    TODO()
                }
            }

            hasCancelAnnotation -> {
                kspRequire(at != Hook.At.Body) { "60.7" }
                kspRequire(cancelGenericClassDecl?.isInstance(builtins[DescBuiltin.Cancel]) == true) { "60.8" }
                val cancelDesc = findDesc(cancelGenericClassDecl)
                kspRequire(cancelDesc == desc) { "60.10" }
                HookCancelParameter(desc)
            }

            hasParamAnnotation -> {
                kspRequire(at != Hook.At.Body) { "60.7" }
                kspRequireNotNull(paramName) { "60.19" }
                HookParamParameter(paramName)
            }

            hasLocalAnnotation -> {
                kspRequire(at != Hook.At.Body) { "60.18" }
                kspRequireNotNull(type) { "60.19" }
                kspRequireNotNull(localOrdinal) { "60.20" }
                kspRequire(localOrdinal >= 0) { "60.21" }
                HookLocalParameter(name, type, localOrdinal)
            }

            else -> kspError { "60.22" }
        }
    }

    private fun skip(): Nothing = throw SkipSignal()

    private inline fun KSPSource.kspLogging(crossinline message: () -> String) {
        logger.logging(message(), source)
    }

    private inline fun KSPSource.kspInfo(crossinline message: () -> String) {
        logger.info(message(), source)
    }

    private inline fun KSPSource.kspWarn(crossinline message: () -> String) {
        logger.warn(message(), source)
    }

    private inline fun KSPSource.kspError(crossinline message: () -> String): Nothing {
        logger.error(message(), source)
        skip()
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun KSPSource.kspRequire(condition: Boolean, crossinline message: () -> String) {
        contract {
            returns() implies condition
        }
        if (!condition) {
            kspError(message = message)
        }
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun <T> KSPSource.kspRequireNotNull(value: T?, crossinline message: () -> String): T {
        contract {
            returns() implies (value != null)
        }
        return value ?: kspError(message = message)
    }

    @Suppress("UnusedReceiverParameter")
    private fun <R> FrontendValidator.runCatchingOrNull(block: () -> R): R? =
        try {
            block()
        } catch (_: SkipSignal) {
            null
        }

    private fun findDesc(classDecl: KSPClassDecl): Desc {
        val qualifiedName = classDecl.qualifiedName?.asString()
        return descByQualifiedName[qualifiedName] ?: lapisError("Desc for ${qualifiedName?.quoted()} not found")
    }

    private class SkipSignal : Exception()
}
