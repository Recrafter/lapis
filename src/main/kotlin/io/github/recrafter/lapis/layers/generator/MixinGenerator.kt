package io.github.recrafter.lapis.layers.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Cancellable
import com.llamalad7.mixinextras.sugar.Local
import com.squareup.kotlinpoet.KModifier
import io.github.recrafter.lapis.api.LapisReturnSignal
import io.github.recrafter.lapis.extensions.backticked
import io.github.recrafter.lapis.extensions.capitalizeWithPrefix
import io.github.recrafter.lapis.extensions.common.asIr
import io.github.recrafter.lapis.extensions.common.unsafeLazy
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ksp.KspDependencies
import io.github.recrafter.lapis.extensions.ksp.createResourceFile
import io.github.recrafter.lapis.extensions.ksp.flatten
import io.github.recrafter.lapis.extensions.withJavaInternalPrefix
import io.github.recrafter.lapis.extensions.withKotlinInternalPrefix
import io.github.recrafter.lapis.layers.lowering.*
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

class MixinGenerator(private val options: Options, private val codeGenerator: CodeGenerator) {

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
        val callbackParameterName = "callback".withKotlinInternalPrefix()
        buildKotlinFile(options.generatedPackageName, "DescriptorImpls") {
            descriptors.forEach { descriptor ->
                val contextImpl = descriptor.contextImpl
                addType(buildKotlinClass(contextImpl.type.simpleName) {
                    setConstructor(
                        buildList {
                            addAll(contextImpl.parameters.map { parameter ->
                                IrParameter(parameter.name, parameter.type)
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
                val contextImplUniqueName = contextImpl.type.uniqueJvmName
                addProperties(contextImpl.parameters.map { parameter ->
                    buildKotlinProperty(parameter.name, parameter.type) {
                        setReceiverType(contextImpl.superType)
                        setGetter {
                            addAnnotation<JvmName> {
                                setStringMember(
                                    JvmName::name,
                                    contextImplUniqueName.capitalizeWithPrefix("get") + "_" + parameter.name
                                )
                            }
                            setBody {
                                line("return (this as %T).%L") {
                                    arg(contextImpl.type)
                                    arg(parameter.name)
                                }
                            }
                        }
                    }
                })
                addFunction(buildKotlinFunction("yield") {
                    addAnnotation<JvmName> {
                        setStringMember(
                            JvmName::name,
                            contextImplUniqueName.capitalizeWithPrefix("yield")
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
                            arg(LapisReturnSignal::class.asIr())
                        }
                    }
                })

                val targetImpl = descriptor.targetImpl
                val operationParameterName = "operation".withKotlinInternalPrefix()
                val receiverParameterName = "receiver".withKotlinInternalPrefix()
                val invokeWithReceiverParameterName = "invokeWithReceiver".withKotlinInternalPrefix()
                addType(buildKotlinClass(targetImpl.type.simpleName) {
                    setConstructor(
                        buildList {
                            targetImpl.receiverType?.let {
                                add(IrParameter(receiverParameterName, it))
                                add(IrParameter(invokeWithReceiverParameterName, Boolean::class.asIr()))
                            }
                            addAll(targetImpl.parameters.map { parameter ->
                                IrParameter(parameter.name, parameter.type)
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
                                line("return (this as %T).%L") {
                                    arg(targetImpl.type)
                                    arg(parameter.name)
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
                            line("return (this as %T).%L") {
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
                            defaultValue("this.%L", parameter.name)
                        })
                    }
                    setReturnType(targetImpl.returnType)
                    setBody {
                        line("this as %T") {
                            arg(targetImpl.type)
                        }

                        fun callOperation(parameters: List<String>) {
                            line(buildString {
                                if (targetImpl.returnType != null) {
                                    append("return ")
                                }
                                append("%L.%L(%L)")
                            }) {
                                arg(operationParameterName)
                                arg(Operation<*>::call.name)
                                arg(parameters.joinToString())
                            }
                        }

                        val parameters = targetImpl.parameters.map { it.name }
                        if (targetImpl.receiverType != null) {
                            if_(buildKotlinCodeBlock(invokeWithReceiverParameterName)) {
                                callOperation(listOf(receiverParameterName) + parameters)
                                if (targetImpl.returnType == null) {
                                    line("return")
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
            setSuperClass(mixin.patchType)
            val constructorProperties = setConstructor(
                listOf(IrParameter("instance", mixin.targetType)),
                KModifier.OVERRIDE
            )
            val instanceProperty = constructorProperties.single()
            addProperties(mixin.accessor?.kinds?.filterIsInstance<IrFieldGetterAccessor>()?.map { getter ->
                buildKotlinProperty(getter.name, getter.type) {
                    setModifiers(IrModifier.OVERRIDE)
                    setGetter {
                        setBody {
                            line(buildString {
                                append("return ")
                                append(
                                    if (getter.isStatic) "%T"
                                    else "(%N as %T)"
                                )
                                append(".%L()")
                            }) {
                                if (!getter.isStatic) {
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
                                        else "(%N as %T)"
                                    )
                                    append(".%L(%L)")
                                }) {
                                    if (!getter.isStatic) {
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
                        line(buildString {
                            if (method.returnType != null) {
                                append("return ")
                            }
                            append(
                                if (method.isStatic) "%T.%L(%L)"
                                else "(%N as %T).%L(%L)"
                            )
                        }) {
                            if (!method.isStatic) {
                                arg(instanceProperty)
                            }
                            arg(mixin.accessor.type)
                            arg(method.internalName)
                            arg(method.parameters.joinToString { it.name })
                        }
                    }
                }
            }.orEmpty())
            addTypes(mixin.innerMixins.filter { it.isNotEmpty() }.map { buildPatchImplClass(it) })
        }

    private fun buildMixinClass(mixin: IrMixin): JPType =
        buildJavaClass(mixin.type.simpleName) {
            setModifiers(IrModifier.PUBLIC)
            addAnnotation<Mixin> {
                setClassMember(Mixin::value, mixin.targetType)
            }
            val patchField = buildJavaField("patch".withJavaInternalPrefix(), mixin.patchType) {
                setModifiers(IrModifier.PRIVATE)
                addAnnotation<Unique>()
            }
            val getThisMethod = buildJavaMethod("getThis".withJavaInternalPrefix()) {
                setModifiers(IrModifier.PRIVATE)
                addAnnotation<Unique>()
                setReturnType(mixin.targetType)
                setBody {
                    line("return (%T) (%T) this") {
                        arg(mixin.targetType)
                        arg(Object::class.asIr())
                    }
                }
            }
            val getOrInitPatchMethod = buildJavaMethod("getOrInitPatch".withJavaInternalPrefix()) {
                setModifiers(IrModifier.PRIVATE)
                addAnnotation<Unique>()
                setReturnType(mixin.patchType)
                setBody {
                    val patchNotInitializedCondition = buildJavaCodeBlock("%N == %L") {
                        arg(patchField)
                        arg(null.toString())
                    }
                    if_(patchNotInitializedCondition) {
                        line("%N = new %T(%N())") {
                            arg(patchField)
                            arg(mixin.patchImplType)
                            arg(getThisMethod)
                        }
                    }
                    line("return %N") { arg(patchField) }
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
                            line(buildString {
                                if (method.returnType != null) {
                                    append("return ")
                                }
                                append("%N().%L(%L)")
                            }) {
                                arg(getOrInitPatchMethod)
                                arg(
                                    when (method) {
                                        is IrFieldGetterExtension -> method.name.capitalizeWithPrefix("get")
                                        is IrFieldSetterExtension -> method.name.capitalizeWithPrefix("set")
                                        else -> method.name
                                    }
                                )
                                arg(method.parameters.joinToString { it.name })
                            }
                        }
                    }
                })
            }
            addMethods(mixin.injections.map { buildMixinInjectionMethod(it, getThisMethod, getOrInitPatchMethod) })
        }

    private fun buildMixinInjectionMethod(
        injection: IrInjection,
        getThisMethod: JPMethod,
        getOrInitPatchMethod: JPMethod
    ): JPMethod =
        buildJavaMethod(injection.name) {
            val callbackParameterName = "callback".withJavaInternalPrefix()
            val originalParameterName = "original".withJavaInternalPrefix()
            val receiverParameterName = "receiver".withJavaInternalPrefix()
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
                        addAnnotationMember<At>(WrapOperation::at) {
                            setStringMember(At::value, "INVOKE")
                            setStringMember(At::target, injection.target)
                            injection.ordinal?.let { setIntMember(At::ordinal, it) }
                        }
                    }
                }

                is IrModifyConstantValueInjection -> {
                    addAnnotation<ModifyExpressionValue> {
                        setStringArrayMember(ModifyExpressionValue::method, injection.method)
                        addAnnotationMember<At>(ModifyExpressionValue::at) {
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
                        buildJavaParameter(parameter.name.withJavaInternalPrefix("argument"), parameter.type)
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

                    is IrInjectionBodyLocalParameter -> {
                        buildJavaParameter(parameter.name.withJavaInternalPrefix("body"), parameter.type) {
                            addAnnotation<Local> {
                                setIntMember(Local::ordinal, parameter.ordinal)
                            }
                        }
                    }

                    is IrInjectionSignatureLocalParameter -> {
                        buildJavaParameter(parameter.name.withJavaInternalPrefix("signature"), parameter.type) {
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
                    is IrHookContextArgument -> buildJavaCodeBlock("new %T(%L)") {
                        arg(argument.descriptor.type)
                        arg(buildList {
                            addAll(argument.descriptor.parameters.map {
                                it.name.withJavaInternalPrefix("signature")
                            })
                            add(callbackParameterName)
                        }.joinToString())
                    }

                    is IrHookTargetArgument -> buildJavaCodeBlock("new %T(%L)") {
                        arg(argument.descriptor.type)
                        arg(buildList {
                            if (injection is IrWrapMethodInjection && !injection.isStatic) {
                                add(getThisMethod.name() + "()")
                                add(false.toString())
                            } else if (injection is IrWrapOperationInjection && !injection.isStatic) {
                                add(receiverParameterName)
                                add(true.toString())
                            }
                            addAll(argument.descriptor.parameters.map {
                                it.name.withJavaInternalPrefix("argument")
                            })
                            add(originalParameterName)
                        }.joinToString())
                    }

                    is IrHookLiteralArgument -> buildJavaCodeBlock("%L") { arg(originalParameterName) }
                    is IrHookOrdinalArgument -> buildJavaCodeBlock("%L") { arg(argument.ordinal) }
                    is IrHookLocalArgument -> buildJavaCodeBlock("%L") {
                        arg(argument.parameterName.withJavaInternalPrefix("body"))
                    }
                }
            }
            setBody {
                val invokeHook: IrJavaCodeBlockBuilder.() -> Unit = {
                    line(buildString {
                        if (injection.returnType != null) {
                            append("return ")
                        }
                        append("%N().%L(")
                        append(hookArgumentCodeBlocks.joinToString { "%L" })
                        append(")")
                    }) {
                        arg(getOrInitPatchMethod)
                        arg(injection.hookName)
                        hookArgumentCodeBlocks.forEach { arg(it) }
                    }
                }
                if (hasCallback) {
                    try_(
                        tryBody = invokeHook,
                        exceptionType = LapisReturnSignal::class,
                        catchBody = if (injection.returnType != null) {
                            { line("return " + injection.returnType.javaPrimitiveType?.defaultValue) }
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
                    setClassMember(Mixin::value, mixin.targetType)
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
                                setStringMember(Invoker::value, method.vanillaName)
                            }
                        } else {
                            addAnnotation<Accessor> {
                                setStringMember(Accessor::value, method.vanillaName)
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
                                mixin.type.uniqueJvmName.capitalizeWithPrefix("get") + "_" + getter.name
                            )
                        }
                    }
                    setBody {
                        line(buildString {
                            append("return ")
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
                    line(buildString {
                        if (method.returnType != null) {
                            append("return ")
                        }
                        append(
                            if (method.isStatic) "%T.%L(%L)"
                            else "(this as %T).%L(%L)"
                        )
                    }) {
                        arg(accessor.type)
                        arg(method.internalName)
                        arg(method.parameters.joinToString { it.name })
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
                        addModifiers(JPModifier.PUBLIC, JPModifier.ABSTRACT)
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
                        line("return (this as %T).%L()") {
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
                    line("return (this as %T).%L(%L)") {
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
        buildKotlinFile(options.generatedPackageName, "Extensions") {
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
