package io.github.recrafter.lapis.phases.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSFile
import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.llamalad7.mixinextras.injector.ModifyReturnValue
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Cancellable
import com.llamalad7.mixinextras.sugar.Local
import com.llamalad7.mixinextras.sugar.Share
import com.squareup.kotlinpoet.ksp.writeTo
import io.github.recrafter.lapis.LapisLogger
import io.github.recrafter.lapis.Options
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.extensions.InternalPrefix.*
import io.github.recrafter.lapis.extensions.capitalize
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ksp.createResourceFile
import io.github.recrafter.lapis.extensions.withInternalPrefix
import io.github.recrafter.lapis.phases.builtins.Builtins
import io.github.recrafter.lapis.phases.builtins.DescriptorWrapperBuiltin
import io.github.recrafter.lapis.phases.builtins.LocalVarImplBuiltin
import io.github.recrafter.lapis.phases.builtins.SimpleBuiltin
import io.github.recrafter.lapis.phases.generator.accessor.AccessorConfigEntry
import io.github.recrafter.lapis.phases.generator.accessor.ClassEntry
import io.github.recrafter.lapis.phases.generator.accessor.FieldEntry
import io.github.recrafter.lapis.phases.generator.accessor.MethodEntry
import io.github.recrafter.lapis.phases.generator.builders.Builder
import io.github.recrafter.lapis.phases.generator.builders.IrJavaCodeBlock
import io.github.recrafter.lapis.phases.lowering.IrModifier
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.models.*
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.binaryName
import io.github.recrafter.lapis.phases.lowering.types.orVoid
import kotlinx.serialization.json.Json
import org.objectweb.asm.Opcodes
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.*
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

