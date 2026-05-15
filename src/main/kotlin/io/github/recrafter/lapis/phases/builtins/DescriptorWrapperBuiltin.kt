package io.github.recrafter.lapis.phases.builtins

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import io.github.recrafter.lapis.common.jvmDescriptor
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.withInternalPrefix
import io.github.recrafter.lapis.phases.generator.builders.GenKotlinEntity
import io.github.recrafter.lapis.phases.generator.builders.GenKotlinFunctionEntity
import io.github.recrafter.lapis.phases.generator.builders.GenKotlinPropertyEntity
import io.github.recrafter.lapis.phases.generator.models.GenDescriptorWrapperImplResult
import io.github.recrafter.lapis.phases.generator.models.GenInternalPrefix.ARGUMENT
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
            impl: IrFieldGetDescriptorWrapperImpl,
            superClassTypeName: IrParameterizedTypeName,
            resolveBuiltin: BuiltinResolver,
        ): GenDescriptorWrapperImplResult {
            val extensionPackEntities = mutableListOf<GenKotlinEntity>()
            val receiverConstructorParameter = impl.receiverTypeName?.let {
                IrParameter("receiver".withInternalPrefix(), it)
            }
            val operationConstructorParameter = IrParameter(
                "operation".withInternalPrefix(),
                Operation::class.asIrParameterizedTypeName(impl.fieldTypeName),
            )
            val getReceiverFunction = receiverConstructorParameter?.let {
                buildKotlinFunction("getReceiver", jvmNamespace = impl.className) {
                    addModifiers(KPModifier.INLINE)
                    setReceiverType(superClassTypeName)
                    setReturnType(receiverConstructorParameter.typeName)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +receiverConstructorParameter }
                    }
                }.let(::GenKotlinFunctionEntity)
            }?.also { extensionPackEntities += it }
            buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                addModifiers(KPModifier.INLINE, KPModifier.OPERATOR)
                setReceiverType(superClassTypeName)
                val receiverParameter = getReceiverFunction?.let {
                    buildKotlinParameter("receiver", impl.receiverTypeName) {
                        setDefaultValue(getReceiverFunction.callFormat) { getReceiverFunction() }
                    }
                }?.also(::addParameter)
                setReturnType(impl.fieldTypeName)
                setBody {
                    val callArguments = listOfNotNull(receiverParameter)
                    return_("(this as %T).%N.%L(${callArguments.format})") {
                        +impl.className; +operationConstructorParameter
                        +Operation<*>::call; callArguments.forEach { +it }
                    }
                }
            }.also { extensionPackEntities += GenKotlinFunctionEntity(it) }
            return GenDescriptorWrapperImplResult(
                constructorParameters = listOfNotNull(receiverConstructorParameter, operationConstructorParameter),
                extensionPackEntities = extensionPackEntities,
            )
        }
    }

    data object FieldSet : DescriptorWrapperBuiltin<IrFieldSetDescriptorWrapperImpl>("FieldSet", SimpleBuiltin.Field) {
        override fun generateImpl(
            impl: IrFieldSetDescriptorWrapperImpl,
            superClassTypeName: IrParameterizedTypeName,
            resolveBuiltin: BuiltinResolver,
        ): GenDescriptorWrapperImplResult {
            val extensionPackEntities = mutableListOf<GenKotlinEntity>()
            val receiverConstructorParameter = impl.receiverTypeName?.let {
                IrParameter("receiver".withInternalPrefix(), it)
            }
            val valueConstructorParameter = IrParameter("value".withInternalPrefix(), impl.fieldTypeName)
            val operationConstructorParameter = IrParameter(
                "operation".withInternalPrefix(),
                Operation::class.asIrParameterizedTypeName(IrTypeName.VOID),
            )
            val valueProperty = buildKotlinProperty("value", impl.fieldTypeName, jvmNamespace = impl.className) {
                setReceiverType(superClassTypeName)
                setGetter {
                    addModifiers(KPModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +valueConstructorParameter }
                    }
                }
            }.also { extensionPackEntities += GenKotlinPropertyEntity(it) }
            val getReceiverFunction = receiverConstructorParameter?.let {
                buildKotlinFunction("getReceiver", jvmNamespace = impl.className) {
                    addModifiers(KPModifier.INLINE)
                    setReceiverType(superClassTypeName)
                    setReturnType(receiverConstructorParameter.typeName)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +receiverConstructorParameter }
                    }
                }.let(::GenKotlinFunctionEntity)
            }?.also { extensionPackEntities += it }
            buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                addModifiers(KPModifier.INLINE, KPModifier.OPERATOR)
                setReceiverType(superClassTypeName)
                val valueParameter = buildKotlinParameter("value", impl.fieldTypeName) {
                    setDefaultValue("this.%N") { +valueProperty }
                }.also(::addParameter)
                val receiverParameter = getReceiverFunction?.let {
                    buildKotlinParameter("receiver", impl.receiverTypeName) {
                        setDefaultValue(getReceiverFunction.callFormat) { getReceiverFunction() }
                    }
                }?.also(::addParameter)
                setBody {
                    val callArguments = listOfNotNull(receiverParameter, valueParameter)
                    code_("(this as %T).%N.%L(${callArguments.format})") {
                        +impl.className; +operationConstructorParameter
                        +Operation<*>::call; callArguments.forEach { +it }
                    }
                }
            }.also { extensionPackEntities += GenKotlinFunctionEntity(it) }
            return GenDescriptorWrapperImplResult(
                constructorParameters = listOfNotNull(
                    receiverConstructorParameter, valueConstructorParameter, operationConstructorParameter
                ),
                extensionPackEntities = extensionPackEntities,
            )
        }
    }

    data object ArrayGet : DescriptorWrapperBuiltin<IrArrayGetDescriptorWrapperImpl>("ArrayGet", SimpleBuiltin.Field) {
        override fun generateImpl(
            impl: IrArrayGetDescriptorWrapperImpl,
            superClassTypeName: IrParameterizedTypeName,
            resolveBuiltin: BuiltinResolver,
        ): GenDescriptorWrapperImplResult {
            val extensionPackEntities = mutableListOf<GenKotlinEntity>()
            val arrayConstructorParameter = IrParameter("array".withInternalPrefix(), impl.typeName)
            val indexConstructorParameter = IrParameter("index".withInternalPrefix(), KPInt.asIrTypeName())
            val arrayProperty = buildKotlinProperty(
                "array", arrayConstructorParameter.typeName, jvmNamespace = impl.className
            ) {
                setReceiverType(superClassTypeName)
                setGetter {
                    addModifiers(KPModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +arrayConstructorParameter }
                    }
                }
            }.also { extensionPackEntities += GenKotlinPropertyEntity(it) }
            val indexProperty = buildKotlinProperty(
                "index", indexConstructorParameter.typeName, jvmNamespace = impl.className
            ) {
                setReceiverType(superClassTypeName)
                setGetter {
                    addModifiers(KPModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +indexConstructorParameter }
                    }
                }
            }.also { extensionPackEntities += GenKotlinPropertyEntity(it) }
            buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                addModifiers(KPModifier.INLINE, KPModifier.OPERATOR)
                setReceiverType(superClassTypeName)
                val arrayParameter = buildKotlinParameter("array", arrayConstructorParameter.typeName) {
                    setDefaultValue("this.%N") { +arrayProperty }
                }
                val indexParameter = buildKotlinParameter("index", indexConstructorParameter.typeName) {
                    setDefaultValue("this.%N") { +indexProperty }
                }
                addParameters(listOf(arrayParameter, indexParameter))
                setReturnType(impl.componentTypeName)
                setBody {
                    return_("%N[%N]") { +arrayParameter; +indexParameter }
                }
            }.also { extensionPackEntities += GenKotlinFunctionEntity(it) }
            return GenDescriptorWrapperImplResult(
                constructorParameters = listOf(arrayConstructorParameter, indexConstructorParameter),
                extensionPackEntities = extensionPackEntities,
            )
        }
    }

    data object ArraySet : DescriptorWrapperBuiltin<IrArraySetDescriptorWrapperImpl>("ArraySet", SimpleBuiltin.Field) {
        override fun generateImpl(
            impl: IrArraySetDescriptorWrapperImpl,
            superClassTypeName: IrParameterizedTypeName,
            resolveBuiltin: BuiltinResolver,
        ): GenDescriptorWrapperImplResult {
            val extensionPackEntities = mutableListOf<GenKotlinEntity>()
            val arrayConstructorParameter = IrParameter("array".withInternalPrefix(), impl.typeName)
            val indexConstructorParameter = IrParameter("index".withInternalPrefix(), KPInt.asIrTypeName())
            val valueConstructorParameter = IrParameter("value".withInternalPrefix(), impl.componentTypeName)
            val arrayProperty = buildKotlinProperty(
                "array", arrayConstructorParameter.typeName, jvmNamespace = impl.className
            ) {
                setReceiverType(superClassTypeName)
                setGetter {
                    addModifiers(KPModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +arrayConstructorParameter }
                    }
                }
            }.also { extensionPackEntities += GenKotlinPropertyEntity(it) }
            val indexProperty = buildKotlinProperty(
                "index", indexConstructorParameter.typeName, jvmNamespace = impl.className
            ) {
                setReceiverType(superClassTypeName)
                setGetter {
                    addModifiers(KPModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +indexConstructorParameter }
                    }
                }
            }.also { extensionPackEntities += GenKotlinPropertyEntity(it) }
            val valueProperty = buildKotlinProperty(
                "value", valueConstructorParameter.typeName, jvmNamespace = impl.className
            ) {
                setReceiverType(superClassTypeName)
                setGetter {
                    addModifiers(KPModifier.INLINE)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +valueConstructorParameter }
                    }
                }
            }.also { extensionPackEntities += GenKotlinPropertyEntity(it) }
            buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                addModifiers(KPModifier.INLINE, KPModifier.OPERATOR)
                setReceiverType(superClassTypeName)
                val arrayParameter = buildKotlinParameter(arrayProperty.name, arrayConstructorParameter.typeName) {
                    setDefaultValue("this.%N") { +arrayProperty }
                }
                val indexParameter = buildKotlinParameter(indexProperty.name, indexConstructorParameter.typeName) {
                    setDefaultValue("this.%N") { +indexProperty }
                }
                val valueParameter = buildKotlinParameter(valueProperty.name, valueConstructorParameter.typeName) {
                    setDefaultValue("this.%N") { +valueProperty }
                }
                addParameters(listOf(arrayParameter, indexParameter, valueParameter))
                setBody {
                    code_("%N[%N] = %N") { +arrayParameter; +indexParameter; +valueParameter }
                }
            }.also { extensionPackEntities += GenKotlinFunctionEntity(it) }
            return GenDescriptorWrapperImplResult(
                constructorParameters = listOf(
                    arrayConstructorParameter, indexConstructorParameter, valueConstructorParameter,
                ),
                extensionPackEntities = extensionPackEntities,
            )
        }
    }

    data object Body : DescriptorWrapperBuiltin<IrBodyDescriptorWrapperImpl>("Body", SimpleBuiltin.Method) {
        override fun generateImpl(
            impl: IrBodyDescriptorWrapperImpl,
            superClassTypeName: IrParameterizedTypeName,
            resolveBuiltin: BuiltinResolver,
        ): GenDescriptorWrapperImplResult {
            val extensionPackEntities = mutableListOf<GenKotlinEntity>()
            val parameterSuites = impl.resolveParameterSuites()
            val operationConstructorParameter = IrParameter(
                "operation".withInternalPrefix(),
                Operation::class.asIrParameterizedTypeName(impl.returnTypeName.orVoid()),
            )
            extensionPackEntities += parameterSuites.mapNotNull { it.sharedProperty }.map { property ->
                buildKotlinProperty(property.name, property.typeName, jvmNamespace = impl.className) {
                    setReceiverType(superClassTypeName)
                    setGetter {
                        addModifiers(KPModifier.INLINE)
                        setBody {
                            return_("(this as %T).%L") { +impl.className; +property.name.withInternalPrefix(ARGUMENT) }
                        }
                    }
                }.let(::GenKotlinPropertyEntity)
            }
            buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                addModifiers(KPModifier.INLINE, KPModifier.OPERATOR)
                setReceiverType(superClassTypeName)
                addParameters(parameterSuites.mapNotNull { it.invokeParameter })
                setReturnType(impl.returnTypeName)
                setBody {
                    val callArguments = parameterSuites.map { it.callArgument }
                    code_("(this as %T).%N.%L(${callArguments.format})", isReturn = impl.isReturn) {
                        +impl.className; +operationConstructorParameter; +Operation<*>::call
                        callArguments.forEach { +it }
                    }
                }
            }.also { extensionPackEntities += GenKotlinFunctionEntity(it) }
            return GenDescriptorWrapperImplResult(
                constructorParameters = parameterSuites.map { it.constructorParameter } + operationConstructorParameter,
                extensionPackEntities = extensionPackEntities,
            )
        }
    }

    data object Call : DescriptorWrapperBuiltin<IrCallDescriptorWrapperImpl>("Call", SimpleBuiltin.Callable) {
        override fun generateImpl(
            impl: IrCallDescriptorWrapperImpl,
            superClassTypeName: IrParameterizedTypeName,
            resolveBuiltin: BuiltinResolver,
        ): GenDescriptorWrapperImplResult {
            val extensionPackEntities = mutableListOf<GenKotlinEntity>()
            val receiverConstructorParameter = impl.receiverTypeName?.let {
                IrParameter("receiver".withInternalPrefix(), it)
            }
            val parameterSuites = impl.resolveParameterSuites()
            val operationConstructorParameter = IrParameter(
                "operation".withInternalPrefix(),
                Operation::class.asIrParameterizedTypeName(impl.returnTypeName.orVoid()),
            )
            extensionPackEntities += parameterSuites.mapNotNull { it.sharedProperty }.map { property ->
                buildKotlinProperty(property.name, property.typeName, jvmNamespace = impl.className) {
                    setReceiverType(superClassTypeName)
                    setGetter {
                        addModifiers(KPModifier.INLINE)
                        setBody {
                            return_("(this as %T).%L") { +impl.className; +property.name.withInternalPrefix(ARGUMENT) }
                        }
                    }
                }.let(::GenKotlinPropertyEntity)
            }
            val getReceiverFunction = receiverConstructorParameter?.let {
                buildKotlinFunction("getReceiver", jvmNamespace = impl.className) {
                    addModifiers(KPModifier.INLINE)
                    setReceiverType(superClassTypeName)
                    setReturnType(receiverConstructorParameter.typeName)
                    setBody {
                        return_("(this as %T).%N") { +impl.className; +receiverConstructorParameter }
                    }
                }.also { extensionPackEntities += GenKotlinFunctionEntity(it) }
            }
            val invokeParameters = parameterSuites.mapNotNull { it.invokeParameter }
            val callArguments = parameterSuites.map { it.callArgument }
            buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                addModifiers(KPModifier.INLINE, KPModifier.OPERATOR)
                setReceiverType(superClassTypeName)
                addParameters(invokeParameters)
                setReturnType(impl.returnTypeName)
                setBody {
                    val receiverFunctionFormat = getReceiverFunction?.let { "%N()" }
                    val callArgumentsFormat = if (callArguments.isNotEmpty()) {
                        ", " + callArguments.format
                    } else ""
                    code_("(this as %T).%N.%L($receiverFunctionFormat$callArgumentsFormat)", isReturn = impl.isReturn) {
                        +impl.className; +operationConstructorParameter; +Operation<*>::call
                        getReceiverFunction?.let { +it }
                        callArguments.forEach { +it }
                    }
                }
            }.also { extensionPackEntities += GenKotlinFunctionEntity(it) }
            if (impl.receiverTypeName != null) {
                buildKotlinFunction("call", jvmNamespace = impl.className) {
                    addModifiers(KPModifier.INLINE)
                    val receiverParameter = IrParameter("_receiver", impl.receiverTypeName)
                    setContextParameters(listOf(receiverParameter))
                    setReceiverType(superClassTypeName)
                    addParameters(invokeParameters)
                    setReturnType(impl.returnTypeName)
                    setBody {
                        val callArgumentsFormat = if (callArguments.isNotEmpty()) {
                            ", " + callArguments.format
                        } else ""
                        code_("(this as %T).%N.%L(%N$callArgumentsFormat)", isReturn = impl.isReturn) {
                            +impl.className; +operationConstructorParameter; +Operation<*>::call; +receiverParameter
                            callArguments.forEach { +it }
                        }
                    }
                }.also { extensionPackEntities += GenKotlinFunctionEntity(it) }
            }
            return GenDescriptorWrapperImplResult(
                constructorParameters = listOfNotNull(receiverConstructorParameter) +
                    parameterSuites.map { it.constructorParameter } +
                    operationConstructorParameter,
                extensionPackEntities = extensionPackEntities,
            )
        }
    }

    data object Cancel : DescriptorWrapperBuiltin<IrCancelDescriptorWrapperImpl>("Cancel", SimpleBuiltin.Method) {
        override fun generateImpl(
            impl: IrCancelDescriptorWrapperImpl,
            superClassTypeName: IrParameterizedTypeName,
            resolveBuiltin: BuiltinResolver,
        ): GenDescriptorWrapperImplResult {
            val extensionPackEntities = mutableListOf<GenKotlinEntity>()
            val (callbackTypeName, checkCallable, cancelCallable) = if (impl.returnTypeName != null) {
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
            val callbackConstructorParameter = IrParameter("callback".withInternalPrefix(), callbackTypeName)
            buildKotlinProperty("isCanceled", KPBoolean.asIrTypeName(), jvmNamespace = impl.className) {
                setReceiverType(superClassTypeName)
                setGetter {
                    addModifiers(KPModifier.INLINE)
                    setBody {
                        val notNullCheckFormat = if (impl.isReturn) " != null" else ""
                        return_("(this as %T).%N.%L()$notNullCheckFormat") {
                            +impl.className; +callbackConstructorParameter; +checkCallable
                        }
                    }
                }
            }.also { extensionPackEntities += GenKotlinPropertyEntity(it) }
            impl.returnTypeName?.let { returnTypeName ->
                val primitiveJvmName = returnTypeName.jvmDescriptor.getPrimitiveName(allowVoid = false)
                val type = if (primitiveJvmName != null) returnTypeName else returnTypeName.makeNullable()
                buildKotlinProperty("returnValue", type, jvmNamespace = impl.className) {
                    setReceiverType(superClassTypeName)
                    setGetter {
                        addModifiers(KPModifier.INLINE)
                        setBody {
                            val callableName = primitiveJvmName?.let { checkCallable.name + it } ?: checkCallable.name
                            return_("(this as %T).%N.%L()") {
                                +impl.className; +callbackConstructorParameter; +callableName
                            }
                        }
                    }
                }.also { extensionPackEntities += GenKotlinPropertyEntity(it) }
            }
            buildKotlinFunction("invoke", jvmNamespace = impl.className) {
                addModifiers(KPModifier.INLINE, KPModifier.OPERATOR)
                setReceiverType(superClassTypeName)
                setReturnType(KPNothing.asIrClassName())
                val returnValueParameter = impl.returnTypeName
                    ?.let { IrParameter("returnValue", it) }
                    ?.also(::addParameter)
                setBody {
                    val cancelArguments = listOfNotNull(returnValueParameter)
                    code_("(this as %T).%N.%L(${cancelArguments.format})") {
                        +impl.className; +callbackConstructorParameter
                        +cancelCallable; cancelArguments.forEach { +it }
                    }
                    throw_("%T") { +resolveBuiltin(SimpleBuiltin.CancelSignal) }
                }
            }.also { extensionPackEntities += GenKotlinFunctionEntity(it) }
            return GenDescriptorWrapperImplResult(
                constructorParameters = listOf(callbackConstructorParameter),
                extensionPackEntities = extensionPackEntities,
            )
        }
    }

    override val isInternal: Boolean = false

    override fun generate(resolveBuiltin: BuiltinResolver): KPClass =
        buildKotlinInterface(name) {
            setVariableTypes(IrTypeVariableName.of("D", resolveBuiltin(builtin).parameterizedByStar()))
        }

    abstract fun generateImpl(
        impl: T,
        superClassTypeName: IrParameterizedTypeName,
        resolveBuiltin: BuiltinResolver,
    ): GenDescriptorWrapperImplResult

    companion object {
        val entries: List<DescriptorWrapperBuiltin<*>> =
            listOf(FieldGet, FieldSet, ArrayGet, ArraySet, Body, Call, Cancel)
    }
}

private class GenInvokableDescriptorWrapperParameterSuite(
    val constructorParameter: IrParameter,
    val sharedProperty: IrParameter?,
) {
    val invokeParameter: KPParameter? = sharedProperty?.let {
        buildKotlinParameter(sharedProperty) {
            setDefaultValue("this.%N") { +sharedProperty }
        }
    }
    val callArgument: IrParameter = sharedProperty ?: constructorParameter
}

private fun IrInvokableDescriptorWrapperImpl.resolveParameterSuites():
    List<GenInvokableDescriptorWrapperParameterSuite> =
    parameters.mapIndexed { index, parameter ->
        GenInvokableDescriptorWrapperParameterSuite(
            constructorParameter = IrParameter(
                (parameter.name ?: index.toString()).withInternalPrefix(ARGUMENT),
                parameter.typeName,
            ),
            sharedProperty = parameter.name?.let { IrParameter(it, parameter.typeName) },
        )
    }
