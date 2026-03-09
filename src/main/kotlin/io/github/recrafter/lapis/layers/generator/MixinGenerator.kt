package io.github.recrafter.lapis.layers.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Cancellable
import com.llamalad7.mixinextras.sugar.Local
import io.github.recrafter.lapis.LapisMeta
import io.github.recrafter.lapis.Options
import io.github.recrafter.lapis.extensions.capitalize
import io.github.recrafter.lapis.extensions.common.unsafeLazy
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ksp.KSPDependencies
import io.github.recrafter.lapis.extensions.ksp.createResourceFile
import io.github.recrafter.lapis.extensions.ksp.toDependencies
import io.github.recrafter.lapis.layers.Builtin
import io.github.recrafter.lapis.layers.Builtins
import io.github.recrafter.lapis.layers.lowering.*
import io.github.recrafter.lapis.layers.lowering.types.orVoid
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
    private val builtins: Builtins,
    private val codeGenerator: CodeGenerator,
) {
    private val configJson: Json by unsafeLazy {
        Json { prettyPrint = true }
    }
    private val extensionProperties: MutableList<KPProperty> = mutableListOf()
    private val extensionFunctions: MutableList<KPFunction> = mutableListOf()

    fun generate(descriptors: List<IrDescriptor>, mixins: List<IrMixin>) {
        generateDescriptors(descriptors)
        mixins.forEach { generateMixin(it) }

        generateExtensions(mixins.mapNotNull { it.containingFile }.toDependencies())
        generateMixinConfig(mixins)
    }

    private fun generateDescriptors(descriptors: List<IrDescriptor>) {
        if (descriptors.isEmpty()) {
            return
        }
        buildKotlinFile(options.generatedPackageName, "_Descriptors") {
            suppress(
                KWarning.RedundantVisibilityModifier,
                KWarning.NothingToInline,
            )
            descriptors.forEach { descriptor ->
                when (descriptor) {
                    is IrInvokableDescriptor -> {
                        descriptor.callable?.let { generateDescriptorCallable(it) }
                        descriptor.context?.let { generateDescriptorContext(it) }
                    }

                    is IrFieldDescriptor -> {
                        TODO("Field descriptors not supported yet.")
                    }
                }
            }
        }.writeTo(codeGenerator, descriptors.mapNotNull { it.containingFile }.toDependencies())
    }

    private fun KPFileBuilder.generateDescriptorCallable(callable: IrDescriptorCallable) {
        val operationParameterName = "operation".withInternalPrefix()
        val receiverParameterName = "receiver".withInternalPrefix()
        val invokeWithReceiverParameterName = "invokeWithReceiver".withInternalPrefix()
        addType(buildKotlinClass(callable.classType.simpleName) {
            setConstructor(
                buildList {
                    callable.receiverType?.let {
                        add(IrParameter(receiverParameterName, it))
                        add(IrParameter(invokeWithReceiverParameterName, Boolean::class.asIr()))
                    }
                    addAll(callable.parameters.map { parameter ->
                        IrParameter(parameter.name.withInternalPrefix("argument"), parameter.type)
                    })
                    add(
                        IrParameter(
                            operationParameterName,
                            Operation::class.asIr().generic(callable.returnType.orVoid())
                        )
                    )
                }
            )
            addSuperInterface(callable.superClassType)
        })
        addProperties(callable.parameters.map { parameter ->
            buildKotlinProperty(parameter.name, parameter.type) {
                setReceiverType(callable.superClassType)
                setGetter {
                    addAnnotation<JvmName> {
                        setStringMember(
                            JvmName::name,
                            callable.classType.simpleName.capitalize() + "_get" + parameter.name.capitalize()
                        )
                    }
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%L") {
                            arg(callable.classType)
                            arg(parameter.name.withInternalPrefix("argument"))
                        }
                    }
                }
            }
        })
        callable.receiverType?.let { returnType ->
            addFunction(buildKotlinFunction("getReceiver") {
                addAnnotation<JvmName> {
                    setStringMember(
                        JvmName::name,
                        callable.classType.simpleName.capitalize() + "_getReceiver"
                    )
                }
                setModifiers(IrModifier.INLINE)
                setReceiverType(callable.superClassType)
                setReturnType(returnType)
                setBody {
                    return_("(this as %T).%L") {
                        arg(callable.classType)
                        arg(receiverParameterName)
                    }
                }
            })
        }
        addFunction(buildKotlinFunction("invoke") {
            addAnnotation<JvmName> {
                setStringMember(
                    JvmName::name,
                    callable.classType.simpleName.capitalize() + "_invoke"
                )
            }
            setModifiers(IrModifier.INLINE)
            setReceiverType(callable.superClassType)
            callable.parameters.forEach { parameter ->
                addParameter(buildKotlinParameter(parameter.name, parameter.type) {
                    defaultValue(buildKotlinCodeBlock("this.%L") {
                        arg(parameter.name)
                    })
                })
            }
            setReturnType(callable.returnType)
            setBody {
                code("this as %T") {
                    arg(callable.classType)
                }

                fun callOperation(parameters: List<String>) {
                    val format = "%L.%L(%L)"
                    val args: IrKotlinCodeBlockBuilder.Arguments.() -> Unit = {
                        arg(operationParameterName)
                        arg(Operation<*>::call)
                        arg(parameters.joinToString())
                    }
                    if (callable.returnType != null) {
                        return_(format, args)
                    } else {
                        code(format, args)
                    }
                }

                val parameters = callable.parameters.map { it.name }
                if (callable.receiverType != null) {
                    if_(buildKotlinCodeBlock(invokeWithReceiverParameterName)) {
                        callOperation(listOf(receiverParameterName) + parameters)
                        if (callable.returnType == null) {
                            return_()
                        }
                    }
                }
                callOperation(parameters)
            }
        })

    }

    private fun KPFileBuilder.generateDescriptorContext(context: IrDescriptorContext) {
        val callbackParameterName = "callback".withInternalPrefix()
        addType(buildKotlinClass(context.classType.simpleName) {
            setConstructor(
                buildList {
                    addAll(context.parameters.map { parameter ->
                        IrParameter(parameter.name.withInternalPrefix("parameter"), parameter.type)
                    })
                    add(
                        IrParameter(
                            callbackParameterName,
                            context.returnType
                                ?.let { CallbackInfoReturnable::class.asIr().generic(it) }
                                ?: CallbackInfo::class.asIr()
                        )
                    )
                }
            )
            addSuperInterface(context.superClassType)
        })
        addProperties(context.parameters.map { parameter ->
            buildKotlinProperty(parameter.name, parameter.type) {
                setReceiverType(context.superClassType)
                setGetter {
                    addAnnotation<JvmName> {
                        setStringMember(
                            JvmName::name,
                            context.classType.simpleName.capitalize() + "_get" + parameter.name.capitalize()
                        )
                    }
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%L") {
                            arg(context.classType)
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
                    context.classType.simpleName.capitalize() + "_yield"
                )
            }
            setModifiers(IrModifier.INLINE)
            setReceiverType(context.superClassType)
            setReturnType(KPNothing.asIr())
            val returnValueParameter = context.returnType?.let {
                addParameter(IrParameter("returnValue", it))
            }
            setBody {
                code("(this as %T)") {
                    arg(context.classType)
                }
                code(buildString {
                    append("%L.%L(")
                    if (returnValueParameter != null) {
                        append("%N")
                    }
                    append(")")
                }) {
                    arg(callbackParameterName)
                    arg(
                        if (returnValueParameter != null) CallbackInfoReturnable<*>::setReturnValue
                        else CallbackInfo::cancel
                    )
                    returnValueParameter?.let { arg(it) }
                }
                throw_("%T") {
                    arg(builtins[Builtin.YieldSignal])
                }
            }
        })
    }

    private fun generateMixin(mixin: IrMixin) {
        mixin.flattenTree().forEach { mixin ->
            if (mixin.isNotEmpty()) {
                val dependencies = listOfNotNull(mixin.containingFile).toDependencies()
                buildKotlinFile(mixin.patchImplClassType) {
                    suppress(KWarning.RedundantVisibilityModifier)
                    addType(buildPatchImplClass(mixin))
                }.writeTo(codeGenerator, dependencies)

                buildJavaFile(mixin.classType) {
                    buildMixinClass(mixin)
                }.writeTo(codeGenerator, dependencies)
            }
            mixin.accessor?.let { generateMixinAccessor(mixin, it) }
            mixin.extension?.let { generateMixinExtension(mixin, it) }
        }
    }

    private fun buildPatchImplClass(mixin: IrMixin): KPClass =
        buildKotlinClass(mixin.patchImplClassType.simpleName) {
            setModifiers(IrModifier.PUBLIC)
            setSuperClass(mixin.patchClassType)
            val constructorProperties = setConstructor(
                listOf(IrParameter("instance", mixin.targetClassType)),
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
                                    arg(mixin.patchImplClassType)
                                    arg(instanceProperty)
                                }
                                arg(mixin.accessor.classType)
                                arg(getter.internalName)
                            }
                        }
                    }
                    mixin.accessor.kinds.find { it is IrFieldSetterAccessor && it.name == getter.name }?.let { setter ->
                        setSetter {
                            setParameters(setter.parameters)
                            setBody {
                                code(buildString {
                                    append(
                                        if (getter.isStatic) "%T"
                                        else "(this@%T.%N as %T)"
                                    )
                                    append(".%L(%L)")
                                }) {
                                    if (!getter.isStatic) {
                                        arg(mixin.patchImplClassType)
                                        arg(instanceProperty)
                                    }
                                    arg(mixin.accessor.classType)
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
                                arg(mixin.patchImplClassType)
                                arg(instanceProperty)
                            }
                            arg(mixin.accessor.classType)
                            arg(method.internalName)
                            arg(method.parameters.joinToString { it.name })
                        }
                        if (method.returnType != null) {
                            return_(format, args)
                        } else {
                            code(format, args)
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

    private fun buildMixinClass(mixin: IrMixin): JPClass =
        buildJavaClass(mixin.classType.simpleName) {
            addAnnotation<Mixin> {
                setClassArrayMember(Mixin::value, mixin.targetClassType)
            }
            setModifiers(IrModifier.PUBLIC)
            val patchField = buildJavaField("patch".withInternalPrefix(), mixin.patchClassType) {
                addAnnotation<Unique>()
                setModifiers(IrModifier.PRIVATE)
            }
            val getThisMethod = buildJavaMethod("getThis".withInternalPrefix()) {
                addAnnotation<Unique>()
                setModifiers(IrModifier.PRIVATE)
                setReturnType(mixin.targetClassType)
                setBody {
                    return_("(%T) (%T) this") {
                        arg(mixin.targetClassType)
                        arg(Object::class.asIr())
                    }
                }
            }
            val getOrInitPatchMethod = buildJavaMethod("getOrInitPatch".withInternalPrefix()) {
                addAnnotation<Unique>()
                setModifiers(IrModifier.PRIVATE)
                setReturnType(mixin.patchClassType)
                setBody {
                    val patchNotInitializedCondition = buildJavaCodeBlock("%N == null") {
                        arg(patchField)
                    }
                    if_(patchNotInitializedCondition) {
                        code("%N = new %T(%N())") {
                            arg(patchField)
                            arg(mixin.patchImplClassType)
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
                addSuperInterface(extension.classType)
                addMethods(extension.kinds.map { method ->
                    buildJavaMethod(method.getInternalName(options.modId)) {
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
                                code(format, args)
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
        buildJavaMethod(injection.internalName) {
            val callbackParameterName = "callback".withInternalPrefix()
            val originalParameterName = "original".withInternalPrefix()
            val receiverParameterName = "receiver".withInternalPrefix()
            when (injection) {
                is IrWrapMethodInjection -> addAnnotation<WrapMethod> {
                    setStringArrayMember(WrapMethod::method, injection.method)
                }

                is IrWrapOperationInjection -> addAnnotation<WrapOperation> {
                    setStringArrayMember(WrapOperation::method, injection.method)
                    setAnnotationArrayMember<WrapOperation, At>(WrapOperation::at) {
                        setStringMember(At::value, "INVOKE")
                        setStringMember(At::target, injection.target)
                        setIntMember(At::ordinal, injection.ordinal)
                    }
                }

                is IrModifyConstantValueInjection -> addAnnotation<ModifyExpressionValue> {
                    setStringArrayMember(ModifyExpressionValue::method, injection.method)
                    setAnnotationArrayMember<ModifyExpressionValue, At>(ModifyExpressionValue::at) {
                        setStringMember(At::value, "CONSTANT")
                        setStringArrayMember(
                            At::args,
                            "${injection.typeName}Value=${injection.value}"
                        )
                        setIntMember(At::ordinal, injection.ordinal)
                    }
                }
            }
            setModifiers(IrModifier.PRIVATE)
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
                            Operation::class.asIr().generic(parameter.returnType.orVoid())
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
                                ?.let { CallbackInfoReturnable::class.asIr().generic(it) }
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
                            arg(argument.descriptor.classType)
                            constructorParameterCodeBlocks.forEach { arg(it) }
                        }
                    }

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
                            arg(argument.descriptor.classType)
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
                        arg(injection.name)
                        hookArgumentCodeBlocks.forEach { arg(it) }
                    }
                    if (injection.returnType != null) {
                        return_(format, args)
                    } else {
                        code(format, args)
                    }
                }
                if (hasCallback) {
                    try_(
                        tryBody = invokeHook,
                        exceptionType = builtins[Builtin.YieldSignal],
                        catchBody = when {
                            injection.returnType != null -> {
                                { return_(injection.returnType.javaPrimitiveType?.defaultValue.toString()) }
                            }

                            else -> null
                        }
                    )
                } else {
                    buildJavaCodeBlock(invokeHook)
                }
            }
        }

    private fun generateMixinAccessor(mixin: IrMixin, accessor: IrAccessor) {
        buildJavaFile(accessor.classType) {
            buildJavaInterface(accessor.classType.simpleName) {
                addAnnotation<Mixin> {
                    setClassArrayMember(Mixin::value, mixin.targetClassType)
                }
                setModifiers(IrModifier.PUBLIC)
                addMethods(accessor.kinds.map { method ->
                    buildJavaMethod(method.internalName) {
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
                        setModifiers(
                            IrModifier.PUBLIC,
                            if (method.isStatic) IrModifier.STATIC else IrModifier.ABSTRACT,
                        )
                        setParameters(method.parameters)
                        setReturnType(method.returnType)
                        if (method.isStatic) {
                            setBody {
                                throw_("new %T()") {
                                    arg(IllegalStateException::class.asIr())
                                }
                            }
                        }
                    }
                })
            }
        }.writeTo(codeGenerator, listOfNotNull(mixin.containingFile).toDependencies())

        extensionProperties += accessor.kinds.filterIsInstance<IrFieldGetterAccessor>().map { getter ->
            buildKotlinProperty(getter.name, getter.type) {
                setReceiverType(
                    if (getter.isStatic) KClass::class.asIr().generic(mixin.targetClassType)
                    else mixin.targetClassType
                )
                setGetter {
                    if (getter.isStatic) {
                        addAnnotation<JvmName> {
                            setStringMember(
                                JvmName::name,
                                mixin.classType.simpleName.capitalize() + "_get" + getter.name
                            )
                        }
                    }
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_(buildString {
                            append(
                                if (getter.isStatic) "%T"
                                else "(this as %T)"
                            )
                            append(".%L()")
                        }) {
                            arg(accessor.classType)
                            arg(getter.internalName)
                        }
                    }
                }
                accessor.kinds.find { it is IrFieldSetterAccessor && it.name == getter.name }?.let { setter ->
                    setSetter {
                        setModifiers(IrModifier.INLINE)
                        setParameters(setter.parameters)
                        setBody {
                            code(buildString {
                                append(
                                    if (getter.isStatic) "%T"
                                    else "(this as %T)"
                                )
                                append(".%L(%L)")
                            }) {
                                arg(accessor.classType)
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
                setModifiers(IrModifier.INLINE)
                setReceiverType(
                    if (method.isStatic) KClass::class.asIr().generic(mixin.targetClassType)
                    else mixin.targetClassType
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
                        arg(accessor.classType)
                        arg(method.internalName)
                        arg(method.parameters.joinToString { it.name })
                    }
                    if (method.returnType != null) {
                        return_(format, args)
                    } else {
                        code(format, args)
                    }
                }
            }
        }
    }

    private fun generateMixinExtension(mixin: IrMixin, extension: IrExtension) {
        buildJavaFile(extension.classType) {
            buildJavaInterface(extension.classType.simpleName) {
                setModifiers(IrModifier.PUBLIC)
                addMethods(extension.kinds.map { method ->
                    buildJavaMethod(method.getInternalName(options.modId)) {
                        setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
                        setParameters(method.parameters)
                        setReturnType(method.returnType)
                    }
                })
            }
        }.writeTo(codeGenerator, listOfNotNull(mixin.containingFile).toDependencies())

        extensionProperties += extension.kinds.filterIsInstance<IrFieldGetterExtension>().map { getter ->
            buildKotlinProperty(getter.name, getter.type) {
                setReceiverType(mixin.targetClassType)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%L()") {
                            arg(extension.classType)
                            arg(getter.getInternalName(options.modId))
                        }
                    }
                }
                extension.kinds.find { it is IrFieldSetterExtension && it.name == getter.name }?.let { setter ->
                    setSetter {
                        setModifiers(IrModifier.INLINE)
                        setParameters(setter.parameters)
                        setBody {
                            code("(this as %T).%L(%L)") {
                                arg(extension.classType)
                                arg(setter.getInternalName(options.modId))
                                arg(setter.parameters.joinToString { it.name })
                            }
                        }
                    }
                }
            }
        }
        extensionFunctions += extension.kinds.filterIsInstance<IrMethodExtension>().map { method ->
            buildKotlinFunction(method.name) {
                setModifiers(IrModifier.INLINE)
                setReceiverType(mixin.targetClassType)
                setParameters(method.parameters)
                setReturnType(method.returnType)
                setBody {
                    return_("(this as %T).%L(%L)") {
                        arg(extension.classType)
                        arg(method.getInternalName(options.modId))
                        arg(method.parameters.joinToString { it.name })
                    }
                }
            }
        }
    }

    private fun generateExtensions(dependencies: KSPDependencies) {
        if (extensionProperties.isEmpty() && extensionFunctions.isEmpty()) {
            return
        }
        buildKotlinFile(options.generatedPackageName, "_Extensions") {
            suppress(
                KWarning.RedundantVisibilityModifier,
                KWarning.UnusedReceiverParameter,
                KWarning.NothingToInline,
            )
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
                                    add(mixin.classType.qualifiedName)
                                }
                                mixin.accessor?.let { accessor ->
                                    add(accessor.classType.qualifiedName)
                                }
                            }
                        }
                    },
                )
            ),
            aggregating = true,
        )
    }
}

fun String.withInternalPrefix(prefix: String = LapisMeta.LOWER_NAME): String =
    "_${prefix}_$this"
