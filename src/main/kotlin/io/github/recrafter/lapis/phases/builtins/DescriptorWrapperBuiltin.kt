package io.github.recrafter.lapis.phases.builtins

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import io.github.recrafter.lapis.extensions.InternalPrefix.ARGUMENT
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.withInternalPrefix
import io.github.recrafter.lapis.phases.common.jvmDescriptor
import io.github.recrafter.lapis.phases.generator.builders.toCodeBlock
import io.github.recrafter.lapis.phases.lowering.IrModifier
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.asIrParameterizedTypeName
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.models.*
import io.github.recrafter.lapis.phases.lowering.types.IrLambdaTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeVariableName
import io.github.recrafter.lapis.phases.lowering.types.orVoid
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

sealed class DescriptorWrapperBuiltin<T : IrDescriptorWrapperImpl>(
    override val name: String,
    val builtin: SimpleBuiltin,
) : Builtin<KPClass> {

    data object FieldGet : DescriptorWrapperBuiltin<IrFieldGetDescriptorWrapperImpl>("FieldGet", SimpleBuiltin.Field) {
        override fun generateImpl(
            destination: KPFileBuilder,
            impl: IrFieldGetDescriptorWrapperImpl,
            resolveBuiltin: BuiltinResolver,
        ) {
            val receiverParameter = impl.receiverTypeName?.let {
                IrParameter("receiver".withInternalPrefix(), it, listOf(IrModifier.PUBLIC))
            }
            val operationParameter = IrParameter(
                "operation".withInternalPrefix(),
                Operation::class.asIrParameterizedTypeName(impl.fieldTypeName),
                listOf(IrModifier.PUBLIC)
            )
            destination.addType(buildKotlinClass(impl.className.simpleName) {
                setConstructor(listOfNotNull(receiverParameter, operationParameter))
                addSuperInterface(impl.superClassTypeName)
            })
            val getReceiverFunction = receiverParameter?.let {
                buildKotlinFunction("getReceiver", jvmNamespace = impl.className) {
                    setModifiers(IrModifier.INLINE)
                    setReceiverType(impl.superClassTypeName)
                    setReturnType(receiverParameter.typeName)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(impl.className)
                            arg(receiverParameter)
                        }
                    }
                }
            }?.also(destination::addFunction)

            destination.addFunction(buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(impl.superClassTypeName)
                val receiverParameter = getReceiverFunction?.let {
                    buildKotlinParameter("receiver", impl.receiverTypeName) {
                        defaultValue(buildKotlinCodeBlock("%N()") {
                            arg(getReceiverFunction)
                        })
                    }
                }
                receiverParameter?.let(::addParameter)
                setReturnType(impl.fieldTypeName)
                setBody {
                    return_(buildString {
                        append("(this as %T).%N.%L(")
                        if (receiverParameter != null) {
                            append("%N")
                        }
                        append(")")
                    }) {
                        arg(impl.className)
                        arg(operationParameter)
                        arg(Operation<*>::call)
                        receiverParameter?.let(::arg)
                    }
                }
            })
        }
    }

    data object FieldSet : DescriptorWrapperBuiltin<IrFieldSetDescriptorWrapperImpl>("FieldSet", SimpleBuiltin.Field) {
        override fun generateImpl(
            destination: KPFileBuilder,
            impl: IrFieldSetDescriptorWrapperImpl,
            resolveBuiltin: BuiltinResolver,
        ) {
            val receiverParameter = impl.receiverTypeName?.let {
                IrParameter(
                    "receiver".withInternalPrefix(),
                    it,
                    listOf(IrModifier.PUBLIC)
                )
            }
            val valueParameter = IrParameter(
                "value".withInternalPrefix(),
                impl.fieldTypeName,
                listOf(IrModifier.PUBLIC)
            )
            val operationParameter = IrParameter(
                "operation".withInternalPrefix(),
                Operation::class.asIrParameterizedTypeName(IrTypeName.VOID),
                listOf(IrModifier.PUBLIC)
            )
            destination.addType(buildKotlinClass(impl.className.simpleName) {
                setConstructor(listOfNotNull(receiverParameter, valueParameter, operationParameter))
                addSuperInterface(impl.superClassTypeName)
            })
            val valueProperty = buildKotlinProperty("value", impl.fieldTypeName, jvmNamespace = impl.className) {
                setReceiverType(impl.superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(impl.className)
                            arg(valueParameter)
                        }
                    }
                }
            }.also(destination::addProperty)

            val getReceiverFunction = receiverParameter?.let {
                buildKotlinFunction("getReceiver", jvmNamespace = impl.className) {
                    setModifiers(IrModifier.INLINE)
                    setReceiverType(impl.superClassTypeName)
                    setReturnType(receiverParameter.typeName)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(impl.className)
                            arg(receiverParameter)
                        }
                    }
                }
            }?.also(destination::addFunction)

            destination.addFunction(buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(impl.superClassTypeName)
                val valueParameter = buildKotlinParameter("value", impl.fieldTypeName) {
                    defaultValue(buildKotlinCodeBlock("this.%N") {
                        arg(valueProperty)
                    })
                }.also(::addParameter)

                val receiverParameter = getReceiverFunction?.let {
                    buildKotlinParameter("receiver", impl.receiverTypeName) {
                        defaultValue(buildKotlinCodeBlock("%N()") {
                            arg(getReceiverFunction)
                        })
                    }
                }?.also(::addParameter)
                setBody {
                    code_(buildString {
                        append("(this as %T).%N.%L(")
                        if (receiverParameter != null) {
                            append("%N, ")
                        }
                        append("%N)")
                    }) {
                        arg(impl.className)
                        arg(operationParameter)
                        arg(Operation<*>::call)
                        receiverParameter?.let(::arg)
                        arg(valueParameter)
                    }
                }
            })
        }
    }

    data object ArrayGet : DescriptorWrapperBuiltin<IrArrayGetDescriptorWrapperImpl>("ArrayGet", SimpleBuiltin.Field) {
        override fun generateImpl(
            destination: KPFileBuilder,
            impl: IrArrayGetDescriptorWrapperImpl,
            resolveBuiltin: BuiltinResolver,
        ) {
            val arrayParameter = IrParameter(
                "array".withInternalPrefix(),
                impl.arrayTypeName,
                listOf(IrModifier.PUBLIC)
            )
            val indexParameter = IrParameter(
                "index".withInternalPrefix(),
                KPInt.asIrTypeName(),
                listOf(IrModifier.PUBLIC)
            )
            destination.addType(buildKotlinClass(impl.className.simpleName) {
                setConstructor(arrayParameter, indexParameter)
                addSuperInterface(impl.superClassTypeName)
            })
            val arrayProperty = buildKotlinProperty("array", arrayParameter.typeName, jvmNamespace = impl.className) {
                setReceiverType(impl.superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(impl.className)
                            arg(arrayParameter)
                        }
                    }
                }
            }.also(destination::addProperty)

            val indexProperty = buildKotlinProperty("index", indexParameter.typeName, jvmNamespace = impl.className) {
                setReceiverType(impl.superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(impl.className)
                            arg(indexParameter)
                        }
                    }
                }
            }.also(destination::addProperty)

            destination.addFunction(buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(impl.superClassTypeName)
                val arrayParameter = buildKotlinParameter("array", arrayParameter.typeName) {
                    defaultValue(buildKotlinCodeBlock("this.%N") {
                        arg(arrayProperty)
                    })
                }
                val indexParameter = buildKotlinParameter("index", indexParameter.typeName) {
                    defaultValue(buildKotlinCodeBlock("this.%N") {
                        arg(indexProperty)
                    })
                }
                addParameters(listOf(arrayParameter, indexParameter))
                setReturnType(impl.arrayComponentTypeName)
                setBody {
                    return_("%N[%N]") {
                        arg(arrayParameter)
                        arg(indexParameter)
                    }
                }
            })
        }
    }

    data object ArraySet : DescriptorWrapperBuiltin<IrArraySetDescriptorWrapperImpl>("ArraySet", SimpleBuiltin.Field) {
        override fun generateImpl(
            destination: KPFileBuilder,
            impl: IrArraySetDescriptorWrapperImpl,
            resolveBuiltin: BuiltinResolver,
        ) {
            val arrayParameter = IrParameter(
                "array".withInternalPrefix(),
                impl.arrayTypeName,
                listOf(IrModifier.PUBLIC)
            )
            val indexParameter = IrParameter(
                "index".withInternalPrefix(),
                KPInt.asIrTypeName(),
                listOf(IrModifier.PUBLIC)
            )
            val valueParameter = IrParameter(
                "value".withInternalPrefix(),
                impl.arrayComponentTypeName,
                listOf(IrModifier.PUBLIC)
            )
            destination.addType(buildKotlinClass(impl.className.simpleName) {
                setConstructor(arrayParameter, indexParameter, valueParameter)
                addSuperInterface(impl.superClassTypeName)
            })
            val arrayProperty = buildKotlinProperty("array", arrayParameter.typeName, jvmNamespace = impl.className) {
                setReceiverType(impl.superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(impl.className)
                            arg(arrayParameter)
                        }
                    }
                }
            }.also(destination::addProperty)

            val indexProperty = buildKotlinProperty("index", indexParameter.typeName, jvmNamespace = impl.className) {
                setReceiverType(impl.superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(impl.className)
                            arg(indexParameter)
                        }
                    }
                }
            }.also(destination::addProperty)

            val valueProperty = buildKotlinProperty("value", valueParameter.typeName, jvmNamespace = impl.className) {
                setReceiverType(impl.superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(impl.className)
                            arg(valueParameter)
                        }
                    }
                }
            }.also(destination::addProperty)

            destination.addFunction(buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(impl.superClassTypeName)
                val arrayParameter = buildKotlinParameter(arrayProperty.name, arrayParameter.typeName) {
                    defaultValue(buildKotlinCodeBlock("this.%N") {
                        arg(arrayProperty)
                    })
                }
                val indexParameter = buildKotlinParameter(indexProperty.name, indexParameter.typeName) {
                    defaultValue(buildKotlinCodeBlock("this.%N") {
                        arg(indexProperty)
                    })
                }
                val valueParameter = buildKotlinParameter(valueProperty.name, valueParameter.typeName) {
                    defaultValue(buildKotlinCodeBlock("this.%N") {
                        arg(valueProperty)
                    })
                }
                addParameters(listOf(arrayParameter, indexParameter, valueParameter))
                setBody {
                    code_("%N[%N] = %N") {
                        arg(arrayParameter)
                        arg(indexParameter)
                        arg(valueParameter)
                    }
                }
            })
        }
    }

    data object Body : DescriptorWrapperBuiltin<IrBodyDescriptorWrapperImpl>("Body", SimpleBuiltin.Method) {
        override fun generateImpl(
            destination: KPFileBuilder,
            impl: IrBodyDescriptorWrapperImpl,
            resolveBuiltin: BuiltinResolver,
        ) {
            val namedParameters = impl.parameters.mapNotNull { parameter ->
                parameter.name?.let { IrParameter(parameter.name, parameter.typeName) }
            }
            val argumentParameters = impl.parameters.mapIndexed { index, parameter ->
                val name = parameter.name ?: index.toString()
                IrParameter(
                    name.withInternalPrefix(ARGUMENT),
                    parameter.typeName,
                    listOf(IrModifier.PUBLIC)
                )
            }
            val operationParameter = IrParameter(
                "operation".withInternalPrefix(),
                Operation::class.asIrParameterizedTypeName(impl.returnTypeName.orVoid()),
                listOf(IrModifier.PUBLIC)
            )
            destination.addType(buildKotlinClass(impl.className.simpleName) {
                setConstructor(argumentParameters + operationParameter)
                addSuperInterface(impl.superClassTypeName)
            })
            destination.addProperties(namedParameters.map { parameter ->
                buildKotlinProperty(parameter.name, parameter.typeName, jvmNamespace = impl.className) {
                    setReceiverType(impl.superClassTypeName)
                    setGetter {
                        setModifiers(IrModifier.INLINE)
                        setBody {
                            return_("(this as %T).%L") {
                                arg(impl.className)
                                arg(parameter.name.withInternalPrefix(ARGUMENT))
                            }
                        }
                    }
                }
            })
            val operationArguments = impl.parameters.mapIndexed { index, parameter ->
                if (parameter.name != null) {
                    namedParameters.first { it.name == parameter.name }
                } else {
                    argumentParameters[index]
                }
            }
            destination.addFunction(buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(impl.superClassTypeName)
                addParameters(namedParameters.map { parameter ->
                    buildKotlinParameter(parameter.name, parameter.typeName) {
                        defaultValue(buildKotlinCodeBlock("this.%N") {
                            arg(parameter)
                        })
                    }
                })
                setReturnType(impl.returnTypeName)
                setBody {
                    code_(
                        format = buildString {
                            append("(this as %T).%N.%L(")
                            if (operationArguments.isNotEmpty()) {
                                append(operationArguments.joinToString { "%N" })
                            }
                            append(")")
                        },
                        isReturn = impl.returnTypeName != null
                    ) {
                        arg(impl.className)
                        arg(operationParameter)
                        arg(Operation<*>::call)
                        operationArguments.forEach(::arg)
                    }
                }
            })
        }
    }

    data object Call : DescriptorWrapperBuiltin<IrCallDescriptorWrapperImpl>("Call", SimpleBuiltin.Callable) {
        override fun generateImpl(
            destination: KPFileBuilder,
            impl: IrCallDescriptorWrapperImpl,
            resolveBuiltin: BuiltinResolver,
        ) {
            val receiverParameter = impl.receiverTypeName?.let {
                IrParameter(
                    "receiver".withInternalPrefix(),
                    it,
                    listOf(IrModifier.PUBLIC)
                )
            }
            val namedParameters = impl.parameters.mapNotNull { parameter ->
                parameter.name?.let {
                    IrParameter(
                        parameter.name,
                        parameter.typeName,
                        listOf(IrModifier.PUBLIC)
                    )
                }
            }
            val argumentParameters = impl.parameters.mapIndexed { index, parameter ->
                val name = parameter.name ?: index.toString()
                IrParameter(
                    name.withInternalPrefix(ARGUMENT),
                    parameter.typeName,
                    listOf(IrModifier.PUBLIC)
                )
            }
            val operationParameter = IrParameter(
                "operation".withInternalPrefix(),
                Operation::class.asIrParameterizedTypeName(impl.returnTypeName.orVoid())
            )
            destination.addType(buildKotlinClass(impl.className.simpleName) {
                setConstructor(listOfNotNull(receiverParameter) + argumentParameters + operationParameter)
                addSuperInterface(impl.superClassTypeName)
            })
            destination.addProperties(namedParameters.map { parameter ->
                buildKotlinProperty(parameter.name, parameter.typeName, jvmNamespace = impl.className) {
                    setReceiverType(impl.superClassTypeName)
                    setGetter {
                        setModifiers(IrModifier.INLINE)
                        setBody {
                            return_("(this as %T).%L") {
                                arg(impl.className)
                                arg(parameter.name.withInternalPrefix(ARGUMENT))
                            }
                        }
                    }
                }
            })
            val getReceiverFunction = receiverParameter?.let {
                buildKotlinFunction("getReceiver", jvmNamespace = impl.className) {
                    setModifiers(IrModifier.INLINE)
                    setReceiverType(impl.superClassTypeName)
                    setReturnType(receiverParameter.typeName)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(impl.className)
                            arg(receiverParameter)
                        }
                    }
                }
            }?.also(destination::addFunction)
            val namedArgumentParameters = namedParameters.map { parameter ->
                buildKotlinParameter(parameter) {
                    defaultValue(buildKotlinCodeBlock("this.%N") {
                        arg(parameter)
                    })
                }
            }
            val operationArguments = impl.parameters.mapIndexed { index, parameter ->
                if (parameter.name != null) {
                    namedParameters.first { it.name == parameter.name }
                } else {
                    argumentParameters[index]
                }
            }
            destination.addFunction(buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(impl.superClassTypeName)
                addParameters(namedArgumentParameters)
                setReturnType(impl.returnTypeName)
                setBody {
                    code_(
                        format = buildString {
                            append("(this as %T).%N.%L(")
                            getReceiverFunction?.let { append("%N()") }
                            if (operationArguments.isNotEmpty()) {
                                append(operationArguments.joinToString(prefix = ", ") { "%N" })
                            }
                            append(")")
                        },
                        isReturn = impl.returnTypeName != null
                    ) {
                        arg(impl.className)
                        arg(operationParameter)
                        arg(Operation<*>::call)
                        getReceiverFunction?.let(::arg)
                        operationArguments.forEach(::arg)
                    }
                }
            })
            val receiverTypeName = impl.receiverTypeName ?: return
            destination.addFunction(buildKotlinFunction("call", jvmNamespace = impl.className) {
                setModifiers(IrModifier.INLINE)
                setReceiverType(impl.superClassTypeName)
                val receiverParameter = IrParameter("_receiver", receiverTypeName)
                setContextParameters(listOf(receiverParameter))
                addParameters(namedArgumentParameters)
                setReturnType(impl.returnTypeName)
                setBody {
                    code_(
                        format = buildString {
                            append("(this as %T).%N.%L(")
                            append("%N")
                            if (operationArguments.isNotEmpty()) {
                                append(operationArguments.joinToString(prefix = ", ") { "%N" })
                            }
                            append(")")
                        },
                        isReturn = impl.returnTypeName != null
                    ) {
                        arg(impl.className)
                        arg(operationParameter)
                        arg(Operation<*>::call)
                        arg(receiverParameter)
                        operationArguments.forEach(::arg)
                    }
                }
            })
            destination.addFunction(buildKotlinFunction("withReceiver", jvmNamespace = impl.className) { functionName ->
                setModifiers(IrModifier.INLINE)
                setReceiverType(impl.superClassTypeName)
                val receiverParameter = buildKotlinParameter("receiver", receiverTypeName).also(::addParameter)
                val blockType = IrLambdaTypeName.of(
                    receiverTypeName = impl.superClassTypeName,
                    returnTypeName = impl.returnTypeName,
                    contextParameters = listOf(receiverTypeName),
                )
                val blockParameter = buildKotlinParameter("block", blockType).also(::addParameter)
                setReturnType(impl.returnTypeName)
                setBody {
                    with_(receiverParameter.toCodeBlock()) {
                        code_(
                            format = "%N(this@%L)",
                            isReturn = impl.returnTypeName != null
                        ) {
                            arg(blockParameter)
                            arg(functionName)
                        }
                    }
                }
            })
        }
    }

    data object Cancel : DescriptorWrapperBuiltin<IrCancelDescriptorWrapperImpl>("Cancel", SimpleBuiltin.Method) {
        override fun generateImpl(
            destination: KPFileBuilder,
            impl: IrCancelDescriptorWrapperImpl,
            resolveBuiltin: BuiltinResolver,
        ) {
            val callbackParameter = IrParameter(
                "callback".withInternalPrefix(),
                impl.returnTypeName
                    ?.let { CallbackInfoReturnable::class.asIrParameterizedTypeName(it) }
                    ?: CallbackInfo::class.asIrTypeName(),
                listOf(IrModifier.PUBLIC)
            )
            destination.addType(buildKotlinClass(impl.className.simpleName) {
                setConstructor(callbackParameter)
                addSuperInterface(impl.superClassTypeName)
            })
            buildKotlinProperty("isCanceled", KPBoolean.asIrTypeName(), jvmNamespace = impl.className) {
                setReceiverType(impl.superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_(buildString {
                            append("(this as %T).%N.%L()")
                            if (impl.returnTypeName != null) {
                                append(" != null")
                            }
                        }) {
                            arg(impl.className)
                            arg(callbackParameter)
                            if (impl.returnTypeName != null) {
                                arg(CallbackInfoReturnable<*>::getReturnValue)
                            } else {
                                arg(CallbackInfo::isCancelled)
                            }
                        }
                    }
                }
            }.also(destination::addProperty)
            impl.returnTypeName?.let { returnTypeName ->
                val primitiveJvmName = returnTypeName.jvmDescriptor.getPrimitiveName(allowVoid = false)
                val type = if (primitiveJvmName != null) returnTypeName else returnTypeName.makeNullable()
                buildKotlinProperty("returnValue", type, jvmNamespace = impl.className) {
                    setReceiverType(impl.superClassTypeName)
                    setGetter {
                        setModifiers(IrModifier.INLINE)
                        setBody {
                            return_("(this as %T).%N.%L()") {
                                arg(impl.className)
                                arg(callbackParameter)
                                primitiveJvmName
                                    ?.let { arg(CallbackInfoReturnable<*>::getReturnValue.name + it) }
                                    ?: arg(CallbackInfoReturnable<*>::getReturnValue)
                            }
                        }
                    }
                }.also(destination::addProperty)
            }
            destination.addFunction(buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(impl.superClassTypeName)
                setReturnType(KPNothing.asIrClassName())
                val returnValueParameter = impl.returnTypeName
                    ?.let { IrParameter("returnValue", it) }
                    ?.also(::addParameter)
                setBody {
                    code_("(this as %T)") {
                        arg(impl.className)
                    }
                    code_(buildString {
                        append("%N.%L(")
                        returnValueParameter?.let { append("%N") }
                        append(")")
                    }) {
                        arg(callbackParameter)
                        arg(
                            if (returnValueParameter != null) CallbackInfoReturnable<*>::setReturnValue
                            else CallbackInfo::cancel
                        )
                        returnValueParameter?.let(::arg)
                    }
                    throw_("%T") {
                        arg(resolveBuiltin(SimpleBuiltin.CancelSignal))
                    }
                }
            })
        }
    }

    override val isInternal: Boolean = false

    override fun generate(resolveBuiltin: BuiltinResolver): KPClass =
        buildKotlinInterface(name) {
            setModifiers(IrModifier.PUBLIC)
            setVariableTypes(IrTypeVariableName.of("D", resolveBuiltin(builtin).parameterizedByStar()))
        }

    abstract fun generateImpl(destination: KPFileBuilder, impl: T, resolveBuiltin: BuiltinResolver)

    companion object {
        val entries: List<DescriptorWrapperBuiltin<*>> =
            listOf(FieldGet, FieldSet, ArrayGet, ArraySet, Body, Call, Cancel)
    }
}
