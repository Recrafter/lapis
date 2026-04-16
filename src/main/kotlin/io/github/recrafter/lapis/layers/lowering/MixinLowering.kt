package io.github.recrafter.lapis.layers.lowering

import com.squareup.kotlinpoet.asClassName
import io.github.recrafter.lapis.LapisLogger
import io.github.recrafter.lapis.Options
import io.github.recrafter.lapis.annotations.ConstructorHeadPhase
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.layers.generator.builtins.Builtins
import io.github.recrafter.lapis.layers.generator.builtins.DescBuiltin
import io.github.recrafter.lapis.layers.lowering.models.*
import io.github.recrafter.lapis.layers.lowering.types.*
import io.github.recrafter.lapis.layers.validator.*
import org.spongepowered.asm.mixin.injection.Constant
import kotlin.reflect.KClass

class MixinLowering(
    private val options: Options,
    private val builtins: Builtins,
    @Suppress("unused") private val logger: LapisLogger,
) {
    private val mixins: MutableList<IrMixin> = mutableListOf()

    fun lower(validatorResult: ValidatorResult): IrResult {
        mixins += validatorResult.patches.map { lowerMixin(it) }
        return IrResult(
            schemas = validatorResult.schemas.map { schema ->
                IrSchema(
                    containingFile = schema.containingFile,

                    makePublic = schema.makePublic,
                    removeFinal = schema.removeFinal,
                    className = schema.className,
                    targetClassName = schema.targetClassName,
                    descriptors = schema.descriptors.map { desc -> lowerDesc(desc) },
                )
            },
            mixins = mixins,
        )
    }

    private fun lowerDesc(desc: Desc): IrDesc =
        when (desc) {
            is InvokableDesc -> {
                if (desc is ConstructorDesc) {
                    IrConstructorDesc(
                        makePublic = desc.makePublic,
                        callWrapper = findOriginDescWrapper(desc.className),
                        cancelWrapper = findCancelDescWrapper(desc.className),
                        parameters = desc.parameters.map { it.asIr() },
                        returnTypeName = desc.className,
                    )
                } else {
                    IrMethodDesc(
                        makePublic = desc.makePublic,
                        removeFinal = desc.removeFinal,
                        name = desc.name,
                        targetName = desc.targetName,
                        bodyWrapper = findOriginDescWrapper(desc.className),
                        callWrapper = findOriginDescWrapper(desc.className),
                        cancelWrapper = findCancelDescWrapper(desc.className),
                        parameters = desc.parameters.map { it.asIr() },
                        returnTypeName = desc.returnTypeName,
                    )
                }
            }

            is FieldDesc -> {
                IrFieldDesc(
                    makePublic = desc.makePublic,
                    removeFinal = desc.removeFinal,
                    name = desc.name,
                    targetName = desc.targetName,
                    fieldGetWrapper = findOriginDescWrapper(desc.className),
                    fieldSetWrapper = findOriginDescWrapper(desc.className),
                    arrayGetWrapper = findOriginDescWrapper(desc.className),
                    arraySetWrapper = findOriginDescWrapper(desc.className),
                    typeName = desc.fieldTypeName,
                )
            }
        }

    private fun lowerMixin(patch: Patch): IrMixin =
        IrMixin(
            containingFile = patch.containingFile,

            className = IrClassName.of(
                options.mixinPackageName,
                "Mixin".withQualifiedNamePrefix(patch.className)
            ),
            patchClassName = patch.className,
            patchImplClassName = IrClassName.of(
                options.generatedPackageName,
                "Impl".withQualifiedNamePrefix(patch.className)
            ),
            targetClassName = patch.targetClassName,

            side = patch.side,
            extension = lowerExtension(patch, patch.className),
            injections = patch.hooks.flatMap { lowerInjections(it) },
        )

    private fun lowerExtension(patch: Patch, patchClassName: IrClassName): IrExtension? {
        if (patch.sharedProperties.isEmpty() && patch.sharedFunctions.isEmpty()) {
            return null
        }
        return IrExtension(
            className = IrClassName.of(
                options.generatedPackageName,
                "Extension".withQualifiedNamePrefix(patchClassName)
            ),
            kinds = buildList {
                patch.sharedProperties.forEach { property ->
                    add(
                        IrPropertyGetterExtension(
                            name = property.name,
                            typeName = property.typeName,
                        )
                    )
                    if (property.isMutable) {
                        add(
                            IrPropertySetterExtension(
                                name = property.name,
                                typeName = property.typeName,
                            )
                        )
                    }
                }
                addAll(patch.sharedFunctions.map { function ->
                    IrFunctionCallExtension(
                        name = function.name,
                        parameters = function.parameters.asIr(),
                        returnTypeName = function.returnTypeName,
                    )
                })
            },
        )
    }

    private fun lowerInjections(hook: DomainHook): List<IrInjection> {
        val parameters = buildList {
            when {
                hook.isInjectBased -> {
                    addAll(hook.desc.parameters.mapIndexed { index, parameter ->
                        IrInjectionArgumentParameter(parameter.name, index, parameter.typeName)
                    })
                    add(
                        IrInjectionCallbackParameter(
                            if (hook is ConstructorHeadHook) null
                            else hook.desc.returnTypeName
                        )
                    )
                }

                hook is HookWithTarget -> {
                    if (hook !is BodyHook && !hook.targetDesc.isStatic) {
                        add(IrInjectionReceiverParameter(hook.targetDesc.receiverTypeName))
                    }
                    if (hook is FieldSetHook) {
                        add(IrInjectionArgumentParameter("value", 0, hook.typeName))
                    }
                    addAll(hook.targetDesc.parameters.mapIndexed { index, parameter ->
                        IrInjectionArgumentParameter(parameter.name, index, parameter.typeName)
                    })
                    add(
                        IrInjectionOperationParameter(
                            if (hook is FieldSetHook) IrTypeName.VOID
                            else hook.targetDesc.returnTypeName
                        )
                    )
                }

                hook is LiteralHook -> {
                    add(IrInjectionValueParameter(hook.typeName))
                }

                hook is ArrayHook -> {
                    add(IrInjectionArgumentParameter("array", 0, hook.typeName))
                    add(IrInjectionArgumentParameter("index", 1, KPInt.asIrTypeName()))
                    if (hook.op == Op.Set) {
                        add(IrInjectionArgumentParameter("value", 2, hook.componentTypeName))
                    }
                }

                hook is ReturnHook && !hook.isInjectBased -> {
                    val returnTypeName = hook.returnTypeName ?: lapisError("Return type not found")
                    add(IrInjectionValueParameter(returnTypeName))
                }

                hook is LocalHook -> {
                    add(IrInjectionValueParameter(hook.typeName))
                }

                hook is InstanceofHook -> {
                    add(IrInjectionValueParameter(Object::class.asIr()))
                    add(IrInjectionOperationParameter(KPBoolean.asIr()))
                }
            }
            if (!hook.isInjectBased && hook.parameters.any { it is HookCancelParameter }) {
                add(IrInjectionCallbackParameter(hook.desc.returnTypeName))
            }
            addAll(
                hook.parameters.mapNotNull { lowerInjectionLocalBasedParameter(hook, it) }.sortedWith(
                    compareBy<IrInjectionLocalBasedParameter> { parameter ->
                        when (parameter) {
                            is IrInjectionParamParameter -> 0
                            is IrInjectionLocalParameter -> if (parameter.local is IrNamedLocal) 1 else 2
                        }
                    }.thenBy { parameter ->
                        (parameter as? IrInjectionParamParameter)?.localIndex
                    }.thenBy { parameter ->
                        ((parameter as? IrInjectionLocalParameter)?.local as? IrPositionalLocal)?.ordinal
                    }
                )
            )
        }
        val hookArguments = hook.parameters.map { lowerHookArgument(it) }
        return hook.ordinals.ifEmpty { listOf(null) }.map { ordinal ->
            when (hook) {
                is MethodHeadHook -> IrMethodHeadInjection(
                    name = hook.name,
                    methodMixinRef = hook.desc.getMixinRef(),
                    parameters = parameters,
                    hookArguments = hookArguments,
                    isStatic = hook.desc.isStatic,
                )

                is ConstructorHeadHook -> IrConstructorHeadInjection(
                    name = hook.name,
                    methodMixinRef = hook.desc.getMixinRef(),
                    parameters = parameters,
                    hookArguments = hookArguments,
                    atArgs = listOf(
                        "enforce" to when (hook.phase) {
                            ConstructorHeadPhase.PreBody -> "PRE_BODY"
                            ConstructorHeadPhase.PostDelegate -> "POST_DELEGATE"
                            ConstructorHeadPhase.PostInit -> "POST_INIT"
                        }
                    ),
                    isStatic = hook.desc.isStatic,
                )

                is BodyHook -> IrWrapMethodInjection(
                    name = hook.name,
                    methodMixinRef = hook.targetDesc.getMixinRef(),
                    isStaticTarget = hook.targetDesc.isStatic,
                    returnTypeName = hook.returnTypeName,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    isStatic = hook.desc.isStatic,
                )

                is TailHook -> IrReturnInjection(
                    name = hook.name,
                    methodMixinRef = hook.desc.getMixinRef(),
                    parameters = parameters,
                    hookArguments = hookArguments,
                    ordinal = null,
                    isTail = true,
                    isStatic = hook.desc.isStatic,
                )

                is LocalHook -> IrModifyVariableInjection(
                    name = hook.name,
                    methodMixinRef = hook.desc.getMixinRef(),
                    returnTypeName = hook.returnTypeName,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    local = lowerLocal(hook.local, hook.desc, hook.typeName),
                    isSet = hook.isSet,
                    ordinal = ordinal,
                    isStatic = hook.desc.isStatic,
                )

                is InstanceofHook -> IrInstanceofInjection(
                    name = hook.name,
                    methodMixinRef = hook.desc.getMixinRef(),
                    className = hook.className,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    ordinal = ordinal,
                    isStatic = hook.desc.isStatic,
                )

                is ReturnHook -> {
                    if (hook.isInjectBased) {
                        IrReturnInjection(
                            name = hook.name,
                            methodMixinRef = hook.desc.getMixinRef(),
                            parameters = parameters,
                            hookArguments = hookArguments,
                            ordinal = ordinal,
                            isTail = false,
                            isStatic = hook.desc.isStatic,
                        )
                    } else {
                        IrModifyReturnValueInjection(
                            name = hook.name,
                            methodMixinRef = hook.desc.getMixinRef(),
                            returnTypeName = hook.returnTypeName,
                            parameters = parameters,
                            hookArguments = hookArguments,
                            ordinal = ordinal,
                            isStatic = hook.desc.isStatic,
                        )
                    }
                }

                is LiteralHook -> {
                    val args = when (val literal = hook.literal) {
                        is ZeroLiteral -> {
                            val mixinConditions = literal.conditions.map {
                                Constant.Condition.entries[it.ordinal]
                            }
                            buildList {
                                add("intValue" to "0")
                                if (mixinConditions.isNotEmpty()) {
                                    add("expandZeroConditions" to mixinConditions.joinToString(","))
                                }
                            }
                        }

                        is IntLiteral -> listOf("intValue" to literal.value.toString())
                        is FloatLiteral -> listOf("floatValue" to literal.value.toString())
                        is LongLiteral -> listOf("longValue" to literal.value.toString())
                        is DoubleLiteral -> listOf("doubleValue" to literal.value.toString())
                        is StringLiteral -> listOf("stringValue" to literal.value)
                        is ClassLiteral -> listOf("classValue" to literal.className.internalName)
                        NullLiteral -> listOf("nullValue" to "true")
                    }
                    IrModifyExpressionValueInjection(
                        name = hook.name,
                        methodMixinRef = hook.desc.getMixinRef(),
                        parameters = parameters,
                        hookArguments = hookArguments,
                        constantTypeName = hook.typeName,
                        atArgs = args,
                        ordinal = ordinal,
                        isStatic = hook.desc.isStatic,
                    )
                }

                is FieldGetHook -> IrFieldGetInjection(
                    name = hook.name,
                    methodMixinRef = hook.desc.getMixinRef(),
                    parameters = parameters,
                    hookArguments = hookArguments,
                    targetMixinRef = hook.targetDesc.getMixinRef(isTarget = true),
                    isStaticTarget = hook.targetDesc.isStatic,
                    fieldTypeName = hook.typeName,
                    ordinal = ordinal,
                    isStatic = hook.desc.isStatic,
                )

                is FieldSetHook -> IrFieldSetInjection(
                    name = hook.name,
                    methodMixinRef = hook.desc.getMixinRef(),
                    parameters = parameters,
                    hookArguments = hookArguments,
                    targetMixinRef = hook.targetDesc.getMixinRef(isTarget = true),
                    isStaticTarget = hook.targetDesc.isStatic,
                    ordinal = ordinal,
                    isStatic = hook.desc.isStatic,
                )

                is ArrayHook -> IrArrayInjection(
                    name = hook.name,
                    methodMixinRef = hook.desc.getMixinRef(),
                    parameters = parameters,
                    hookArguments = hookArguments,
                    targetMixinRef = hook.targetDesc.getMixinRef(isTarget = true),
                    isStaticTarget = hook.targetDesc.isStatic,
                    ordinal = ordinal,
                    componentTypeName = hook.componentTypeName,
                    isStatic = hook.desc.isStatic,
                    isSet = hook.op == Op.Set,
                )

                is CallHook -> IrWrapOperationInjection(
                    name = hook.name,
                    methodMixinRef = hook.desc.getMixinRef(),
                    returnTypeName = hook.returnTypeName,
                    parameters = parameters,
                    hookArguments = hookArguments,
                    targetMixinRef = hook.targetDesc.getMixinRef(isTarget = true),
                    isStaticTarget = hook.targetDesc.isStatic,
                    isConstructorCall = hook.targetDesc is ConstructorDesc,
                    ordinal = ordinal,
                    isStatic = hook.desc.isStatic,
                )
            }
        }
    }

    private fun lowerInjectionLocalBasedParameter(
        hook: DomainHook,
        parameter: HookParameter
    ): IrInjectionLocalBasedParameter? =
        when (parameter) {
            is HookParamParameter -> {
                if (hook.isInjectBased) {
                    return null
                }
                val initialSlot = if (hook.desc.isStatic) 0 else 1
                val descParameter = hook.desc.parameters[parameter.index]
                val slotOffset = hook.desc.parameters.take(parameter.index).sumOf {
                    if (it.typeName.is64bit) 2
                    else 1
                }
                IrInjectionParamParameter(
                    name = descParameter.name,
                    index = parameter.index,
                    typeName = descParameter.typeName,
                    localIndex = initialSlot + slotOffset,
                )
            }

            is HookLocalParameter -> {
                val irLocal = when (val local = parameter.local) {
                    is NamedLocal -> IrNamedLocal(local.name)
                    is PositionalLocal -> {
                        val paramsOffset = buildList {
                            if (!hook.desc.isStatic) {
                                add(hook.desc.receiverTypeName)
                            }
                            addAll(hook.desc.parameters.map { it.typeName })
                        }.count { it == parameter.typeName }
                        IrPositionalLocal(paramsOffset + local.ordinal)
                    }
                }
                IrInjectionLocalParameter(parameter.name, parameter.typeName, irLocal)
            }

            else -> null
        }

    private fun lowerLocal(local: DomainLocal, desc: Desc, typeName: IrTypeName) =
        when (local) {
            is NamedLocal -> IrNamedLocal(local.name)
            is PositionalLocal -> {
                val paramsOffset = buildList {
                    if (!desc.isStatic) {
                        add(desc.receiverTypeName)
                    }
                    addAll(desc.parameters.map { it.typeName })
                }.count { it == typeName }
                IrPositionalLocal(paramsOffset + local.ordinal)
            }
        }

    private fun lowerHookArgument(parameter: HookParameter): IrHookArgument =
        when (parameter) {
            is HookOriginValueParameter -> IrHookOriginValueArgument()

            is HookOriginDescBodyParameter -> {
                val desc = parameter.desc
                val wrapper = IrDescBodyWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescBuiltin.Body.name.withQualifiedNamePrefix(desc.className)
                    ),
                    descClassName = desc.className,
                    builtin = builtins[DescBuiltin.Body],
                    parameters = desc.parameters.map { it.asIr() },
                    returnTypeName = desc.returnTypeName,
                )
                IrHookOriginDescBodyWrapperArgument(wrapper)
            }

            is HookOriginDescFieldGetParameter -> {
                val desc = parameter.desc
                val wrapper = IrDescFieldGetWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescBuiltin.FieldGet.name.withQualifiedNamePrefix(desc.className)
                    ),
                    descClassName = desc.className,
                    builtin = builtins[DescBuiltin.FieldGet],
                    receiverTypeName = if (desc.isStatic) null else desc.receiverTypeName,
                    fieldTypeName = desc.fieldTypeName,
                )
                IrHookOriginDescFieldGetWrapperArgument(wrapper)
            }

            is HookOriginDescFieldSetParameter -> {
                val desc = parameter.desc
                val wrapper = IrDescFieldSetWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescBuiltin.FieldSet.name.withQualifiedNamePrefix(desc.className)
                    ),
                    descClassName = desc.className,
                    builtin = builtins[DescBuiltin.FieldSet],
                    receiverTypeName = if (desc.isStatic) null else desc.receiverTypeName,
                    fieldTypeName = desc.fieldTypeName,
                )
                IrHookOriginDescFieldSetWrapperArgument(wrapper)
            }

            is HookOriginDescArrayGetParameter -> {
                val desc = parameter.desc
                val wrapper = IrDescArrayGetWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescBuiltin.ArrayGet.name.withQualifiedNamePrefix(desc.className)
                    ),
                    descClassName = desc.className,
                    builtin = builtins[DescBuiltin.ArrayGet],
                    arrayTypeName = desc.fieldTypeName,
                    arrayComponentTypeName = parameter.arrayComponentTypeName,
                )
                IrHookOriginDescArrayGetWrapperArgument(wrapper)
            }

            is HookOriginDescArraySetParameter -> {
                val desc = parameter.desc
                val wrapper = IrDescArraySetWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescBuiltin.ArraySet.name.withQualifiedNamePrefix(desc.className)
                    ),
                    descClassName = desc.className,
                    builtin = builtins[DescBuiltin.ArraySet],
                    arrayTypeName = desc.fieldTypeName,
                    arrayComponentTypeName = parameter.arrayComponentTypeName,
                )
                IrHookOriginDescArraySetWrapperArgument(wrapper)
            }

            is HookOriginDescCallParameter -> {
                val desc = parameter.desc
                val wrapper = IrDescCallWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescBuiltin.Call.name.withQualifiedNamePrefix(desc.className)
                    ),
                    descClassName = desc.className,
                    builtin = builtins[DescBuiltin.Call],
                    receiverTypeName = if (desc.isStatic) null else desc.receiverTypeName,
                    parameters = desc.parameters.map { it.asIr() },
                    returnTypeName = desc.returnTypeName,
                )
                IrHookOriginDescCallWrapperArgument(wrapper)
            }

            is HookCancelParameter -> {
                val desc = parameter.desc
                val wrapper = IrDescCancelWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescBuiltin.Cancel.name.withQualifiedNamePrefix(desc.className)
                    ),
                    descClassName = desc.className,
                    builtin = builtins[DescBuiltin.Cancel],
                    parameters = desc.parameters.map { it.asIr() },
                    returnTypeName = if (desc is MethodDesc) desc.returnTypeName else null
                )
                IrHookCancelArgument(wrapper)
            }

            is HookOriginInstanceofParameter -> IrHookOriginInstanceofArgument()

            is HookOrdinalParameter -> IrHookOrdinalArgument
            is HookParamParameter -> IrHookParamArgument(parameter.name)
            is HookLocalParameter -> IrHookLocalArgument(parameter.name)
        }

    private inline fun <reified W : IrDescWrapper> findOriginDescWrapper(descClassName: IrClassName): W? =
        mixins.asSequence()
            .flatMap { it.injections }
            .flatMap { it.hookArguments }
            .filterIsInstance<IrHookOriginDescWrapperArgument<*>>()
            .map { it.wrapper }
            .filterIsInstance<W>()
            .firstOrNull { it.descClassName == descClassName }

    private fun findCancelDescWrapper(descClassName: IrClassName): IrDescCancelWrapper? =
        mixins.asSequence()
            .flatMap { it.injections }
            .flatMap { it.hookArguments }
            .filterIsInstance<IrHookCancelArgument>()
            .map { it.wrapper }
            .firstOrNull { it.descClassName == descClassName }
}

fun List<FunctionParameter>.asIr(): List<IrParameter> =
    map { parameter -> IrParameter(parameter.name, parameter.typeName) }

fun FunctionTypeParameter.asIr(): IrFunctionTypeParameter =
    IrFunctionTypeParameter(name, typeName)

fun KClass<*>.asIr(): IrClassName =
    asClassName().asIr()

fun KPTypeName.asIrTypeName(): IrTypeName =
    IrTypeName(this)

fun KPClassName.asIr(): IrClassName =
    IrClassName(this)

fun KPParameterizedTypeName.asIr(): IrParameterizedTypeName =
    IrParameterizedTypeName(this)

fun KPWildcardTypeName.asIr(): IrWildcardTypeName =
    IrWildcardTypeName(this)

fun KPTypeVariableName.asIr(): IrTypeVariableName =
    IrTypeVariableName(this)

fun KPLambdaTypeName.asIr(): IrLambdaTypeName =
    IrLambdaTypeName(this)

fun KPDynamic.asIr(): IrDynamic =
    IrDynamic(this)

private fun String.withQualifiedNamePrefix(className: IrClassName): String =
    className.qualifiedName.replace('.', '_') + "_$this"