class MixinGenerator(
    private val options: Options,
    private val builtins: Builtins,
    private val codeGenerator: CodeGenerator,
    private val logger: LapisLogger,
) {
    private val extensionProperties: MutableList<KPProperty> = mutableListOf()
    private val extensionFunctions: MutableList<KPFunction> = mutableListOf()

    fun generate(schemas: List<IrSchema>, mixins: List<IrMixin>) {
        val schemaContainingFiles = schemas.mapNotNull { it.containingFile }
        generateDescriptorWrappers(schemas.flatMap { it.descriptors }, schemaContainingFiles)
        mixins.forEach { generateMixin(it) }

        generateExtensions(schemaContainingFiles + mixins.mapNotNull { it.containingFile })

        generateMixinConfig(mixins)
        generateAccessorConfig(schemas)
    }

    private fun generateDescriptorWrappers(descriptors: List<IrDescriptor>, originatingFiles: List<KSFile>) {
        if (descriptors.isEmpty()) {
            return
        }
        buildKotlinFile(options.generatedPackageName, "_Descriptors") {
            suppressWarnings(
                KSuppressWarning.RedundantVisibilityModifier,
                KSuppressWarning.NothingToInline,
                KSuppressWarning.LocalVariableName,
            )
            descriptors.forEach { descriptor ->
                when (descriptor) {
                    is IrInvokableDescriptor -> {
                        descriptor.bodyWrapperImpl?.let {
                            builtins.generateDescriptorWrapperImpl(this, DescriptorWrapperBuiltin.Body, it)
                        }
                        descriptor.callWrapperImpl?.let {
                            builtins.generateDescriptorWrapperImpl(this, DescriptorWrapperBuiltin.Call, it)
                        }
                        descriptor.cancelWrapperImpl?.let {
                            builtins.generateDescriptorWrapperImpl(this, DescriptorWrapperBuiltin.Cancel, it)
                        }
                    }

                    is IrFieldDescriptor -> {
                        descriptor.fieldGetWrapperImpl?.let {
                            builtins.generateDescriptorWrapperImpl(this, DescriptorWrapperBuiltin.FieldGet, it)
                        }
                        descriptor.fieldSetWrapperImpl?.let {
                            builtins.generateDescriptorWrapperImpl(this, DescriptorWrapperBuiltin.FieldSet, it)
                        }
                        descriptor.arrayGetWrapperImpl?.let {
                            builtins.generateDescriptorWrapperImpl(this, DescriptorWrapperBuiltin.ArrayGet, it)
                        }
                        descriptor.arraySetWrapperImpl?.let {
                            builtins.generateDescriptorWrapperImpl(this, DescriptorWrapperBuiltin.ArraySet, it)
                        }
                    }
                }
            }
        }.writeTo(codeGenerator, aggregating = false, originatingFiles)
    }

    private fun generateMixin(mixin: IrMixin) {
        val originatingFiles = listOfNotNull(mixin.containingFile)
        buildKotlinFile(mixin.patchImpl.className) {
            suppressWarnings(KSuppressWarning.RedundantVisibilityModifier)
            addType(buildPatchImplClass(mixin))
        }.writeTo(codeGenerator, aggregating = false, originatingFiles)

        buildJavaFile(mixin.className) {
            buildMixinClass(mixin)
        }.writeTo(codeGenerator, aggregating = false, originatingFiles)
        mixin.extension?.let { generateMixinExtension(mixin, it) }
    }

    private fun buildPatchImplClass(mixin: IrMixin): KPClass =
        buildKotlinClass(mixin.patchImpl.className.simpleName) {
            setModifiers(IrModifier.PUBLIC)
            val instanceParameterName = "instance"
            val constructorParameters = mixin.patchImpl.constructorArguments.map { constructorParameter ->
                when (constructorParameter) {
                    is IrPatchImplConstructorInstanceArgument -> {
                        IrParameter(instanceParameterName, mixin.instanceClassName)
                    }
                }
            }
            if (constructorParameters.isNotEmpty()) {
                setConstructor(constructorParameters)
            }
            setSuperClass(
                mixin.patchClassName,
                mixin.patchImpl.patchConstructorArguments.map { patchConstructorArgument ->
                    when (patchConstructorArgument) {
                        is IrPatchConstructorOriginArgument -> buildKotlinCodeBlock("%L") { arg(instanceParameterName) }
                    }
                }
            )
        }

    private fun buildMixinClass(mixin: IrMixin): JPClass =
        buildJavaClass(mixin.className.simpleName) {
            addAnnotation<Mixin> {
                setStringArrayMember(Mixin::targets, mixin.bytecodeTargetName)
            }
            setModifiers(IrModifier.PUBLIC)
            val patchImplField = buildJavaField("patchImpl".withInternalPrefix(), mixin.patchImpl.className) {
                addAnnotation<Unique>()
                setModifiers(IrModifier.PRIVATE)
            }
            addField(patchImplField)
            val getOrInitPatchImplMethod = buildJavaMethod("getOrInitPatchImpl".withInternalPrefix()) {
                addAnnotation<Unique>()
                setModifiers(IrModifier.PRIVATE)
                setReturnType(mixin.patchImpl.className)
                setBody {
                    val isImplNotInitializedCondition = buildJavaCodeBlock("%N == null") {
                        arg(patchImplField)
                    }
                    if_(isImplNotInitializedCondition) {
                        val implConstructorArgumentCodeBlocks = mixin.patchImpl.constructorArguments.map { argument ->
                            when (argument) {
                                is IrPatchImplConstructorInstanceArgument -> {
                                    val isDoubleCastRequired = mixin.instanceClassName != KPAny.asIrClassName()
                                    buildJavaCodeBlock(
                                        buildString {
                                            if (isDoubleCastRequired) {
                                                append("(%T) (%T) ")
                                            }
                                            append("this")
                                        }
                                    ) {
                                        if (isDoubleCastRequired) {
                                            arg(mixin.instanceClassName)
                                            arg(Object::class.asIrClassName())
                                        }
                                    }
                                }
                            }
                        }
                        val format = buildString {
                            append("%N = new %T(")
                            append(implConstructorArgumentCodeBlocks.joinToString { "%L" })
                            append(")")
                        }
                        code_(format) {
                            arg(patchImplField)
                            arg(mixin.patchImpl.className)
                            implConstructorArgumentCodeBlocks.forEach { arg(it) }
                        }
                    }
                    return_("%N") { arg(patchImplField) }
                }
            }
            addMethod(getOrInitPatchImplMethod)
            mixin.extension?.let { extension ->
                addSuperInterface(extension.className)
                addMethods(extension.kinds.map { method ->
                    buildJavaMethod(method.methodName) {
                        setModifiers(IrModifier.PUBLIC, IrModifier.OVERRIDE)
                        setParameters(method.parameters)
                        setReturnType(method.returnTypeName)
                        setBody {
                            code_(
                                format = "%N().%L(%L)",
                                isReturn = method.returnTypeName != null
                            ) {
                                arg(getOrInitPatchImplMethod)
                                arg(
                                    when (method) {
                                        is IrPropertyGetterExtension -> "get" + method.name.capitalize()
                                        is IrPropertySetterExtension -> "set" + method.name.capitalize()
                                        is IrFunctionCallExtension -> method.name
                                    }
                                )
                                arg(method.parameters.joinToString { it.name })
                            }
                        }
                    }
                })
            }
            addMethods(mixin.injections.map {
                buildMixinInjectionMethod(it, mixin, getOrInitPatchImplMethod)
            })
        }

    private fun buildMixinInjectionMethod(
        injection: IrInjection,
        mixin: IrMixin,
        getOrInitPatchImplMethod: JPMethod,
    ): JPMethod =
        buildJavaMethod(buildString {
            append(injection.name)
            injection.ordinal?.let { append("_ordinal${it}") }
        }) {
            val hasCancelArgument = injection.hookArguments.any { it is IrHookCancelArgument }
            when (injection) {
                is IrWrapMethodInjection -> addAnnotation<WrapMethod> {
                    setStringArrayMember(WrapMethod::method, injection.methodMixinRef)
                }

                is IrInjectInjection -> addAnnotation<Inject> {
                    setStringArrayMember(Inject::method, injection.methodMixinRef)
                    setAnnotationArrayMember<Inject, At>(Inject::at) {
                        setStringMember(
                            At::value,
                            when (injection) {
                                is IrConstructorHeadInjection -> "CTOR_HEAD"
                                is IrMethodHeadInjection -> "HEAD"
                                is IrReturnInjection -> if (injection.isTail) "TAIL" else "RETURN"
                            }
                        )
                        if (injection is IrConstructorHeadInjection) {
                            setStringArrayMember(
                                At::args,
                                *injection.atArgs.map { "${it.first}=${it.second}" }.toTypedArray()
                            )
                        }
                        injection.ordinal?.let { setIntMember(At::ordinal, it) }
                        setBooleanMember(At::unsafe, true)
                    }
                    if (hasCancelArgument) {
                        setBooleanMember(Inject::cancellable, true)
                    }
                }

                is IrModifyVariableInjection -> addAnnotation<ModifyVariable> {
                    setStringArrayMember(ModifyVariable::method, injection.methodMixinRef)
                    when (val local = injection.local) {
                        is IrNamedLocal -> setStringArrayMember(ModifyVariable::name, local.name)
                        is IrPositionalLocal -> setIntMember(ModifyVariable::ordinal, local.ordinal)
                    }
                    setAnnotationMember<ModifyVariable, At>(ModifyVariable::at) {
                        setStringMember(
                            At::value,
                            when (injection.op) {
                                Op.Get -> "LOAD"
                                Op.Set -> "STORE"
                            }
                        )
                        injection.ordinal?.let { setIntMember(At::ordinal, it) }
                        setBooleanMember(At::unsafe, true)
                    }
                }

                is IrModifyReturnValueInjection -> addAnnotation<ModifyReturnValue> {
                    setStringArrayMember(ModifyReturnValue::method, injection.methodMixinRef)
                    setAnnotationArrayMember<ModifyReturnValue, At>(ModifyReturnValue::at) {
                        setStringMember(At::value, "RETURN")
                        injection.ordinal?.let { setIntMember(At::ordinal, it) }
                        setBooleanMember(At::unsafe, true)
                    }
                }

                is IrWrapOperationInjection -> addAnnotation<WrapOperation> {
                    setStringArrayMember(WrapOperation::method, injection.methodMixinRef)
                    setAnnotationArrayMember<WrapOperation, At>(WrapOperation::at) {
                        setStringMember(At::value, if (injection.isConstructorCall) "NEW" else "INVOKE")
                        setStringMember(At::target, injection.targetMixinRef)
                        injection.ordinal?.let { setIntMember(At::ordinal, it) }
                        setBooleanMember(At::unsafe, true)
                    }
                }

                is IrModifyExpressionValueInjection -> addAnnotation<ModifyExpressionValue> {
                    setStringArrayMember(ModifyExpressionValue::method, injection.methodMixinRef)
                    setAnnotationArrayMember<ModifyExpressionValue, At>(ModifyExpressionValue::at) {
                        setStringMember(At::value, "CONSTANT")
                        setStringArrayMember(
                            At::args,
                            *injection.atArgs.map { "${it.first}=${it.second}" }.toTypedArray()
                        )
                        injection.ordinal?.let { setIntMember(At::ordinal, it) }
                        setBooleanMember(At::unsafe, true)
                    }
                }

                is IrFieldGetInjection, is IrFieldSetInjection -> addAnnotation<WrapOperation> {
                    setStringArrayMember(WrapOperation::method, injection.methodMixinRef)
                    setAnnotationArrayMember<WrapOperation, At>(WrapOperation::at) {
                        setStringMember(At::value, "FIELD")
                        setStringMember(At::target, injection.targetMixinRef)
                        val opcode = when (injection) {
                            is IrFieldGetInjection -> {
                                if (injection.isStaticTarget) Opcodes.GETSTATIC
                                else Opcodes.GETFIELD
                            }

                            is IrFieldSetInjection -> {
                                if (injection.isStaticTarget) Opcodes.PUTSTATIC
                                else Opcodes.PUTFIELD
                            }
                        }
                        setIntMember(At::opcode, opcode)
                        injection.ordinal?.let { setIntMember(At::ordinal, it) }
                        setBooleanMember(At::unsafe, true)
                    }
                }

                is IrArrayInjection -> addAnnotation<Redirect> {
                    setStringArrayMember(Redirect::method, injection.methodMixinRef)
                    setAnnotationMember<Redirect, At>(Redirect::at) {
                        setStringMember(At::value, "FIELD")
                        setStringMember(At::target, injection.targetMixinRef)
                        setIntMember(
                            At::opcode,
                            if (injection.isStaticTarget) Opcodes.GETSTATIC else Opcodes.GETFIELD
                        )
                        val arrayOp = when (injection.op) {
                            Op.Get -> "get"
                            Op.Set -> "set"
                        }
                        setStringArrayMember(At::args, "array=$arrayOp")
                        injection.ordinal?.let { setIntMember(At::ordinal, it) }
                        setBooleanMember(At::unsafe, true)
                    }
                }

                is IrInstanceofInjection -> addAnnotation<WrapOperation> {
                    setStringArrayMember(WrapOperation::method, injection.methodMixinRef)
                    setAnnotationArrayMember<WrapOperation, Constant>(WrapOperation::constant) {
                        setClassMember(Constant::classValue, injection.className)
                        injection.ordinal?.let { setIntMember(Constant::ordinal, it) }
                    }
                }
            }
            setModifiers(
                listOfNotNull(
                    IrModifier.PRIVATE,
                    if (injection.isStatic) IrModifier.STATIC else null
                )
            )
            val receiverParameterName = "receiver".withInternalPrefix()
            val valueParameterName = "value".withInternalPrefix()
            val originalParameterName = "original".withInternalPrefix()
            val callbackParameterName = "callback".withInternalPrefix()
            addParameters(injection.parameters.map { parameter ->
                when (parameter) {
                    is IrInjectionReceiverParameter -> buildJavaParameter(receiverParameterName, parameter.typeName)
                    is IrInjectionArgumentParameter -> {
                        val name = parameter.name ?: parameter.index.toString()
                        buildJavaParameter(name.withInternalPrefix(ARGUMENT), parameter.typeName)
                    }

                    is IrInjectionOperationParameter -> {
                        buildJavaParameter(
                            originalParameterName,
                            Operation::class.asIrClassName().parameterizedBy(parameter.returnTypeName.orVoid())
                        )
                    }

                    is IrInjectionValueParameter -> buildJavaParameter(valueParameterName, parameter.typeName)

                    is IrInjectionLocalParameter -> {
                        val typeName = parameter.varImplBuiltin?.let {
                            if (it == LocalVarImplBuiltin.ObjectLocalVar) {
                                it.referenceClassName.parameterizedBy(parameter.typeName)
                            } else {
                                it.referenceClassName
                            }
                        } ?: parameter.typeName
                        when (parameter) {
                            is IrInjectionBodyLocalParameter -> {
                                buildJavaParameter(parameter.name.withInternalPrefix(LOCAL), typeName) {
                                    addAnnotation<Local> {
                                        when (val local = parameter.local) {
                                            is IrNamedLocal -> setStringArrayMember(Local::name, local.name)
                                            is IrPositionalLocal -> setIntMember(Local::ordinal, local.ordinal)
                                        }
                                    }
                                }
                            }

                            is IrInjectionParamLocalParameter -> {
                                buildJavaParameter(parameter.name.withInternalPrefix(PARAM), typeName) {
                                    addAnnotation<Local> {
                                        setIntMember(Local::index, parameter.localIndex)
                                        setBooleanMember(Local::argsOnly, true)
                                    }
                                }
                            }

                            is IrInjectionShareParameter -> {
                                buildJavaParameter(parameter.name.withInternalPrefix(SHARE), typeName) {
                                    addAnnotation<Share> {
                                        setStringMember(Share::value, parameter.key)
                                        if (parameter.isExported) {
                                            setStringMember(Share::namespace, options.modId)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is IrInjectionCallbackParameter -> {
                        buildJavaParameter(
                            callbackParameterName,
                            parameter.returnTypeName
                                ?.let { CallbackInfoReturnable::class.asIrClassName().parameterizedBy(it) }
                                ?: CallbackInfo::class.asIrClassName()
                        ) {
                            if (injection !is IrInjectInjection) {
                                addAnnotation<Cancellable>()
                            }
                        }
                    }
                }
            })
            setReturnType(injection.returnTypeName)
            val hookArgumentCodeBlocks = injection.hookArguments.map { argument ->
                when (argument) {
                    is IrHookOriginValueArgument -> {
                        buildJavaCodeBlock("%L") {
                            arg(valueParameterName)
                        }
                    }

                    is IrHookOriginDescriptorWrapperImplArgument<*> -> {
                        val descriptorWrapperConstructorArgumentCodeBlocks = buildList {
                            val impl = argument.wrapperImpl
                            if (
                                injection is IrTargetInjection &&
                                injection !is IrWrapMethodInjection &&
                                injection !is IrArrayInjection &&
                                !injection.isStaticTarget
                            ) {
                                add(buildJavaCodeBlock(receiverParameterName))
                            }
                            if (injection is IrFieldSetInjection) {
                                add(buildJavaCodeBlock("value".withInternalPrefix(ARGUMENT)))
                            }
                            if (impl is IrInvokableDescriptorWrapperImpl) {
                                addAll(impl.parameters.mapIndexed { index, parameter ->
                                    val name = parameter.name ?: index.toString()
                                    buildJavaCodeBlock(name.withInternalPrefix(ARGUMENT))
                                })
                            }
                            if (injection is IrInstanceofInjection) {
                                add(buildJavaCodeBlock("value".withInternalPrefix(ARGUMENT)))
                            }
                            if (injection is IrArrayInjection) {
                                add(buildJavaCodeBlock("array".withInternalPrefix(ARGUMENT)))
                                add(buildJavaCodeBlock("index".withInternalPrefix(ARGUMENT)))
                                if (injection.op == Op.Set) {
                                    add(buildJavaCodeBlock("value".withInternalPrefix(ARGUMENT)))
                                }
                            } else {
                                add(buildJavaCodeBlock(originalParameterName))
                            }
                        }
                        buildJavaCodeBlock(
                            buildString {
                                append("new %T(")
                                append(descriptorWrapperConstructorArgumentCodeBlocks.joinToString { "%L" })
                                append(")")
                            }
                        ) {
                            arg(argument.wrapperImpl.className)
                            descriptorWrapperConstructorArgumentCodeBlocks.forEach { arg(it) }
                        }
                    }

                    is IrHookOriginInstanceofArgument -> {
                        buildJavaCodeBlock("new %T(%L, %L)") {
                            arg(builtins[SimpleBuiltin.Instanceof])
                            arg(valueParameterName)
                            arg(originalParameterName)
                        }
                    }

                    is IrHookCancelArgument -> {
                        buildJavaCodeBlock("new %T(%L)") {
                            arg(argument.wrapperImpl.className)
                            arg(callbackParameterName)
                        }
                    }

                    is IrHookOrdinalArgument -> {
                        buildJavaCodeBlock("%L") {
                            arg(injection.ordinal ?: lapisError("Ordinal not found"))
                        }
                    }

                    is IrHookLocalArgument -> {
                        val localName = argument.name.withInternalPrefix(
                            when {
                                argument.isBody -> LOCAL
                                argument.isShare -> SHARE
                                injection is IrInjectInjection -> ARGUMENT
                                else -> PARAM
                            }
                        )
                        val format = buildString {
                            if (argument.varBuiltin != null) {
                                append("new %T")
                                if (argument.varBuiltin == LocalVarImplBuiltin.ObjectLocalVar) {
                                    append("<>")
                                }
                                append("(")
                            }
                            append("%L")
                            if (argument.varBuiltin != null) {
                                append(")")
                            }
                        }
                        buildJavaCodeBlock(format) {
                            argument.varBuiltin?.let { arg(builtins[it]) }
                            arg(localName)
                        }
                    }
                }
            }
            setBody {
                val invokeHook: Builder<IrJavaCodeBlock> = {
                    code_(
                        format = buildString {
                            if (injection.isStatic) {
                                append("%T.Companion")
                            } else {
                                append("%N()")
                            }
                            append(".%L(")
                            append(hookArgumentCodeBlocks.joinToString { "%L" })
                            append(")")
                        },
                        isReturn = injection.returnTypeName != null,
                    ) {
                        if (injection.isStatic) {
                            arg(mixin.patchImpl.className)
                        } else {
                            arg(getOrInitPatchImplMethod)
                        }
                        arg(injection.name)
                        hookArgumentCodeBlocks.forEach { arg(it) }
                    }
                }
                if (hasCancelArgument) {
                    try_(
                        block = invokeHook,
                        catchingClassName = builtins[SimpleBuiltin.CancelSignal],
                        catch_ = injection.returnTypeName?.let {
                            { return_(it.javaPrimitiveType?.primitiveDefaultValue ?: "null") }
                        },
                    )
                } else {
                    buildJavaCodeBlock(invokeHook)
                }
            }
        }

    private fun generateMixinExtension(mixin: IrMixin, extension: IrExtension) {
        buildKotlinFile(extension.className) {
            addType(buildKotlinInterface(extension.className.simpleName) {
                setModifiers(IrModifier.PUBLIC)
                addFunctions(extension.kinds.map { method ->
                    buildKotlinFunction(method.methodName) {
                        setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
                        setParameters(method.parameters)
                        setReturnType(method.returnTypeName)
                    }
                })
            })
        }.writeTo(codeGenerator, aggregating = false, listOfNotNull(mixin.containingFile))

        extensionProperties += extension.kinds.filterIsInstance<IrPropertyGetterExtension>().map { getter ->
            buildKotlinProperty(getter.name, getter.typeName) {
                setReceiverType(mixin.instanceClassName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%L()") {
                            arg(extension.className)
                            arg(getter.methodName)
                        }
                    }
                }
                extension.kinds.find { it is IrPropertySetterExtension && it.name == getter.name }?.let { setter ->
                    setSetter {
                        setModifiers(IrModifier.INLINE)
                        setParameters(setter.parameters)
                        setBody {
                            code_("(this as %T).%L(%L)") {
                                arg(extension.className)
                                arg(setter.methodName)
                                arg(setter.parameters.joinToString { it.name })
                            }
                        }
                    }
                }
            }
        }
        extensionFunctions += extension.kinds.filterIsInstance<IrFunctionCallExtension>().map { method ->
            buildKotlinFunction(method.name) {
                setModifiers(IrModifier.INLINE)
                setReceiverType(mixin.instanceClassName)
                setParameters(method.parameters)
                setReturnType(method.returnTypeName)
                setBody {
                    return_("(this as %T).%L(%L)") {
                        arg(extension.className)
                        arg(method.methodName)
                        arg(method.parameters.joinToString { it.name })
                    }
                }
            }
        }
    }

    private fun generateExtensions(originatingFiles: List<KSFile>) {
        if (extensionProperties.isEmpty() && extensionFunctions.isEmpty()) {
            return
        }
        buildKotlinFile(options.generatedPackageName, "_Extensions") {
            suppressWarnings(
                KSuppressWarning.RedundantVisibilityModifier,
                KSuppressWarning.UnusedReceiverParameter,
                KSuppressWarning.NothingToInline,
            )
            addProperties(extensionProperties)
            addFunctions(extensionFunctions)
        }.writeTo(codeGenerator, aggregating = false, originatingFiles)
    }

    private fun generateMixinConfig(mixins: List<IrMixin>) {
        val contents = configJson.encodeToString(
            MixinConfig.of(
                mixinPackage = options.mixinPackageName,
                qualifiedNames = mixins.groupBy { it.side }.mapValues { (_, mixins) ->
                    mixins.map { it.className.qualifiedName }
                },
            )
        )
        logger.info(buildString {
            appendLine("Mixin config generated:")
            append(contents)
        })
        codeGenerator.createResourceFile(
            path = options.mixinConfigName,
            contents = contents,
            aggregating = true,
        )
    }

    private fun generateAccessorConfig(schemas: List<IrSchema>) {
        val entries = mutableListOf<AccessorConfigEntry>()
        schemas.forEach { schema ->
            if (schema.makePublic) {
                entries += ClassEntry(
                    ownerClassName = schema.originClassName,
                    removeFinal = schema.removeFinal,
                )
            }
            schema.descriptors.filter { it.makePublic }.forEach { descriptor ->
                entries += when (descriptor) {
                    is IrInvokableDescriptor -> {
                        MethodEntry(
                            ownerClassName = schema.originClassName,
                            name = descriptor.binaryName,
                            parameterTypes = descriptor.parameters.map { it.typeName },
                            returnTypeName = when (descriptor) {
                                is IrConstructorDescriptor -> null
                                else -> descriptor.returnTypeName
                            },
                            removeFinal = descriptor.removeFinal,
                            isConstructor = descriptor is IrConstructorDescriptor,
                        )
                    }

                    is IrFieldDescriptor -> {
                        FieldEntry(
                            ownerClassName = schema.originClassName,
                            name = descriptor.targetName,
                            typeName = descriptor.typeName,
                            removeFinal = descriptor.removeFinal,
                        )
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            return
        }
        val sortedEntries = entries.distinctBy { it.awEntry }.sorted()

        fun formatConfig(header: String? = null, directive: (AccessorConfigEntry) -> String): String = buildString {
            header?.let { appendLine(it) }
            var lastOwner: IrClassName? = null
            sortedEntries.forEach { entry ->
                appendLine()
                if (lastOwner != entry.ownerClassName) {
                    if (lastOwner != null) {
                        appendLine()
                    }
                    appendLine("# ${entry.ownerClassName.nestedName}")
                    lastOwner = entry.ownerClassName
                }
                append(directive(entry))
            }
        }

        options.accessWidenerConfigName?.let { name ->
            val header = if (options.isUnobfuscated) "classTweaker v1 official" else "accessWidener v2 named"
            val contents = formatConfig(header) { it.awEntry }
            logger.info(buildString {
                appendLine("AW config generated:")
                append(contents)
            })
            codeGenerator.createResourceFile(
                path = name,
                contents = contents,
                aggregating = true,
            )
        }
        options.accessTransformerConfigName?.let { name ->
            val contents = formatConfig { it.atEntry }
            logger.info(buildString {
                appendLine("AT config generated:")
                append(contents)
            })
            codeGenerator.createResourceFile(
                path = name,
                contents = contents,
                aggregating = true,
            )
        }
    }

    private val IrExtensionKind.methodName: String
        get() = when (this) {
            is IrPropertyGetterExtension -> "get" + name.capitalize()
            is IrPropertySetterExtension -> "set" + name.capitalize()
            is IrFunctionCallExtension -> name
        }.withInternalPrefix(options.modId)
}

private val configJson: Json = Json { prettyPrint = true }
