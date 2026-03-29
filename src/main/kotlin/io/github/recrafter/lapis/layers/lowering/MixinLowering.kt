package io.github.recrafter.lapis.layers.lowering

import com.squareup.kotlinpoet.asClassName
import io.github.recrafter.lapis.Options
import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.layers.generator.builtins.Builtins
import io.github.recrafter.lapis.layers.generator.builtins.DescBuiltin
import io.github.recrafter.lapis.layers.lowering.types.*
import io.github.recrafter.lapis.layers.validator.*
import kotlin.reflect.KClass

class MixinLowering(
    private val options: Options,
    private val builtins: Builtins,
) {
    private val descByQualifiedName: MutableMap<String, IrDesc> = mutableMapOf()
    private val patches: MutableList<Patch> = mutableListOf()

    fun lower(validatorResult: ValidatorResult): IrResult {
        patches += validatorResult.patches
        return IrResult(
            schemas = validatorResult.schemas.map { schema ->
                IrSchema(
                    containingFile = schema.containingFile,

                    makePublic = schema.hasAccess,
                    removeFinal = schema.isMarkedAsFinal,
                    className = schema.className,
                    targetClassName = schema.targetClassName,
                    descriptors = schema.descriptors.map { desc ->
                        lowerDesc(desc).also { irDesc -> descByQualifiedName[desc.className.qualifiedName] = irDesc }
                    },
                )
            },
            mixins = patches.map { lowerMixin(it) },
        )
    }

    private fun lowerDesc(desc: Desc): IrDesc =
        when (desc) {
            is InvokableDesc -> {
                val callWrapper = IrDescCallWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescBuiltin.Call.name.withQualifiedNamePrefix(desc.className)
                    ),
                    superClassTypeName = builtins[DescBuiltin.Call].generic(desc.className),
                    receiverTypeName = if (desc.isStatic) null else desc.receiverTypeName,
                    parameters = desc.parameters.map { it.asIr() },
                    returnTypeName = desc.returnTypeName,
                )
                val cancelWrapper = IrDescCancelWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescBuiltin.Cancel.name.withQualifiedNamePrefix(desc.className)
                    ),
                    superClassTypeName = builtins[DescBuiltin.Cancel].generic(desc.className),
                    parameters = desc.parameters.map { it.asIr() },
                    returnTypeName = desc.returnTypeName
                )
                if (desc is ConstructorDesc) {
                    IrConstructorDesc(
                        makePublic = desc.makePublic,
                        callWrapper = callWrapper,
                        cancelWrapper = cancelWrapper,
                        parameters = desc.parameters.map { it.asIr() },
                        returnTypeName = desc.className,
                    )
                } else {
                    IrMethodDesc(
                        makePublic = desc.makePublic,
                        removeFinal = desc.removeFinal,
                        name = desc.name,
                        targetName = desc.targetName,
                        callWrapper = callWrapper,
                        cancelWrapper = cancelWrapper,
                        parameters = desc.parameters.map { it.asIr() },
                        returnTypeName = desc.returnTypeName,
                    )
                }
            }

            is FieldDesc -> {
                val fieldGetWrapper = IrDescFieldGetWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescBuiltin.FieldGet.name.withQualifiedNamePrefix(desc.className)
                    ),
                    superClassTypeName = builtins[DescBuiltin.FieldGet].generic(desc.className),
                    receiverTypeName = if (desc.isStatic) null else desc.receiverTypeName,
                    fieldTypeName = desc.fieldTypeName,
                )
                val fieldWriteWrapper = IrDescFieldWriteWrapper(
                    className = IrClassName.of(
                        options.generatedPackageName,
                        DescBuiltin.FieldWrite.name.withQualifiedNamePrefix(desc.className)
                    ),
                    superClassTypeName = builtins[DescBuiltin.FieldWrite].generic(desc.className),
                    receiverTypeName = if (desc.isStatic) null else desc.receiverTypeName,
                    fieldTypeName = desc.fieldTypeName,
                )
                IrFieldDesc(
                    makePublic = desc.makePublic,
                    removeFinal = desc.removeFinal,
                    name = desc.name,
                    targetName = desc.targetName,
                    fieldGetWrapper = fieldGetWrapper,
                    fieldWriteWrapper = fieldWriteWrapper,
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
                        IrFieldGetterExtension(
                            name = property.name,
                            typeName = property.typeName,
                        )
                    )
                    if (property.isMutable) {
                        add(
                            IrFieldSetterExtension(
                                name = property.name,
                                typeName = property.typeName,
                            )
                        )
                    }
                }
                addAll(patch.sharedFunctions.map { function ->
                    IrMethodExtension(
                        name = function.name,
                        parameters = function.parameters.asIr(),
                        returnTypeName = function.returnTypeName,
                    )
                })
            },
        )
    }

    private fun lowerInjections(hook: HookModel): List<IrInjection> {
        val parameters = buildList {
            if (hook is HookWithTarget) {
                if (hook !is BodyHook && !hook.targetDesc.isStatic) {
                    add(IrInjectionReceiverParameter(hook.targetDesc.receiverTypeName))
                }
                if (hook is FieldWriteHook) {
                    add(IrInjectionArgumentParameter("fieldValue", 0, hook.typeName))
                }
                addAll(hook.targetDesc.parameters.mapIndexed { index, parameter ->
                    IrInjectionArgumentParameter(parameter.name, index, parameter.typeName)
                })
                add(
                    IrInjectionOperationParameter(
                        if (hook is FieldWriteHook) IrTypeName.VOID
                        else hook.targetDesc.returnTypeName
                    )
                )
            } else if (hook is LiteralHook) {
                add(IrInjectionValueParameter(hook.typeName))
            }
            addAll(hook.parameters.flatMap { lowerInjectionParameter(hook, it) })
        }
        return hook.ordinals.map { ordinal ->
            when (hook) {
                is BodyHook -> IrWrapMethodInjection(
                    name = hook.name,
                    methodMixinRef = hook.targetDesc.getMixinRef(),
                    isStatic = hook.targetDesc.isStatic,
                    returnTypeName = hook.returnTypeName,
                    parameters = parameters,
                    hookArguments = hook.parameters.map { lowerHookArgument(it) },
                )

                is CallHook -> IrWrapOperationInjection(
                    name = hook.name,
                    methodMixinRef = hook.desc.getMixinRef(),
                    returnTypeName = hook.returnTypeName,
                    parameters = parameters,
                    hookArguments = hook.parameters.map { lowerHookArgument(it) },
                    targetMixinRef = hook.targetDesc.getMixinRef(isTarget = true),
                    isStatic = hook.targetDesc.isStatic,
                    ordinal = ordinal,
                )

                is LiteralHook -> {
                    val args = when (val literal = hook.literal) {
                        is ZeroLiteral -> {
                            TODO()
//                            val expandZeroConditions = literal.conditions.map {
//                                when (it) {
//                                    `<` -> Constant.Condition.LESS_THAN_ZERO
//                                    `<=` -> Constant.Condition.LESS_THAN_OR_EQUAL_TO_ZERO
//                                    `>=` -> Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO
//                                    `>` -> Constant.Condition.GREATER_THAN_ZERO
//                                }
//                            }
//                            listOf(
//                                "intValue" to "0",
//                                "expandZeroConditions" to expandZeroConditions.joinToString(",")
//                            )
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
                        hookArguments = hook.parameters.map { lowerHookArgument(it) },
                        constantTypeName = hook.typeName,
                        args = args,
                        ordinal = ordinal,
                    )
                }

                is FieldGetHook -> IrFieldGetInjection(
                    name = hook.name,
                    methodMixinRef = hook.desc.getMixinRef(),
                    parameters = parameters,
                    hookArguments = hook.parameters.map { lowerHookArgument(it) },
                    targetMixinRef = hook.targetDesc.getMixinRef(isTarget = true),
                    isStatic = hook.targetDesc.isStatic,
                    fieldTypeName = hook.typeName,
                    ordinal = ordinal,
                )

                is FieldWriteHook -> IrFieldWriteInjection(
                    name = hook.name,
                    methodMixinRef = hook.desc.getMixinRef(),
                    parameters = parameters,
                    hookArguments = hook.parameters.map { lowerHookArgument(it) },
                    targetMixinRef = hook.targetDesc.getMixinRef(isTarget = true),
                    isStatic = hook.targetDesc.isStatic,
                    ordinal = ordinal,
                )
            }
        }
    }

    private fun lowerInjectionParameter(hook: HookModel, parameter: HookParameter): List<IrInjectionParameter> =
        when (parameter) {
            is HookOriginParameter -> emptyList()
            is HookCancelParameter -> listOf(IrInjectionCallbackParameter(hook.desc.returnTypeName))
            is HookParamParameter -> buildList {
                var currentSlot = if (hook.desc.isStatic) 0 else 1
                addAll(hook.desc.parameters.mapIndexed { index, parameter ->
                    val typeName = parameter.typeName
                    val irParameter = IrInjectionParamParameter(parameter.name, index, typeName, currentSlot)
                    currentSlot += if (typeName.is64bit) 2 else 1
                    return@mapIndexed irParameter
                })
            }

            is HookLocalParameter -> {
                val signatureOffset = buildList {
                    if (!hook.desc.isStatic) {
                        add(hook.desc.receiverTypeName)
                    }
                    addAll(hook.desc.parameters.map { it.typeName })
                }.count { it == parameter.typeName }
                listOf(
                    IrInjectionLocalParameter(
                        parameter.name,
                        parameter.typeName,
                        signatureOffset + parameter.ordinal
                    )
                )
            }
        }

    private fun lowerHookArgument(parameter: HookParameter): IrHookArgument =
        when (parameter) {
            is HookOriginValueParameter -> IrHookOriginValueArgument()
            is HookOriginCallParameter -> {
                IrHookOriginDescCallWrapperArgument(findDesc<IrInvokableDesc>(parameter.desc.className).callWrapper)
            }

            is HookCancelParameter -> {
                IrHookCancelArgument(findDesc<IrInvokableDesc>(parameter.desc.className).cancelWrapper)
            }

            is HookParamParameter -> IrHookParamArgument(parameter.name)
            is HookLocalParameter -> IrHookLocalArgument(parameter.name, parameter.ordinal)
        }

    private inline fun <reified T : IrDesc> findDesc(className: IrClassName): T {
        val qualifiedName = className.qualifiedName
        return descByQualifiedName[qualifiedName]?.castOrNull()
            ?: lapisError("Desc for ${qualifiedName.quoted()} not found")
    }
}

fun List<FunctionParameter>.asIr(): List<IrParameter> =
    map { parameter -> IrParameter(parameter.name, parameter.typeName) }

fun FunctionTypeParameter.asIr(): IrFunctionTypeParameter =
    IrFunctionTypeParameter(name, typeName)

fun KClass<*>.asIr(): IrClassName =
    asClassName().asIr()

fun KPType.asIr(): IrTypeName =
    IrTypeName(this)

fun KPClassType.asIr(): IrClassName =
    IrClassName(this)

fun KPGenericType.asIr(): IrGenericTypeName =
    IrGenericTypeName(this)

fun KPWildcardType.asIr(): IrWildcardTypeName =
    IrWildcardTypeName(this)

fun KPVariableType.asIr(): IrVariableTypeName =
    IrVariableTypeName(this)

fun String.withQualifiedNamePrefix(className: IrClassName): String =
    className.qualifiedName.replace('.', '_') + "_$this"
