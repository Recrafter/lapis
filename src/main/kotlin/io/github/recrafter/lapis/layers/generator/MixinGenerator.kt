package io.github.recrafter.lapis.layers.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Cancellable
import com.llamalad7.mixinextras.sugar.Local
import io.github.recrafter.lapis.extensions.backticked
import io.github.recrafter.lapis.extensions.capitalize
import io.github.recrafter.lapis.extensions.common.unsafeLazy
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ksp.KspDependencies
import io.github.recrafter.lapis.extensions.ksp.createResourceFile
import io.github.recrafter.lapis.extensions.ksp.flatten
import io.github.recrafter.lapis.layers.ApiYieldSignal
import io.github.recrafter.lapis.layers.RuntimeApi
import io.github.recrafter.lapis.layers.lowering.*
import io.github.recrafter.lapis.layers.lowering.types.orVoid
import io.github.recrafter.lapis.options.Options
import kotlinx.serialization.json.Json
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Mutable
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.gen.Accessor
import org.spongepowered.asm.mixin.gen.Invoker
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import kotlin.reflect.KClass

class MixinGenerator(
    private val options: Options,
    private val runtimeApi: RuntimeApi,
    private val codeGenerator: CodeGenerator,
) {
    private val configJson: Json by unsafeLazy {
        Json { prettyPrint = true }
    }
    private val extensionProperties: MutableList<KPProperty> = mutableListOf()
    private val extensionFunctions: MutableList<KPFunction> = mutableListOf()

    fun generate(descriptors: List<IrDescriptor>, mixins: List<IrMixin>) {
        generateDescriptorImpls(descriptors)
        mixins.forEach { generateRootMixin(it) }

        generateExtensions(mixins.map { it.dependencies }.flatten())
        generateMixinConfig(mixins)
    }

    private fun generateDescriptorImpls(descriptors: List<IrDescriptor>) {
        val callbackParameterName = "callback".withInternalPrefix()
        buildKotlinFile(options.generatedPackageName, "_Descriptors") {
            descriptors.forEach { descriptor ->
                descriptor.contextImpl?.let { contextImpl ->
                    val contextImplName = contextImpl.type.simpleName
                    addType(buildKotlinClass(contextImplName) {
                        setConstructor(
                            buildList {
                                addAll(contextImpl.parameters.map { parameter ->
                                    IrParameter(parameter.name.withInternalPrefix("parameter"), parameter.type)
                                })
                                add(
                                    IrParameter(
                                        callbackParameterName,
                                        contextImpl.returnType
                                            ?.let { CallbackInfoReturnable::class.asIr().parameterizedBy(it) }
                                            ?: CallbackInfo::class.asIr()
                                    )
                                )
                            }
                        )
                        addSuperInterface(contextImpl.superType)
                    })
                    addProperties(contextImpl.parameters.map { parameter ->
                        buildKotlinProperty(parameter.name, parameter.type) {
                            setReceiverType(contextImpl.superType)
                            setGetter {
                                addAnnotation<JvmName> {
                                    setStringMember(
                                        JvmName::name,
                                        "get" + contextImplName.capitalize() + "_" + parameter.name
                                    )
                                }
                                setBody {
                                    return_("(this as %T).%L") {
                                        arg(contextImpl.type)
                                        arg(parameter.name.withInternalPrefix("parameter"))
                                    }
                                }
                            }
                        }
                    })
                    addFunction(buildKotlinFunction("yield") {
                        addAnnotation<JvmName> {
                            setStringMember(
                                JvmName::name,
                                "yield" + contextImplName.capitalize()
                            )
                        }
                        setReceiverType(contextImpl.superType)
                        setReturnType(KPNothing.asIr())
                        val returnValueParameter = contextImpl.returnType?.let {
                            addParameter(IrParameter("returnValue", it))
                        }
                        setBody {
                            line("(this as %T)") {
                                arg(contextImpl.type)
                            }
                            line(buildString {
                                append("%L.%L(")
                                if (returnValueParameter != null) {
                                    append("%N")
                                }
                                append(")")
                            }) {
                                arg(callbackParameterName)
                                arg(
                                    if (returnValueParameter != null) CallbackInfoReturnable<*>::setReturnValue.name
                                    else CallbackInfo::cancel.name
                                )
                                returnValueParameter?.let { arg(it) }
                            }
                            line("throw %T") {
                                arg(runtimeApi[ApiYieldSignal])
                            }
                        }
                    })
                }

                val targetImpl = descriptor.targetImpl
                val targetImplName = targetImpl.type.simpleName
                val operationParameterName = "operation".withInternalPrefix()
                val receiverParameterName = "receiver".withInternalPrefix()
                val invokeWithReceiverParameterName = "invokeWithReceiver".withInternalPrefix()
                addType(buildKotlinClass(targetImplName) {
                    setConstructor(
                        buildList {
                            targetImpl.receiverType?.let {
                                add(IrParameter(receiverParameterName, it))
                                add(IrParameter(invokeWithReceiverParameterName, Boolean::class.asIr()))
                            }
                            addAll(targetImpl.parameters.map { parameter ->
                                IrParameter(parameter.name.withInternalPrefix("argument"), parameter.type)
                            })
                            add(
                                IrParameter(
                                    operationParameterName,
                                    Operation::class.asIr().parameterizedBy(targetImpl.returnType.orVoid())
                                )
                            )
                        }
                    )
                    setSuperClass(targetImpl.superType)
                })
                addProperties(targetImpl.parameters.map { parameter ->
                    buildKotlinProperty(parameter.name, parameter.type) {
                        setReceiverType(targetImpl.superType)
                        setGetter {
                            setBody {
                                return_("(this as %T).%L") {
                                    arg(targetImpl.type)
                                    arg(parameter.name.withInternalPrefix("argument"))
                                }
                            }
                        }
                    }
                })
                targetImpl.receiverType?.let { returnType ->
                    addFunction(buildKotlinFunction("getReceiver") {
                        setReceiverType(targetImpl.superType)
                        setReturnType(returnType)
                        setBody {
                            return_("(this as %T).%L") {
                                arg(targetImpl.type)
                                arg(receiverParameterName)
                            }
                        }
                    })
                }
                addFunction(buildKotlinFunction("invoke") {
                    setReceiverType(targetImpl.superType)
                    targetImpl.parameters.forEach { parameter ->
                        addParameter(buildKotlinParameter(parameter.name, parameter.type) {
                            defaultValue(buildKotlinCodeBlock("this.%L") {
                                arg(parameter.name)
                            })
                        })
                    }
                    setReturnType(targetImpl.returnType)
                    setBody {
                        line("this as %T") {
                            arg(targetImpl.type)
                        }

                        fun callOperation(parameters: List<String>) {
                            val format = "%L.%L(%L)"
                            val args: IrKotlinCodeBlockBuilder.Arguments.() -> Unit = {
                                arg(operationParameterName)
                                arg(Operation<*>::call.name)
                                arg(parameters.joinToString())
                            }
                            if (targetImpl.returnType != null) {
                                return_(format, args)
                            } else {
                                line(format, args)
                            }
                        }

                        val parameters = targetImpl.parameters.map { it.name }
                        if (targetImpl.receiverType != null) {
                            if_(buildKotlinCodeBlock(invokeWithReceiverParameterName)) {
                                callOperation(listOf(receiverParameterName) + parameters)
                                if (targetImpl.returnType == null) {
                                    return_()
                                }
                            }
                        }
                        callOperation(parameters)
                    }
                })
            }
        }.writeTo(codeGenerator, descriptors.map { it.dependencies }.flatten())
    }

    private fun generateRootMixin(mixin: IrMixin) {
        mixin.flattenTree().forEach { mixin ->
            if (mixin.isNotEmpty()) {
                buildKotlinFile(mixin.patchImplType) {
                    addType(buildPatchImplClass(mixin))
                }.writeTo(codeGenerator, mixin.dependencies)

                buildJavaFile(mixin.type) {
                    buildMixinClass(mixin)
                }.writeTo(codeGenerator, mixin.dependencies)
            }
            mixin.accessor?.let { generateMixinAccessor(mixin, it) }
            mixin.extension?.let { generateMixinExtension(mixin, it) }
        }
    }

    private fun buildPatchImplClass(mixin: IrMixin): KPType =
        buildKotlinClass(mixin.patchImplType.simpleName) {
            setModifiers(IrModifier.PUBLIC)
            setSuperClass(mixin.patchDeclarationType)
            val constructorProperties = setConstructor(
                listOf(IrParameter("instance", mixin.targetType)),
                IrModifier.OVERRIDE
            )
            val instanceProperty = constructorProperties.single()
            addProperties(mixin.accessor?.kinds?.filterIsInstance<IrFieldGetterAccessor>()?.map { getter ->
                buildKotlinProperty(getter.name, getter.type) {
                    setModifiers(IrModifier.OVERRIDE)
                    setGetter {
                        setBody {
                            return_(buildString {
                                append(
                                    if (getter.isStatic) "%T"
                                    else "(this@%T.%N as %T)"
                                )
                                append(".%L()")
                            }) {
                                if (!getter.isStatic) {
                                    arg(mixin.patchImplType)
                                    arg(instanceProperty)
                                }
                                arg(mixin.accessor.type)
                                arg(getter.internalName)
                            }
                        }
                    }
                    mixin.accessor.kinds.find { it is IrFieldSetterAccessor && it.name == getter.name }?.let { setter ->
                        setSetter {
                            setParameters(setter.parameters)
                            setBody {
                                line(buildString {
                                    append(
                                        if (getter.isStatic) "%T"
                                        else "(this@%T.%N as %T)"
                                    )
                                    append(".%L(%L)")
                                }) {
                                    if (!getter.isStatic) {
                                        arg(mixin.patchImplType)
                                        arg(instanceProperty)
                                    }
                                    arg(mixin.accessor.type)
                                    arg(setter.internalName)
                                    arg(setter.parameters.joinToString { it.name })
                                }
                            }
                        }
                    }
                }
            }.orEmpty())
            addFunctions(mixin.accessor?.kinds?.filterIsInstance<IrMethodAccessor>()?.map { method ->
                buildKotlinFunction(method.name) {
                    setModifiers(IrModifier.OVERRIDE)
                    setParameters(method.parameters)
                    setReturnType(method.returnType)
                    setBody {
                        val format = buildString {
                            append(
                                if (method.isStatic) "%T"
                                else "(this@%T.%N as %T)"
                            )
                            append(".%L(%L)")
                        }
                        val args: IrKotlinCodeBlockBuilder.Arguments.() -> Unit = {
                            if (!method.isStatic) {
                                arg(mixin.patchImplType)
                                arg(instanceProperty)
                            }
                            arg(mixin.accessor.type)
                            arg(method.internalName)
                            arg(method.parameters.joinToString { it.name })
                        }
                        if (method.returnType != null) {
                            return_(format, args)
                        } else {
                            line(format, args)
                        }
                    }
                }
            }.orEmpty())
            addTypes(
                mixin.innerMixins
                    .filter { it.isNotEmpty() }
                    .map { buildPatchImplClass(it) }
            )
        }

    private fun buildMixinClass(mixin: IrMixin): JPType =
        buildJavaClass(mixin.type.simpleName) {
            setModifiers(IrModifier.PUBLIC)
            addAnnotation<Mixin> {
                setClassArrayMember(Mixin::value, mixin.targetType)
            }
            val patchField = buildJavaField("patch".withInternalPrefix(), mixin.patchDeclarationType) {
                setModifiers(IrModifier.PRIVATE)
                addAnnotation<Unique>()
            }
            val getThisMethod = buildJavaMethod("getThis".withInternalPrefix()) {
                setModifiers(IrModifier.PRIVATE)
                addAnnotation<Unique>()
                setReturnType(mixin.targetType)
                setBody {
                    return_("(%T) (%T) this") {
                        arg(mixin.targetType)
                        arg(Object::class.asIr())
                    }
                }
            }
            val getOrInitPatchMethod = buildJavaMethod("getOrInitPatch".withInternalPrefix()) {
                setModifiers(IrModifier.PRIVATE)
                addAnnotation<Unique>()
                setReturnType(mixin.patchDeclarationType)
                setBody {
                    val patchNotInitializedCondition = buildJavaCodeBlock("%N == null") {
                        arg(patchField)
                    }
                    if_(patchNotInitializedCondition) {
                        line("%N = new %T(%N())") {
                            arg(patchField)
                            arg(mixin.patchImplType)
                            arg(getThisMethod)
                        }
                    }
                    return_("%N") { arg(patchField) }
                }
            }
            addField(patchField)
            addMethod(getThisMethod)
            addMethod(getOrInitPatchMethod)
            mixin.extension?.let { extension ->
                addSuperInterface(extension.type)
                addMethods(extension.kinds.map { method ->
                    buildJavaMethod(method.internalName) {
                        setModifiers(IrModifier.PUBLIC, IrModifier.OVERRIDE)
                        setParameters(method.parameters)
                        setReturnType(method.returnType)
                        setBody {
                            val format = "%N().%L(%L)"
                            val args: IrJavaCodeBlockBuilder.Arguments.() -> Unit = {
                                arg(getOrInitPatchMethod)
                                arg(
                                    when (method) {
                                        is IrFieldGetterExtension -> "get" + method.name.capitalize()
                                        is IrFieldSetterExtension -> "set" + method.name.capitalize()
                                        else -> method.name
                                    }
                                )
                                arg(method.parameters.joinToString { it.name })
                            }
                            if (method.returnType != null) {
                                return_(format, args)
                            } else {
                                line(format, args)
                            }
                        }
                    }
                })
            }
            addMethods(mixin.injections.map {
                buildMixinInjectionMethod(it, getThisMethod, getOrInitPatchMethod)
            })
        }

    private fun buildMixinInjectionMethod(
        injection: IrInjection,
        getThisMethod: JPMethod,
        getOrInitPatchMethod: JPMethod
    ): JPMethod =
        buildJavaMethod(injection.name) {
            val callbackParameterName = "callback".withInternalPrefix()
            val originalParameterName = "original".withInternalPrefix()
            val receiverParameterName = "receiver".withInternalPrefix()
            setModifiers(IrModifier.PRIVATE)
            when (injection) {
                is IrWrapMethodInjection -> {
                    addAnnotation<WrapMethod> {
                        setStringArrayMember(WrapMethod::method, injection.method)
                    }
                }

                is IrWrapOperationInjection -> {
                    addAnnotation<WrapOperation> {
                        setStringArrayMember(WrapOperation::method, injection.method)
                        setAnnotationArrayMember<WrapOperation, At>(WrapOperation::at) {
                            setStringMember(At::value, "INVOKE")
                            setStringMember(At::target, injection.target)
                            injection.ordinal?.let { setIntMember(At::ordinal, it) }
                        }
                    }
                }

                is IrModifyConstantValueInjection -> {
                    addAnnotation<ModifyExpressionValue> {
                        setStringArrayMember(ModifyExpressionValue::method, injection.method)
                        setAnnotationArrayMember<ModifyExpressionValue, At>(ModifyExpressionValue::at) {
                            setStringMember(At::value, "CONSTANT")
                            setStringArrayMember(
                                At::args,
                                "${injection.literalTypeName}Value=${injection.literalValue}"
                            )
                            injection.ordinal?.let { setIntMember(At::ordinal, it) }
                        }
                    }
                }
            }
            val sortedParameters = injection.parameters.sortedWith(
                compareBy<IrInjectionParameter> { it.priority }.thenBy { it.subPriority }
            )
            val hasCallback = sortedParameters.find { it is IrInjectionCallbackParameter } != null
            addParameters(sortedParameters.map { parameter ->
                when (parameter) {
                    is IrInjectionReceiverParameter -> {
                        buildJavaParameter(receiverParameterName, parameter.type)
                    }

                    is IrInjectionArgumentParameter -> {
                        buildJavaParameter(parameter.name.withInternalPrefix("argument"), parameter.type)
                    }

                    is IrInjectionOperationParameter -> {
                        buildJavaParameter(
                            originalParameterName,
                            Operation::class.asIr().parameterizedBy(parameter.returnType.orVoid())
                        )
                    }

                    is IrInjectionLiteralParameter -> {
                        buildJavaParameter(originalParameterName, parameter.type)
                    }

                    is IrInjectionLocalParameter -> {
                        buildJavaParameter(parameter.name.withInternalPrefix("local"), parameter.type) {
                            addAnnotation<Local> {
                                setIntMember(Local::ordinal, parameter.ordinal)
                            }
                        }
                    }

                    is IrInjectionParameterParameter -> {
                        buildJavaParameter(parameter.name.withInternalPrefix("parameter"), parameter.type) {
                            addAnnotation<Local> {
                                setIntMember(Local::index, parameter.index)
                                setBooleanMember(Local::argsOnly, true)
                            }
                        }
                    }

                    is IrInjectionCallbackParameter -> {
                        buildJavaParameter(
                            callbackParameterName,
                            parameter.returnType
                                ?.let { CallbackInfoReturnable::class.asIr().parameterizedBy(it) }
                                ?: CallbackInfo::class.asIr()
                        ) {
                            addAnnotation<Cancellable>()
                        }
                    }
                }
            })
            setReturnType(injection.returnType)
            val hookArgumentCodeBlocks = injection.hookArguments.map { argument ->
                when (argument) {
                    is IrHookContextArgument -> {
                        val constructorParameterCodeBlocks = buildList {
                            addAll(argument.descriptor.parameters.map {
                                buildJavaCodeBlock(it.name.withInternalPrefix("parameter"))
                            })
                            add(buildJavaCodeBlock(callbackParameterName))
                        }
                        buildJavaCodeBlock(buildString {
                            append("new %T(")
                            append(constructorParameterCodeBlocks.joinToString { "%L" })
                            append(")")
                        }) {
                            arg(argument.descriptor.type)
                            constructorParameterCodeBlocks.forEach { arg(it) }
                        }
                    }

                    is IrHookTargetArgument -> {
                        val constructorParameterCodeBlocks = buildList {
                            if (injection is IrWrapMethodInjection && !injection.isStatic) {
                                add(buildJavaCodeBlock("%N()") { arg(getThisMethod) })
                                add(buildJavaCodeBlock("%L") { arg(false) })
                            } else if (injection is IrWrapOperationInjection && !injection.isStatic) {
                                add(buildJavaCodeBlock(receiverParameterName))
                                add(buildJavaCodeBlock("%L") { arg(true) })
                            }
                            addAll(argument.descriptor.parameters.map {
                                buildJavaCodeBlock(it.name.withInternalPrefix("argument"))
                            })
                            add(buildJavaCodeBlock(originalParameterName))
                        }
                        buildJavaCodeBlock(buildString {
                            append("new %T(")
                            append(constructorParameterCodeBlocks.joinToString { "%L" })
                            append(")")
                        }) {
                            arg(argument.descriptor.type)
                            constructorParameterCodeBlocks.forEach { arg(it) }
                        }
                    }

                    is IrHookLiteralArgument -> buildJavaCodeBlock("%L") { arg(originalParameterName) }
                    is IrHookOrdinalArgument -> buildJavaCodeBlock("%L") { arg(argument.ordinal) }
                    is IrHookLocalArgument -> buildJavaCodeBlock("%L") {
                        arg(argument.parameterName.withInternalPrefix("local"))
                    }
                }
            }
            setBody {
                val invokeHook: IrJavaCodeBlockBuilder.() -> Unit = {
                    val format = buildString {
                        append("%N().%L(")
                        append(hookArgumentCodeBlocks.joinToString { "%L" })
                        append(")")
                    }
                    val args: IrJavaCodeBlockBuilder.Arguments.() -> Unit = {
                        arg(getOrInitPatchMethod)
                        arg(injection.hookName)
                        hookArgumentCodeBlocks.forEach { arg(it) }
                    }
                    if (injection.returnType != null) {
                        return_(format, args)
                    } else {
                        line(format, args)
                    }
                }
                if (hasCallback) {
                    try_(
                        tryBody = invokeHook,
                        exceptionType = runtimeApi[ApiYieldSignal],
                        catchBody = if (injection.returnType != null) {
                            { return_(injection.returnType.javaPrimitiveType?.defaultValue.toString()) }
                        } else null
                    )
                } else {
                    buildJavaCodeBlock(invokeHook)
                }
            }
        }

    private fun generateMixinAccessor(mixin: IrMixin, accessor: IrAccessor) {
        buildJavaFile(accessor.type) {
            buildJavaInterface(accessor.type.simpleName) {
                addAnnotation<Mixin> {
                    setClassArrayMember(Mixin::value, mixin.targetType)
                }
                setModifiers(IrModifier.PUBLIC)
                addMethods(accessor.kinds.map { method ->
                    buildJavaMethod(method.internalName) {
                        setModifiers(
                            IrModifier.PUBLIC,
                            if (method.isStatic) IrModifier.STATIC else IrModifier.ABSTRACT,
                        )
                        if (method is IrMethodAccessor) {
                            addAnnotation<Invoker> {
                                setStringMember(Invoker::value, method.binaryName)
                            }
                        } else {
                            addAnnotation<Accessor> {
                                setStringMember(Accessor::value, method.binaryName)
                            }
                            if (method is IrFieldSetterAccessor) {
                                addAnnotation<Mutable>()
                            }
                        }
                        setParameters(method.parameters)
                        setReturnType(method.returnType)
                        if (method.isStatic) {
                            setBody {
                                line("throw new %T()") {
                                    arg(IllegalStateException::class.asIr())
                                }
                            }
                        }
                    }
                })
            }
        }.writeTo(codeGenerator, mixin.dependencies)

        extensionProperties += accessor.kinds.filterIsInstance<IrFieldGetterAccessor>().map { getter ->
            buildKotlinProperty(getter.name, getter.type) {
                setReceiverType(
                    if (getter.isStatic) KClass::class.asIr().parameterizedBy(mixin.targetType)
                    else mixin.targetType
                )
                setGetter {
                    if (getter.isStatic) {
                        addAnnotation<JvmName> {
                            setStringMember(
                                JvmName::name,
                                "get" + mixin.type.simpleName.capitalize() + "_" + getter.name
                            )
                        }
                    }
                    setBody {
                        return_(buildString {
                            append(
                                if (getter.isStatic) "%T"
                                else "(this as %T)"
                            )
                            append(".%L()")
                        }) {
                            arg(accessor.type)
                            arg(getter.internalName)
                        }
                    }
                }
                accessor.kinds.find { it is IrFieldSetterAccessor && it.name == getter.name }?.let { setter ->
                    setSetter {
                        setParameters(setter.parameters)
                        setBody {
                            line(buildString {
                                append(
                                    if (getter.isStatic) "%T"
                                    else "(this as %T)"
                                )
                                append(".%L(%L)")
                            }) {
                                arg(accessor.type)
                                arg(setter.internalName)
                                arg(setter.parameters.joinToString { it.name })
                            }
                        }
                    }
                }
            }
        }

        extensionFunctions += accessor.kinds.filterIsInstance<IrMethodAccessor>().map { method ->
            buildKotlinFunction(method.name) {
                setReceiverType(
                    if (method.isStatic) KClass::class.asIr().parameterizedBy(mixin.targetType)
                    else mixin.targetType
                )
                setParameters(method.parameters)
                setReturnType(method.returnType)
                setBody {
                    val format = buildString {
                        append(
                            if (method.isStatic) "%T"
                            else "(this as %T)"
                        )
                        append(".%L(%L)")
                    }
                    val args: IrKotlinCodeBlockBuilder.Arguments.() -> Unit = {
                        arg(accessor.type)
                        arg(method.internalName)
                        arg(method.parameters.joinToString { it.name })
                    }
                    if (method.returnType != null) {
                        return_(format, args)
                    } else {
                        line(format, args)
                    }
                }
            }
        }
    }

    private fun generateMixinExtension(mixin: IrMixin, extension: IrExtension) {
        buildJavaFile(extension.type) {
            buildJavaInterface(extension.type.simpleName) {
                setModifiers(IrModifier.PUBLIC)
                addMethods(extension.kinds.map { method ->
                    buildJavaMethod(method.internalName) {
                        setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
                        setParameters(method.parameters)
                        setReturnType(method.returnType)
                    }
                })
            }
        }.writeTo(codeGenerator, mixin.dependencies)

        extensionProperties += extension.kinds.filterIsInstance<IrFieldGetterExtension>().map { getter ->
            buildKotlinProperty(getter.name, getter.type) {
                setReceiverType(mixin.targetType)
                setGetter {
                    setBody {
                        return_("(this as %T).%L()") {
                            arg(extension.type)
                            arg(getter.internalName.backticked())
                        }
                    }
                }
                extension.kinds.find { it is IrFieldSetterExtension && it.name == getter.name }?.let { setter ->
                    setSetter {
                        setParameters(setter.parameters)
                        setBody {
                            line("(this as %T).%L(%L)") {
                                arg(extension.type)
                                arg(setter.internalName.backticked())
                                arg(setter.parameters.joinToString { it.name })
                            }
                        }
                    }
                }
            }
        }
        extensionFunctions += extension.kinds.filterIsInstance<IrMethodExtension>().map { method ->
            buildKotlinFunction(method.name) {
                setReceiverType(mixin.targetType)
                setParameters(method.parameters)
                setReturnType(method.returnType)
                setBody {
                    return_("(this as %T).%L(%L)") {
                        arg(extension.type)
                        arg(method.internalName.backticked())
                        arg(method.parameters.joinToString { it.name })
                    }
                }
            }
        }
    }

    private fun generateExtensions(dependencies: KspDependencies) {
        if (extensionProperties.isEmpty() && extensionFunctions.isEmpty()) {
            return
        }
        buildKotlinFile(options.generatedPackageName, "_Extensions") {
            addProperties(extensionProperties)
            addFunctions(extensionFunctions)
        }.writeTo(codeGenerator, dependencies)
    }

    private fun generateMixinConfig(mixins: List<IrMixin>) {
        codeGenerator.createResourceFile(
            path = "${options.modId}.mixins.json",
            contents = configJson.encodeToString(
                MixinConfig.of(
                    mixinPackage = options.mixinPackageName,
                    refmapFileName = options.refmapFileName,
                    qualifiedNames = mixins.flatMap { it.flattenTree() }.groupBy { it.side }.mapValues {
                        it.value.flatMap { mixin ->
                            buildList {
                                if (mixin.isNotEmpty()) {
                                    add(mixin.type.qualifiedName)
                                }
                                mixin.accessor?.let { accessor -> add(accessor.type.qualifiedName) }
                            }
                        }
                    },
                ),
            ),
            aggregating = true,
        )
    }
}

fun String.withInternalPrefix(prefix: String = "lapis"): String =
    "_${prefix}_$this"
