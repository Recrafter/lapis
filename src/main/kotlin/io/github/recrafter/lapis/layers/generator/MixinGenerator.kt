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
import io.github.recrafter.lapis.extensions.common.defaultValue
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ksp.KSPDependencies
import io.github.recrafter.lapis.extensions.ksp.createResourceFile
import io.github.recrafter.lapis.extensions.ksp.toDependencies
import io.github.recrafter.lapis.layers.Builtin
import io.github.recrafter.lapis.layers.Builtins
import io.github.recrafter.lapis.layers.lowering.*
import io.github.recrafter.lapis.layers.lowering.types.IrType
import io.github.recrafter.lapis.layers.lowering.types.orVoid
import kotlinx.serialization.json.Json
import org.objectweb.asm.Opcodes
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import java.io.File

class MixinGenerator(
    private val options: Options,
    private val builtins: Builtins,
    private val codeGenerator: CodeGenerator,
) {
    private val extensionProperties: MutableList<KPProperty> = mutableListOf()
    private val extensionFunctions: MutableList<KPFunction> = mutableListOf()

    fun generate(schemas: List<IrSchema>, mixins: List<IrMixin>) {
        val schemaContainingFiles = schemas.mapNotNull { it.containingFile }
        generateDescriptors(
            schemas.flatMap { it.descriptors },
            schemaContainingFiles.toDependencies()
        )
        mixins.forEach { generateMixin(it) }

        val allContainingFiles = schemaContainingFiles + mixins.mapNotNull { it.containingFile }
        generateExtensions(allContainingFiles.toDependencies())

        generateMixinConfig(mixins)
        generateAccessorConfig(schemas)
    }

    private fun generateDescriptors(descriptors: List<IrDescriptor>, dependencies: KSPDependencies) {
        if (descriptors.isEmpty()) {
            return
        }
        buildKotlinFile(options.generatedPackageName, "_Descriptors") {
            suppressWarnings(
                KWarning.RedundantVisibilityModifier,
                KWarning.NothingToInline,
            )
            descriptors.forEach { descriptor ->
                when (descriptor) {
                    is IrInvokableDescriptor -> {
                        descriptor.callable?.let { generateDescriptorCallableWrapper(descriptor, it) }
                        descriptor.context?.let { generateDescriptorContextWrapper(descriptor, it) }
                    }

                    is IrFieldDescriptor -> {
                        descriptor.getter?.let { generateDescriptorGetterWrapper(descriptor, it) }
                        descriptor.setter?.let { generateDescriptorSetterWrapper(descriptor, it) }
                    }
                }
            }
        }.writeTo(codeGenerator, dependencies)
    }

    private fun KPFileBuilder.generateDescriptorCallableWrapper(
        descriptor: IrInvokableDescriptor,
        callable: IrDescriptorCallable
    ) {
        val operationParameterName = "operation".withInternalPrefix()
        val receiverParameterName = "receiver".withInternalPrefix()
        val invokeWithReceiverParameterName = "invokeWithReceiver".withInternalPrefix()
        addType(buildKotlinClass(callable.classType.simpleName) {
            setConstructor(buildList {
                callable.receiverType?.let {
                    add(IrParameter(receiverParameterName, it))
                    add(IrParameter(invokeWithReceiverParameterName, Boolean::class.asIr()))
                }
                addAll(descriptor.parameters.map { parameter ->
                    IrParameter(parameter.name.withInternalPrefix("argument"), parameter.type)
                })
                add(
                    IrParameter(
                        operationParameterName,
                        Operation::class.asIr().generic(descriptor.returnType.orVoid())
                    )
                )
            })
            addSuperInterface(callable.superClassType)
        })
        addProperties(descriptor.parameters.map { parameter ->
            buildKotlinProperty(parameter.name, parameter.type) {
                setReceiverType(callable.superClassType)
                setGetter {
                    setJvmName(callable.classType.simpleName + "_get" + parameter.name.capitalize())
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
                setJvmName(callable.classType.simpleName + "_getReceiver")
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
            setJvmName(callable.classType.simpleName + "_invoke")
            setModifiers(IrModifier.INLINE)
            setReceiverType(callable.superClassType)
            descriptor.parameters.forEach { parameter ->
                addParameter(buildKotlinParameter(parameter.name, parameter.type) {
                    defaultValue(buildKotlinCodeBlock("this.%L") {
                        arg(parameter.name)
                    })
                })
            }
            setReturnType(descriptor.returnType)
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
                    if (descriptor.returnType != null) {
                        return_(format, args)
                    } else {
                        code(format, args)
                    }
                }

                val parameters = descriptor.parameters.map { it.name }
                if (callable.receiverType != null) {
                    if_(buildKotlinCodeBlock(invokeWithReceiverParameterName)) {
                        callOperation(listOf(receiverParameterName) + parameters)
                        if (descriptor.returnType == null) {
                            return_()
                        }
                    }
                }
                callOperation(parameters)
            }
        })
    }

    private fun KPFileBuilder.generateDescriptorContextWrapper(
        descriptor: IrInvokableDescriptor,
        context: IrDescriptorContext
    ) {
        val callbackParameterName = "callback".withInternalPrefix()
        addType(buildKotlinClass(context.classType.simpleName) {
            setConstructor(buildList {
                addAll(descriptor.parameters.map { parameter ->
                    IrParameter(parameter.name.withInternalPrefix("parameter"), parameter.type)
                })
                add(
                    IrParameter(
                        callbackParameterName,
                        descriptor.returnType
                            ?.let { CallbackInfoReturnable::class.asIr().generic(it) }
                            ?: CallbackInfo::class.asIr()
                    )
                )
            })
            addSuperInterface(context.superClassType)
        })
        addProperties(descriptor.parameters.map { parameter ->
            buildKotlinProperty(parameter.name, parameter.type) {
                setReceiverType(context.superClassType)
                setGetter {
                    setJvmName(context.classType.simpleName + "_get" + parameter.name.capitalize())
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
            setJvmName(context.classType.simpleName + "_yield")
            setModifiers(IrModifier.INLINE)
            setReceiverType(context.superClassType)
            setReturnType(KPNothing.asIr())
            val returnValueParameter = descriptor.returnType?.let {
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

    private fun KPFileBuilder.generateDescriptorGetterWrapper(
        descriptor: IrFieldDescriptor,
        getter: IrDescriptorGetter
    ) {
        val receiverParameterName = "receiver".withInternalPrefix()
        val operationParameterName = "operation".withInternalPrefix()
        addType(buildKotlinClass(getter.classType.simpleName) {
            setConstructor(buildList {
                getter.receiverType?.let { add(IrParameter(receiverParameterName, it)) }
                add(IrParameter(operationParameterName, Operation::class.asIr().generic(descriptor.type)))
            })
            addSuperInterface(getter.superClassType)
        })
        getter.receiverType?.let {
            addProperty(buildKotlinProperty("receiver", it) {
                setReceiverType(getter.superClassType)
                setGetter {
                    setJvmName(getter.classType.simpleName + "_receiver")
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%L") {
                            arg(getter.classType)
                            arg(receiverParameterName)
                        }
                    }
                }
            })
        }
        addFunction(buildKotlinFunction("get") {
            setJvmName(getter.classType.simpleName + "_get")
            setModifiers(IrModifier.INLINE)
            setReceiverType(getter.superClassType)
            getter.receiverType?.let {
                addParameter(buildKotlinParameter("receiver", it) {
                    defaultValue(buildKotlinCodeBlock("this.%L") {
                        arg("receiver")
                    })
                })
            }
            setReturnType(descriptor.type)
            setBody {
                return_(buildString {
                    append("(this as %T).%L.%L(")
                    if (getter.receiverType != null) {
                        append("%L")
                    }
                    append(")")
                }) {
                    arg(getter.classType)
                    arg(operationParameterName)
                    arg(Operation<*>::call)
                    if (getter.receiverType != null) {
                        arg("receiver")
                    }
                }
            }
        })
    }

    private fun KPFileBuilder.generateDescriptorSetterWrapper(
        descriptor: IrFieldDescriptor,
        setter: IrDescriptorSetter
    ) {
        val receiverParameterName = "receiver".withInternalPrefix()
        val valueParameterName = "value".withInternalPrefix()
        val operationParameterName = "operation".withInternalPrefix()
        addType(buildKotlinClass(setter.classType.simpleName) {
            setConstructor(buildList {
                setter.receiverType?.let { add(IrParameter(receiverParameterName, it)) }
                add(IrParameter(valueParameterName, descriptor.type))
                add(IrParameter(operationParameterName, Operation::class.asIr().generic(IrType.VOID)))
            })
            addSuperInterface(setter.superClassType)
        })
        setter.receiverType?.let {
            addProperty(buildKotlinProperty("receiver", it) {
                setReceiverType(setter.superClassType)
                setGetter {
                    setJvmName(setter.classType.simpleName + "_receiver")
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%L") {
                            arg(setter.classType)
                            arg(receiverParameterName)
                        }
                    }
                }
            })
        }
        addProperty(buildKotlinProperty("value", descriptor.type) {
            setReceiverType(setter.superClassType)
            setGetter {
                setJvmName(setter.classType.simpleName + "_value")
                setModifiers(IrModifier.INLINE)
                setBody {
                    return_("(this as %T).%L") {
                        arg(setter.classType)
                        arg(valueParameterName)
                    }
                }
            }
        })
        addFunction(buildKotlinFunction("set") {
            setJvmName(setter.classType.simpleName + "_set")
            setModifiers(IrModifier.INLINE)
            setReceiverType(setter.superClassType)
            addParameter(buildKotlinParameter("value", descriptor.type) {
                defaultValue(buildKotlinCodeBlock("this.%L") {
                    arg("value")
                })
            })
            setter.receiverType?.let {
                addParameter(buildKotlinParameter("receiver", it) {
                    defaultValue(buildKotlinCodeBlock("this.%L") {
                        arg("receiver")
                    })
                })
            }
            setBody {
                code(buildString {
                    append("(this as %T).%L.%L(")
                    if (setter.receiverType != null) {
                        append("%L, ")
                    }
                    append("%L")
                    append(")")
                }) {
                    arg(setter.classType)
                    arg(operationParameterName)
                    arg(Operation<*>::call)
                    if (setter.receiverType != null) {
                        arg("receiver")
                    }
                    arg("value")
                }
            }
        })
    }

    private fun generateMixin(mixin: IrMixin) {
        if (mixin.isNotEmpty()) {
            val dependencies = listOfNotNull(mixin.containingFile).toDependencies()
            buildKotlinFile(mixin.patchImplClassType) {
                suppressWarnings(KWarning.RedundantVisibilityModifier)
                addType(buildPatchImplClass(mixin))
            }.writeTo(codeGenerator, dependencies)

            buildJavaFile(mixin.classType) {
                buildMixinClass(mixin)
            }.writeTo(codeGenerator, dependencies)
        }
        mixin.extension?.let { generateMixinExtension(mixin, it) }
    }

    private fun buildPatchImplClass(mixin: IrMixin): KPClass =
        buildKotlinClass(mixin.patchImplClassType.simpleName) {
            setModifiers(IrModifier.PUBLIC)
            setSuperClass(mixin.patchClassType)
            setConstructor(
                listOf(IrParameter("instance", mixin.targetClassType)),
                IrModifier.OVERRIDE
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
            addMethods(mixin.injections.map { buildMixinInjectionMethod(it, getThisMethod, getOrInitPatchMethod) })
        }

    private fun buildMixinInjectionMethod(
        injection: IrInjection,
        getThisMethod: JPMethod,
        getOrInitPatchMethod: JPMethod
    ): JPMethod {
        val name = buildString {
            append(injection.name)
            if (injection.ordinal != At::ordinal.defaultValue) {
                append("_ordinal${injection.ordinal}")
            }
        }
        return buildJavaMethod(name) {
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
                            "${injection.constantTypeName}Value=${injection.constantValue}"
                        )
                        setIntMember(At::ordinal, injection.ordinal)
                    }
                }

                is IrFieldGetInjection -> addAnnotation<WrapOperation> {
                    setStringArrayMember(WrapOperation::method, injection.method)
                    setAnnotationArrayMember<WrapOperation, At>(WrapOperation::at) {
                        setStringMember(At::value, "FIELD")
                        setStringMember(At::target, injection.target)
                        setIntMember(At::opcode, if (injection.isStatic) Opcodes.GETSTATIC else Opcodes.GETFIELD)
                        setIntMember(At::ordinal, injection.ordinal)
                    }
                }

                is IrFieldSetInjection -> addAnnotation<WrapOperation> {
                    setStringArrayMember(WrapOperation::method, injection.method)
                    setAnnotationArrayMember<WrapOperation, At>(WrapOperation::at) {
                        setStringMember(At::value, "FIELD")
                        setStringMember(At::target, injection.target)
                        setIntMember(At::opcode, if (injection.isStatic) Opcodes.PUTSTATIC else Opcodes.PUTFIELD)
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
                        val constructorArgumentCodeBlocks = buildList {
                            when (injection) {
                                is IrWrapMethodInjection if !injection.isStatic -> {
                                    add(buildJavaCodeBlock("%N()") { arg(getThisMethod) })
                                    add(buildJavaCodeBlock("%L") { arg(false) })
                                }

                                is IrWrapOperationInjection if !injection.isStatic -> {
                                    add(buildJavaCodeBlock(receiverParameterName))
                                    add(buildJavaCodeBlock("%L") { arg(true) })
                                }

                                is IrFieldGetInjection if !injection.isStatic -> {
                                    add(buildJavaCodeBlock(receiverParameterName))
                                }

                                is IrFieldSetInjection if !injection.isStatic -> {
                                    add(buildJavaCodeBlock(receiverParameterName))
                                    add(buildJavaCodeBlock("newValue".withInternalPrefix("argument")))
                                }

                                else -> {}
                            }
                            if (argument is IrHookCallableTargetArgument) {
                                addAll(argument.descriptor.parameters.map {
                                    buildJavaCodeBlock(it.name.withInternalPrefix("argument"))
                                })
                            }
                            add(buildJavaCodeBlock(originalParameterName))
                        }
                        buildJavaCodeBlock(buildString {
                            append("new %T(")
                            append(constructorArgumentCodeBlocks.joinToString { "%L" })
                            append(")")
                        }) {
                            arg(argument.wrapper.classType)
                            constructorArgumentCodeBlocks.forEach { arg(it) }
                        }
                    }

                    is IrHookContextArgument -> {
                        val constructorArgumentCodeBlocks = buildList {
                            addAll(argument.descriptor.parameters.map {
                                buildJavaCodeBlock(it.name.withInternalPrefix("parameter"))
                            })
                            add(buildJavaCodeBlock(callbackParameterName))
                        }
                        buildJavaCodeBlock(buildString {
                            append("new %T(")
                            append(constructorArgumentCodeBlocks.joinToString { "%L" })
                            append(")")
                        }) {
                            arg(argument.wrapper.classType)
                            constructorArgumentCodeBlocks.forEach { arg(it) }
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
                        catchBody = if (injection.returnType != null) {
                            { return_(injection.returnType.javaPrimitiveType?.defaultValue.toString()) }
                        } else null
                    )
                } else {
                    buildJavaCodeBlock(invokeHook)
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
            suppressWarnings(
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
                    qualifiedNames = mixins.groupBy { it.side }.mapValues { (_, mixins) ->
                        mixins.mapNotNull { mixin ->
                            if (mixin.isNotEmpty()) {
                                mixin.classType.qualifiedName
                            } else {
                                null
                            }
                        }
                    },
                )
            ),
            aggregating = true,
        )
    }

    private fun generateAccessorConfig(schemas: List<IrSchema>) {
        val entries = mutableListOf<AccessorConfigEntry>()
        schemas.forEach { schema ->
            if (schema.needAccess) {
                entries += ClassEntry(
                    classType = schema.targetClassType,
                    needRemoveFinal = schema.needRemoveFinal,
                )
            }
            schema.descriptors.filter { it.needAccess }.forEach { descriptor ->
                entries += when (descriptor) {
                    is IrInvokableDescriptor -> {
                        MethodEntry(
                            ownerClassType = schema.targetClassType,
                            name = descriptor.binaryName,
                            parameterTypes = descriptor.parameters.map { it.type },
                            returnType = if (descriptor is IrConstructorDescriptor) null else descriptor.returnType,
                            needRemoveFinal = descriptor.needRemoveFinal,
                        )
                    }

                    is IrFieldDescriptor -> {
                        FieldEntry(
                            ownerClassType = schema.targetClassType,
                            name = descriptor.targetName,
                            type = descriptor.type,
                            needRemoveFinal = descriptor.needRemoveFinal,
                        )
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            return
        }
        val sortedEntries = entries.distinctBy { it.awEntry }.sortedWith(
            compareBy<AccessorConfigEntry> {
                when (it) {
                    is ClassEntry -> 0
                    is FieldEntry -> 1
                    is MethodEntry -> 2
                }
            }.thenBy { it.awEntry }
        )
        options.accessWidener?.let { path ->
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(buildString {
                appendLine("accessWidener v2 named")
                appendLine()
                sortedEntries.forEach { appendLine(it.awEntry) }
            })
        }
        options.accessTransformer?.let { path ->
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(buildString {
                sortedEntries.forEach { appendLine(it.atEntry) }
            })
        }
    }
}

fun String.withInternalPrefix(prefix: String = LapisMeta.LOWER_NAME): String =
    "_${prefix}_$this"

private val configJson: Json = Json { prettyPrint = true }
