package io.github.recrafter.lapis.layers.generator.builtins

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import io.github.recrafter.lapis.extensions.InternalPrefix.ARGUMENT
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.withInternalPrefix
import io.github.recrafter.lapis.layers.lowering.IrModifier
import io.github.recrafter.lapis.layers.lowering.asIr
import io.github.recrafter.lapis.layers.lowering.asIrTypeName
import io.github.recrafter.lapis.layers.lowering.models.*
import io.github.recrafter.lapis.layers.lowering.types.IrLambdaTypeName
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName
import io.github.recrafter.lapis.layers.lowering.types.IrTypeVariableName
import io.github.recrafter.lapis.layers.lowering.types.orVoid
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

sealed class DescBuiltin<D : IrDesc, W : IrDescWrapper>(
    val name: String,
    val builtin: Builtin,
) {
    fun generate(typer: BuiltinTyper): KPClass =
        buildKotlinInterface(name) {
            setModifiers(IrModifier.PUBLIC)
            setVariableTypes(IrTypeVariableName.of("D", typer(builtin).parameterizedBy(KPStar.asIr())))
        }

    abstract fun generateWrapper(dest: KPFileBuilder, wrapper: W, typer: BuiltinTyper)

    data object FieldGet : DescBuiltin<IrFieldDesc, IrDescFieldGetWrapper>("FieldGet", Builtin.Field) {

        override fun generateWrapper(dest: KPFileBuilder, wrapper: IrDescFieldGetWrapper, typer: BuiltinTyper) {
            val receiverParameter = wrapper.receiverTypeName?.let {
                IrParameter("receiver".withInternalPrefix(), it, listOf(IrModifier.PUBLIC))
            }
            val operationParameter = IrParameter(
                "operation".withInternalPrefix(),
                Operation::class.asIr().parameterizedBy(wrapper.fieldTypeName),
                listOf(IrModifier.PUBLIC)
            )
            dest.addType(buildKotlinClass(wrapper.className.simpleName) {
                setConstructor(listOfNotNull(receiverParameter, operationParameter))
                addSuperInterface(wrapper.superClassTypeName)
            })
            val getReceiverFunction = receiverParameter?.let {
                buildKotlinFunction("getReceiver", jvmNamespace = wrapper.className) {
                    setModifiers(IrModifier.INLINE)
                    setReceiverType(wrapper.superClassTypeName)
                    setReturnType(receiverParameter.typeName)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(wrapper.className)
                            arg(receiverParameter)
                        }
                    }
                }
            }
            getReceiverFunction?.let { dest.addFunction(it) }

            dest.addFunction(buildKotlinFunction("invoke", jvmNamespace = wrapper.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(wrapper.superClassTypeName)
                val receiverParameter = getReceiverFunction?.let {
                    buildKotlinParameter("receiver", wrapper.receiverTypeName) {
                        defaultValue(buildKotlinCodeBlock("%N()") {
                            arg(getReceiverFunction)
                        })
                    }
                }
                receiverParameter?.let { addParameter(it) }
                setReturnType(wrapper.fieldTypeName)
                setBody {
                    return_(buildString {
                        append("(this as %T).%N.%L(")
                        if (receiverParameter != null) {
                            append("%N")
                        }
                        append(")")
                    }) {
                        arg(wrapper.className)
                        arg(operationParameter)
                        arg(Operation<*>::call)
                        receiverParameter?.let { arg(it) }
                    }
                }
            })
        }
    }

    data object FieldSet : DescBuiltin<IrFieldDesc, IrDescFieldSetWrapper>("FieldSet", Builtin.Field) {

        override fun generateWrapper(dest: KPFileBuilder, wrapper: IrDescFieldSetWrapper, typer: BuiltinTyper) {
            val receiverParameter = wrapper.receiverTypeName?.let {
                IrParameter(
                    "receiver".withInternalPrefix(),
                    it,
                    listOf(IrModifier.PUBLIC)
                )
            }
            val valueParameter = IrParameter(
                "value".withInternalPrefix(),
                wrapper.fieldTypeName,
                listOf(IrModifier.PUBLIC)
            )
            val operationParameter = IrParameter(
                "operation".withInternalPrefix(),
                Operation::class.asIr().parameterizedBy(IrTypeName.VOID),
                listOf(IrModifier.PUBLIC)
            )
            dest.addType(buildKotlinClass(wrapper.className.simpleName) {
                setConstructor(listOfNotNull(receiverParameter, valueParameter, operationParameter))
                addSuperInterface(wrapper.superClassTypeName)
            })
            val valueProperty = buildKotlinProperty("value", wrapper.fieldTypeName, jvmNamespace = wrapper.className) {
                setReceiverType(wrapper.superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(wrapper.className)
                            arg(valueParameter)
                        }
                    }
                }
            }
            dest.addProperty(valueProperty)

            val getReceiverFunction = receiverParameter?.let {
                buildKotlinFunction("getReceiver", jvmNamespace = wrapper.className) {
                    setModifiers(IrModifier.INLINE)
                    setReceiverType(wrapper.superClassTypeName)
                    setReturnType(receiverParameter.typeName)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(wrapper.className)
                            arg(receiverParameter)
                        }
                    }
                }
            }
            getReceiverFunction?.let { dest.addFunction(it) }

            dest.addFunction(buildKotlinFunction("invoke", jvmNamespace = wrapper.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(wrapper.superClassTypeName)
                val valueParameter = buildKotlinParameter("value", wrapper.fieldTypeName) {
                    defaultValue(buildKotlinCodeBlock("this.%N") {
                        arg(valueProperty)
                    })
                }
                addParameter(valueParameter)

                val receiverParameter = getReceiverFunction?.let {
                    buildKotlinParameter("receiver", wrapper.receiverTypeName) {
                        defaultValue(buildKotlinCodeBlock("%N()") {
                            arg(getReceiverFunction)
                        })
                    }
                }?.also { addParameter(it) }
                setBody {
                    code_(buildString {
                        append("(this as %T).%N.%L(")
                        if (receiverParameter != null) {
                            append("%N, ")
                        }
                        append("%N)")
                    }) {
                        arg(wrapper.className)
                        arg(operationParameter)
                        arg(Operation<*>::call)
                        receiverParameter?.let { arg(it) }
                        arg(valueParameter)
                    }
                }
            })
        }
    }

    data object ArrayGet : DescBuiltin<IrFieldDesc, IrDescArrayGetWrapper>("ArrayGet", Builtin.Field) {

        override fun generateWrapper(dest: KPFileBuilder, wrapper: IrDescArrayGetWrapper, typer: BuiltinTyper) {
            val arrayParameter = IrParameter(
                "array".withInternalPrefix(),
                wrapper.arrayTypeName,
                listOf(IrModifier.PUBLIC)
            )
            val indexParameter = IrParameter(
                "index".withInternalPrefix(),
                KPInt.asIrTypeName(),
                listOf(IrModifier.PUBLIC)
            )
            dest.addType(buildKotlinClass(wrapper.className.simpleName) {
                setConstructor(listOf(arrayParameter, indexParameter))
                addSuperInterface(wrapper.superClassTypeName)
            })
            val arrayProperty = buildKotlinProperty(
                "array", arrayParameter.typeName, jvmNamespace = wrapper.className
            ) {
                setReceiverType(wrapper.superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(wrapper.className)
                            arg(arrayParameter)
                        }
                    }
                }
            }
            dest.addProperty(arrayProperty)

            val indexProperty = buildKotlinProperty(
                "index", indexParameter.typeName, jvmNamespace = wrapper.className
            ) {
                setReceiverType(wrapper.superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(wrapper.className)
                            arg(indexParameter)
                        }
                    }
                }
            }
            dest.addProperty(indexProperty)

            dest.addFunction(buildKotlinFunction("invoke", jvmNamespace = wrapper.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(wrapper.superClassTypeName)
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
                setReturnType(wrapper.arrayComponentTypeName)
                setBody {
                    return_("%N[%N]") {
                        arg(arrayParameter)
                        arg(indexParameter)
                    }
                }
            })
        }
    }

    data object ArraySet : DescBuiltin<IrFieldDesc, IrDescArraySetWrapper>("ArraySet", Builtin.Field) {

        override fun generateWrapper(dest: KPFileBuilder, wrapper: IrDescArraySetWrapper, typer: BuiltinTyper) {
            val arrayParameter = IrParameter(
                "array".withInternalPrefix(),
                wrapper.arrayTypeName,
                listOf(IrModifier.PUBLIC)
            )
            val indexParameter = IrParameter(
                "index".withInternalPrefix(),
                KPInt.asIrTypeName(),
                listOf(IrModifier.PUBLIC)
            )
            val valueParameter = IrParameter(
                "value".withInternalPrefix(),
                wrapper.arrayComponentTypeName,
                listOf(IrModifier.PUBLIC)
            )
            dest.addType(buildKotlinClass(wrapper.className.simpleName) {
                setConstructor(listOf(arrayParameter, indexParameter, valueParameter))
                addSuperInterface(wrapper.superClassTypeName)
            })
            val arrayProperty = buildKotlinProperty(
                "array", arrayParameter.typeName, jvmNamespace = wrapper.className
            ) {
                setReceiverType(wrapper.superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(wrapper.className)
                            arg(arrayParameter)
                        }
                    }
                }
            }
            dest.addProperty(arrayProperty)

            val indexProperty = buildKotlinProperty(
                "index", indexParameter.typeName, jvmNamespace = wrapper.className
            ) {
                setReceiverType(wrapper.superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(wrapper.className)
                            arg(indexParameter)
                        }
                    }
                }
            }
            dest.addProperty(indexProperty)

            val valueProperty = buildKotlinProperty(
                "value", valueParameter.typeName, jvmNamespace = wrapper.className
            ) {
                setReceiverType(wrapper.superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(wrapper.className)
                            arg(valueParameter)
                        }
                    }
                }
            }
            dest.addProperty(valueProperty)

            dest.addFunction(buildKotlinFunction("invoke", jvmNamespace = wrapper.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(wrapper.superClassTypeName)
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
                        arg(indexProperty)
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

    data object Body : DescBuiltin<IrInvokableDesc, IrDescBodyWrapper>("Body", Builtin.Method) {

        override fun generateWrapper(dest: KPFileBuilder, wrapper: IrDescBodyWrapper, typer: BuiltinTyper) {
            val namedParameters = wrapper.parameters.mapNotNull { parameter ->
                parameter.name?.let { IrParameter(parameter.name, parameter.typeName) }
            }
            val argumentParameters = wrapper.parameters.mapIndexed { index, parameter ->
                val name = parameter.name ?: index.toString()
                IrParameter(
                    name.withInternalPrefix(ARGUMENT),
                    parameter.typeName,
                    listOf(IrModifier.PUBLIC)
                )
            }
            val operationParameter = IrParameter(
                "operation".withInternalPrefix(),
                Operation::class.asIr().parameterizedBy(wrapper.returnTypeName.orVoid()),
                listOf(IrModifier.PUBLIC)
            )
            dest.addType(buildKotlinClass(wrapper.className.simpleName) {
                setConstructor(argumentParameters + operationParameter)
                addSuperInterface(wrapper.superClassTypeName)
            })
            dest.addProperties(namedParameters.map { parameter ->
                buildKotlinProperty(parameter.name, parameter.typeName, jvmNamespace = wrapper.className) {
                    setReceiverType(wrapper.superClassTypeName)
                    setGetter {
                        setModifiers(IrModifier.INLINE)
                        setBody {
                            return_("(this as %T).%L") {
                                arg(wrapper.className)
                                arg(parameter.name.withInternalPrefix(ARGUMENT))
                            }
                        }
                    }
                }
            })
            dest.addFunction(buildKotlinFunction("invoke", jvmNamespace = wrapper.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(wrapper.superClassTypeName)
                addParameters(namedParameters.map { parameter ->
                    buildKotlinParameter(parameter.name, parameter.typeName) {
                        defaultValue(buildKotlinCodeBlock("this.%N") {
                            arg(parameter)
                        })
                    }
                })
                setReturnType(wrapper.returnTypeName)
                setBody {
                    code_(
                        format = buildString {
                            append("(this as %T).%N.%L(")
                            if (namedParameters.isNotEmpty()) {
                                append(namedParameters.joinToString { "%N" })
                            }
                            append(")")
                        },
                        isReturn = wrapper.returnTypeName != null
                    ) {
                        arg(wrapper.className)
                        arg(operationParameter)
                        arg(Operation<*>::call)
                        namedParameters.forEach { arg(it) }
                    }
                }
            })
        }
    }

    data object Call : DescBuiltin<IrInvokableDesc, IrDescCallWrapper>("Call", Builtin.Callable) {

        override fun generateWrapper(dest: KPFileBuilder, wrapper: IrDescCallWrapper, typer: BuiltinTyper) {
            val receiverParameter = wrapper.receiverTypeName?.let {
                IrParameter(
                    "receiver".withInternalPrefix(),
                    it,
                    listOf(IrModifier.PUBLIC)
                )
            }
            val namedParameters = wrapper.parameters.mapNotNull { parameter ->
                parameter.name?.let {
                    IrParameter(
                        parameter.name,
                        parameter.typeName,
                        listOf(IrModifier.PUBLIC)
                    )
                }
            }
            val argumentParameters = wrapper.parameters.mapIndexed { index, parameter ->
                val name = parameter.name ?: index.toString()
                IrParameter(
                    name.withInternalPrefix(ARGUMENT),
                    parameter.typeName,
                    listOf(IrModifier.PUBLIC)
                )
            }
            val operationParameter = IrParameter(
                "operation".withInternalPrefix(),
                Operation::class.asIr().parameterizedBy(wrapper.returnTypeName.orVoid())
            )
            dest.addType(buildKotlinClass(wrapper.className.simpleName) {
                setConstructor(listOfNotNull(receiverParameter) + argumentParameters + operationParameter)
                addSuperInterface(wrapper.superClassTypeName)
            })
            dest.addProperties(namedParameters.map { parameter ->
                buildKotlinProperty(parameter.name, parameter.typeName, jvmNamespace = wrapper.className) {
                    setReceiverType(wrapper.superClassTypeName)
                    setGetter {
                        setModifiers(IrModifier.INLINE)
                        setBody {
                            return_("(this as %T).%L") {
                                arg(wrapper.className)
                                arg(parameter.name.withInternalPrefix(ARGUMENT))
                            }
                        }
                    }
                }
            })
            val getReceiverFunction = receiverParameter?.let {
                buildKotlinFunction("getReceiver", jvmNamespace = wrapper.className) {
                    setModifiers(IrModifier.INLINE)
                    setReceiverType(wrapper.superClassTypeName)
                    setReturnType(receiverParameter.typeName)
                    setBody {
                        return_("(this as %T).%N") {
                            arg(wrapper.className)
                            arg(receiverParameter)
                        }
                    }
                }
            }?.also { dest.addFunction(it) }
            val namedArgumentParameters = namedParameters.map { parameter ->
                buildKotlinParameter(parameter) {
                    defaultValue(buildKotlinCodeBlock("this.%N") {
                        arg(parameter)
                    })
                }
            }
            dest.addFunction(buildKotlinFunction("invoke", jvmNamespace = wrapper.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(wrapper.superClassTypeName)
                addParameters(namedArgumentParameters)
                setReturnType(wrapper.returnTypeName)
                setBody {
                    code_(
                        format = buildString {
                            append("(this as %T).%N.%L(")
                            getReceiverFunction?.let { append("%N()") }
                            if (namedParameters.isNotEmpty()) {
                                append(namedParameters.joinToString(prefix = ", ") { "%N" })
                            }
                            append(")")
                        },
                        isReturn = wrapper.returnTypeName != null
                    ) {
                        arg(wrapper.className)
                        arg(operationParameter)
                        arg(Operation<*>::call)
                        getReceiverFunction?.let { arg(it) }
                        namedParameters.forEach { arg(it) }
                    }
                }
            })
            val receiverTypeName = wrapper.receiverTypeName ?: return
            dest.addFunction(buildKotlinFunction("call", jvmNamespace = wrapper.className) {
                setModifiers(IrModifier.INLINE)
                setReceiverType(wrapper.superClassTypeName)
                val receiverParameter = IrParameter("_receiver", receiverTypeName)
                setContextParameters(listOf(receiverParameter))
                addParameters(namedArgumentParameters)
                setReturnType(wrapper.returnTypeName)
                setBody {
                    code_(
                        format = buildString {
                            append("(this as %T).%N.%L(")
                            append("%N")
                            if (argumentParameters.isNotEmpty()) {
                                append(argumentParameters.joinToString(prefix = ", ") { "%N" })
                            }
                            append(")")
                        },
                        isReturn = wrapper.returnTypeName != null
                    ) {
                        arg(wrapper.className)
                        arg(operationParameter)
                        arg(Operation<*>::call)
                        arg(receiverParameter)
                        argumentParameters.forEach { arg(it) }
                    }
                }
            })
            dest.addFunction(buildKotlinFunction("withReceiver", jvmNamespace = wrapper.className) { functionName ->
                setModifiers(IrModifier.INLINE)
                setReceiverType(wrapper.superClassTypeName)
                val receiverParameter = buildKotlinParameter("receiver", receiverTypeName)
                addParameter(receiverParameter)
                val blockType = IrLambdaTypeName.of(
                    receiverTypeName = wrapper.superClassTypeName,
                    returnTypeName = wrapper.returnTypeName,
                    contextParameters = listOf(receiverTypeName),
                )
                val blockParameter = buildKotlinParameter("block", blockType)
                addParameter(blockParameter)
                setReturnType(wrapper.returnTypeName)
                setBody {
                    with_(buildKotlinCodeBlock("%N") { arg(receiverParameter) }) {
                        code_(
                            format = "%N(this@%L)",
                            isReturn = wrapper.returnTypeName != null
                        ) {
                            arg(blockParameter)
                            arg(functionName)
                        }
                    }
                }
            })
        }
    }

    data object Cancel : DescBuiltin<IrInvokableDesc, IrDescCancelWrapper>("Cancel", Builtin.Method) {

        override fun generateWrapper(dest: KPFileBuilder, wrapper: IrDescCancelWrapper, typer: BuiltinTyper) {
            val callbackParameter = IrParameter(
                "callback".withInternalPrefix(),
                wrapper.returnTypeName
                    ?.let { CallbackInfoReturnable::class.asIr().parameterizedBy(it) }
                    ?: CallbackInfo::class.asIr(),
                listOf(IrModifier.PUBLIC)
            )
            dest.addType(buildKotlinClass(wrapper.className.simpleName) {
                setConstructor(listOf(callbackParameter))
                addSuperInterface(wrapper.superClassTypeName)
            })
            dest.addFunction(buildKotlinFunction("invoke", jvmNamespace = wrapper.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(wrapper.superClassTypeName)
                setReturnType(KPNothing.asIr())
                val returnValueParameter = wrapper.returnTypeName
                    ?.let { IrParameter("returnValue", it) }
                    ?.also { addParameter(it) }
                setBody {
                    code_("(this as %T)") {
                        arg(wrapper.className)
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
                        returnValueParameter?.let { arg(it) }
                    }
                    throw_("%T") {
                        arg(typer(Builtin.CancelSignal))
                    }
                }
            })
        }
    }

    companion object {
        val entries: Array<DescBuiltin<*, *>> = arrayOf(FieldGet, FieldSet, ArrayGet, ArraySet, Body, Call, Cancel)
    }
}
