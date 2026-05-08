package io.github.recrafter.lapis.phases.builtins

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import io.github.recrafter.lapis.extensions.InternalPrefix.ARGUMENT
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.withInternalPrefix
import io.github.recrafter.lapis.phases.common.jvmDescriptor
import io.github.recrafter.lapis.phases.lowering.IrModifier
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.asIrParameterizedTypeName
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.models.*
import io.github.recrafter.lapis.phases.lowering.types.IrParameterizedTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeVariableName
import io.github.recrafter.lapis.phases.lowering.types.orVoid
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

sealed class DescriptorWrapperBuiltin<T : IrDescriptorWrapperImpl<T>>(
    override val name: String,
    val builtin: SimpleBuiltin,
) : Builtin<KPClass> {

    data object FieldGet : DescriptorWrapperBuiltin<IrFieldGetDescriptorWrapperImpl>("FieldGet", SimpleBuiltin.Field) {
        override fun generateImpl(
            destination: KPFileBuilder,
            impl: IrFieldGetDescriptorWrapperImpl,
            superClassTypeName: IrParameterizedTypeName,
            resolveBuiltin: BuiltinResolver,
        ) {
            val receiverParameter = impl.receiverTypeName?.let {
                IrParameter("receiver".withInternalPrefix(), it, IrModifier.PUBLIC)
            }
            val operationParameter = IrParameter(
                "operation".withInternalPrefix(),
                Operation::class.asIrParameterizedTypeName(impl.fieldTypeName),
                IrModifier.PUBLIC
            )
            destination.addType(buildKotlinClass(impl.className.simpleName) {
                setConstructor(listOfNotNull(receiverParameter, operationParameter))
                addSuperInterface(superClassTypeName)
            })
            val getReceiverFunction = receiverParameter?.let {
                buildKotlinFunction("getReceiver", jvmNamespace = impl.className) {
                    setModifiers(IrModifier.INLINE)
                    setReceiverType(superClassTypeName)
                    setReturnType(receiverParameter.typeName)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +receiverParameter }
                    }
                }
            }?.also(destination::addFunction)
            destination.addFunction(buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(superClassTypeName)
                val receiverParameter = getReceiverFunction?.let {
                    buildKotlinParameter("receiver", impl.receiverTypeName) {
                        setDefaultValue("%N()") { +getReceiverFunction }
                    }
                }
                receiverParameter?.let(::addParameter)
                setReturnType(impl.fieldTypeName)
                setBody {
                    return_(
                        buildString {
                            append("(this as %T).%N.%L(")
                            receiverParameter?.let { append("%N") }
                            append(")")
                        }
                    ) {
                        +impl.className; +operationParameter; +Operation<*>::call
                        receiverParameter?.let { +it }
                    }
                }
            })
        }
    }

    data object FieldSet : DescriptorWrapperBuiltin<IrFieldSetDescriptorWrapperImpl>("FieldSet", SimpleBuiltin.Field) {
        override fun generateImpl(
            destination: KPFileBuilder,
            impl: IrFieldSetDescriptorWrapperImpl,
            superClassTypeName: IrParameterizedTypeName,
            resolveBuiltin: BuiltinResolver,
        ) {
            val receiverParameter = impl.receiverTypeName?.let {
                IrParameter("receiver".withInternalPrefix(), it, IrModifier.PUBLIC)
            }
            val valueParameter = IrParameter("value".withInternalPrefix(), impl.fieldTypeName, IrModifier.PUBLIC)
            val operationParameter = IrParameter(
                "operation".withInternalPrefix(),
                Operation::class.asIrParameterizedTypeName(IrTypeName.VOID),
                IrModifier.PUBLIC
            )
            destination.addType(buildKotlinClass(impl.className.simpleName) {
                setConstructor(listOfNotNull(receiverParameter, valueParameter, operationParameter))
                addSuperInterface(superClassTypeName)
            })
            val valueProperty = buildKotlinProperty("value", impl.fieldTypeName, jvmNamespace = impl.className) {
                setReceiverType(superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +valueParameter }
                    }
                }
            }.also(destination::addProperty)
            val getReceiverFunction = receiverParameter?.let {
                buildKotlinFunction("getReceiver", jvmNamespace = impl.className) {
                    setModifiers(IrModifier.INLINE)
                    setReceiverType(superClassTypeName)
                    setReturnType(receiverParameter.typeName)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +receiverParameter }
                    }
                }
            }?.also(destination::addFunction)
            destination.addFunction(buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(superClassTypeName)
                val valueParameter = buildKotlinParameter("value", impl.fieldTypeName) {
                    setDefaultValue("this.%N") { +valueProperty }
                }.also(::addParameter)
                val receiverParameter = getReceiverFunction?.let {
                    buildKotlinParameter("receiver", impl.receiverTypeName) {
                        setDefaultValue("%N()") { +getReceiverFunction }
                    }
                }?.also(::addParameter)
                setBody {
                    code_(buildString {
                        append("(this as %T).%N.%L(")
                        receiverParameter?.let { append("%N, ") }
                        append("%N)")
                    }) {
                        +impl.className; +operationParameter; +Operation<*>::call
                        receiverParameter?.let { +it }
                        +valueParameter
                    }
                }
            })
        }
    }

    data object ArrayGet : DescriptorWrapperBuiltin<IrArrayGetDescriptorWrapperImpl>("ArrayGet", SimpleBuiltin.Field) {
        override fun generateImpl(
            destination: KPFileBuilder,
            impl: IrArrayGetDescriptorWrapperImpl,
            superClassTypeName: IrParameterizedTypeName,
            resolveBuiltin: BuiltinResolver,
        ) {
            val arrayParameter = IrParameter("array".withInternalPrefix(), impl.typeName, IrModifier.PUBLIC)
            val indexParameter = IrParameter("index".withInternalPrefix(), KPInt.asIrTypeName(), IrModifier.PUBLIC)
            destination.addType(buildKotlinClass(impl.className.simpleName) {
                setConstructor(arrayParameter, indexParameter)
                addSuperInterface(superClassTypeName)
            })
            val arrayProperty = buildKotlinProperty("array", arrayParameter.typeName, jvmNamespace = impl.className) {
                setReceiverType(superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +arrayParameter }
                    }
                }
            }.also(destination::addProperty)
            val indexProperty = buildKotlinProperty("index", indexParameter.typeName, jvmNamespace = impl.className) {
                setReceiverType(superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +indexParameter }
                    }
                }
            }.also(destination::addProperty)
            destination.addFunction(buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(superClassTypeName)
                val arrayParameter = buildKotlinParameter("array", arrayParameter.typeName) {
                    setDefaultValue("this.%N") { +arrayProperty }
                }
                val indexParameter = buildKotlinParameter("index", indexParameter.typeName) {
                    setDefaultValue("this.%N") { +indexProperty }
                }
                addParameters(listOf(arrayParameter, indexParameter))
                setReturnType(impl.componentTypeName)
                setBody {
                    return_("%N[%N]") { +arrayParameter; +indexParameter }
                }
            })
        }
    }

    data object ArraySet : DescriptorWrapperBuiltin<IrArraySetDescriptorWrapperImpl>("ArraySet", SimpleBuiltin.Field) {
        override fun generateImpl(
            destination: KPFileBuilder,
            impl: IrArraySetDescriptorWrapperImpl,
            superClassTypeName: IrParameterizedTypeName,
            resolveBuiltin: BuiltinResolver,
        ) {
            val arrayParameter = IrParameter("array".withInternalPrefix(), impl.typeName, IrModifier.PUBLIC)
            val indexParameter = IrParameter("index".withInternalPrefix(), KPInt.asIrTypeName(), IrModifier.PUBLIC)
            val valueParameter = IrParameter("value".withInternalPrefix(), impl.componentTypeName, IrModifier.PUBLIC)
            destination.addType(buildKotlinClass(impl.className.simpleName) {
                setConstructor(arrayParameter, indexParameter, valueParameter)
                addSuperInterface(superClassTypeName)
            })
            val arrayProperty = buildKotlinProperty("array", arrayParameter.typeName, jvmNamespace = impl.className) {
                setReceiverType(superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +arrayParameter }
                    }
                }
            }.also(destination::addProperty)
            val indexProperty = buildKotlinProperty("index", indexParameter.typeName, jvmNamespace = impl.className) {
                setReceiverType(superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +indexParameter }
                    }
                }
            }.also(destination::addProperty)
            val valueProperty = buildKotlinProperty("value", valueParameter.typeName, jvmNamespace = impl.className) {
                setReceiverType(superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +valueParameter }
                    }
                }
            }.also(destination::addProperty)
            destination.addFunction(buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(superClassTypeName)
                val arrayParameter = buildKotlinParameter(arrayProperty.name, arrayParameter.typeName) {
                    setDefaultValue("this.%N") { +arrayProperty }
                }
                val indexParameter = buildKotlinParameter(indexProperty.name, indexParameter.typeName) {
                    setDefaultValue("this.%N") { +indexProperty }
                }
                val valueParameter = buildKotlinParameter(valueProperty.name, valueParameter.typeName) {
                    setDefaultValue("this.%N") { +valueProperty }
                }
                addParameters(listOf(arrayParameter, indexParameter, valueParameter))
                setBody {
                    code_("%N[%N] = %N") { +arrayParameter; +indexParameter; +valueParameter }
                }
            })
        }
    }

    data object Body : DescriptorWrapperBuiltin<IrBodyDescriptorWrapperImpl>("Body", SimpleBuiltin.Method) {
        override fun generateImpl(
            destination: KPFileBuilder,
            impl: IrBodyDescriptorWrapperImpl,
            superClassTypeName: IrParameterizedTypeName,
            resolveBuiltin: BuiltinResolver,
        ) {
            val suites = impl.resolveParameterSuites()
            val operationParameter = IrParameter(
                "operation".withInternalPrefix(),
                Operation::class.asIrParameterizedTypeName(impl.returnTypeName.orVoid()),
                IrModifier.PUBLIC
            )
            destination.addType(buildKotlinClass(impl.className.simpleName) {
                setConstructor(suites.map { it.constructorParameter } + operationParameter)
                addSuperInterface(superClassTypeName)
            })
            destination.addProperties(suites.mapNotNull { it.property }.map { property ->
                buildKotlinProperty(property.name, property.typeName, jvmNamespace = impl.className) {
                    setReceiverType(superClassTypeName)
                    setGetter {
                        setModifiers(IrModifier.INLINE)
                        setBody {
                            return_("(this as %T).%L") { +impl.className; +property.name.withInternalPrefix(ARGUMENT) }
                        }
                    }
                }
            })
            destination.addFunction(buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(superClassTypeName)
                addParameters(suites.mapNotNull { it.invokeParameter })
                setReturnType(impl.returnTypeName)
                setBody {
                    val callArgumentReferences = suites.map { it.callArgumentReference }
                    code_("(this as %T).%N.%L(${callArgumentReferences.format})", impl.isReturnable) {
                        +impl.className; +operationParameter; +Operation<*>::call
                        callArgumentReferences.forEach { +it }
                    }
                }
            })
        }
    }

    data object Call : DescriptorWrapperBuiltin<IrCallDescriptorWrapperImpl>("Call", SimpleBuiltin.Callable) {
        override fun generateImpl(
            destination: KPFileBuilder,
            impl: IrCallDescriptorWrapperImpl,
            superClassTypeName: IrParameterizedTypeName,
            resolveBuiltin: BuiltinResolver,
        ) {
            val receiverParameter = impl.receiverTypeName?.let {
                IrParameter("receiver".withInternalPrefix(), it, IrModifier.PUBLIC)
            }
            val suites = impl.resolveParameterSuites()
            val operationParameter = IrParameter(
                "operation".withInternalPrefix(),
                Operation::class.asIrParameterizedTypeName(impl.returnTypeName.orVoid()),
                IrModifier.PUBLIC
            )
            destination.addType(buildKotlinClass(impl.className.simpleName) {
                val constructorParameters = suites.map { it.constructorParameter }
                setConstructor(listOfNotNull(receiverParameter) + constructorParameters + operationParameter)
                addSuperInterface(superClassTypeName)
            })
            destination.addProperties(suites.mapNotNull { it.property }.map { property ->
                buildKotlinProperty(property.name, property.typeName, jvmNamespace = impl.className) {
                    setReceiverType(superClassTypeName)
                    setGetter {
                        setModifiers(IrModifier.INLINE)
                        setBody {
                            return_("(this as %T).%L") { +impl.className; +property.name.withInternalPrefix(ARGUMENT) }
                        }
                    }
                }
            })
            val getReceiverFunction = receiverParameter?.let {
                buildKotlinFunction("getReceiver", jvmNamespace = impl.className) {
                    setModifiers(IrModifier.INLINE)
                    setReceiverType(superClassTypeName)
                    setReturnType(receiverParameter.typeName)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +receiverParameter }
                    }
                }
            }?.also(destination::addFunction)
            val invokeParameters = suites.mapNotNull { it.invokeParameter }
            val callArgumentReferences = suites.map { it.callArgumentReference }
            destination.addFunction(buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(superClassTypeName)
                addParameters(invokeParameters)
                setReturnType(impl.returnTypeName)
                setBody {
                    code_(
                        format = buildString {
                            append("(this as %T).%N.%L(")
                            getReceiverFunction?.let { append("%N()") }
                            if (callArgumentReferences.isNotEmpty()) {
                                append(callArgumentReferences.joinToString(prefix = ", ") { "%N" })
                            }
                            append(")")
                        },
                        isReturn = impl.isReturnable
                    ) {
                        +impl.className; +operationParameter; +Operation<*>::call
                        getReceiverFunction?.let { +it }
                        callArgumentReferences.forEach { +it }
                    }
                }
            })
            if (impl.receiverTypeName != null) {
                destination.addFunction(buildKotlinFunction("call", jvmNamespace = impl.className) {
                    setModifiers(IrModifier.INLINE)
                    val receiverParameter = IrParameter("_receiver", impl.receiverTypeName)
                    setContextParameters(listOf(receiverParameter))
                    setReceiverType(superClassTypeName)
                    addParameters(invokeParameters)
                    setReturnType(impl.returnTypeName)
                    setBody {
                        code_(
                            format = buildString {
                                append("(this as %T).%N.%L(%N")
                                if (callArgumentReferences.isNotEmpty()) {
                                    append(callArgumentReferences.joinToString(prefix = ", ") { "%N" })
                                }
                                append(")")
                            },
                            isReturn = impl.isReturnable
                        ) {
                            +impl.className; +operationParameter; +Operation<*>::call; +receiverParameter
                            callArgumentReferences.forEach { +it }
                        }
                    }
                })
            }
        }
    }

    data object Cancel : DescriptorWrapperBuiltin<IrCancelDescriptorWrapperImpl>("Cancel", SimpleBuiltin.Method) {
        override fun generateImpl(
            destination: KPFileBuilder,
            impl: IrCancelDescriptorWrapperImpl,
            superClassTypeName: IrParameterizedTypeName,
            resolveBuiltin: BuiltinResolver,
        ) {
            val (callbackType, callbackCheck, callbackCancel) = if (impl.returnTypeName != null) {
                Triple(
                    CallbackInfoReturnable::class.asIrParameterizedTypeName(impl.returnTypeName),
                    CallbackInfoReturnable<*>::getReturnValue,
                    CallbackInfoReturnable<*>::setReturnValue,
                )
            } else {
                Triple(
                    CallbackInfo::class.asIrTypeName(),
                    CallbackInfo::isCancelled,
                    CallbackInfo::cancel,
                )
            }
            val callbackParameter = IrParameter("callback".withInternalPrefix(), callbackType, IrModifier.PUBLIC)
            destination.addType(buildKotlinClass(impl.className.simpleName) {
                setConstructor(callbackParameter)
                addSuperInterface(superClassTypeName)
            })
            buildKotlinProperty("isCanceled", KPBoolean.asIrTypeName(), jvmNamespace = impl.className) {
                setReceiverType(superClassTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_(
                            buildString {
                                append("(this as %T).%N.%L()")
                                if (impl.isReturnable) append(" != null")
                            }
                        ) { +impl.className; +callbackParameter; +callbackCheck }
                    }
                }
            }.also(destination::addProperty)
            impl.returnTypeName?.let { returnTypeName ->
                val primitiveJvmName = returnTypeName.jvmDescriptor.getPrimitiveName(allowVoid = false)
                val type = if (primitiveJvmName != null) returnTypeName else returnTypeName.makeNullable()
                buildKotlinProperty("returnValue", type, jvmNamespace = impl.className) {
                    setReceiverType(superClassTypeName)
                    setGetter {
                        setModifiers(IrModifier.INLINE)
                        setBody {
                            val callableName = primitiveJvmName?.let { callbackCheck.name + it } ?: callbackCheck.name
                            return_("(this as %T).%N.%L()") {
                                +impl.className; +callbackParameter; +callableName
                            }
                        }
                    }
                }.also(destination::addProperty)
            }
            destination.addFunction(buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(superClassTypeName)
                setReturnType(KPNothing.asIrClassName())
                val returnValueParameter = impl.returnTypeName?.let { IrParameter("returnValue", it) }
                    ?.also(::addParameter)
                setBody {
                    code_("(this as %T)") { +impl.className }
                    code_(buildString {
                        append("%N.%L(")
                        returnValueParameter?.let { append("%N") }
                        append(")")
                    }) {
                        +callbackParameter; +callbackCancel
                        returnValueParameter?.let { +it }
                    }
                    throw_("%T") { +resolveBuiltin(SimpleBuiltin.CancelSignal) }
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

    abstract fun generateImpl(
        destination: KPFileBuilder,
        impl: T,
        superClassTypeName: IrParameterizedTypeName,
        resolveBuiltin: BuiltinResolver,
    )

    companion object {
        val entries: List<DescriptorWrapperBuiltin<*>> =
            listOf(FieldGet, FieldSet, ArrayGet, ArraySet, Body, Call, Cancel)
    }
}

private data class InvokableDescriptorWrapperParameterSuite(
    val constructorParameter: IrParameter,
    val property: IrParameter?,
) {
    val invokeParameter: KPParameter? = property?.let {
        buildKotlinParameter(property) {
            setDefaultValue("this.%N") { +property }
        }
    }
    val callArgumentReference: IrParameter = property ?: constructorParameter
}

private fun IrInvokableDescriptorWrapperImpl.resolveParameterSuites(): List<InvokableDescriptorWrapperParameterSuite> =
    parameters.mapIndexed { index, parameter ->
        InvokableDescriptorWrapperParameterSuite(
            constructorParameter = IrParameter(
                (parameter.name ?: index.toString()).withInternalPrefix(ARGUMENT),
                parameter.typeName,
                IrModifier.PUBLIC
            ),
            property = parameter.name?.let { IrParameter(it, parameter.typeName, IrModifier.PUBLIC) },
        )
    }
