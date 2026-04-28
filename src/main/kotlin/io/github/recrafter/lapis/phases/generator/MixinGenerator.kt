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
import io.github.recrafter.lapis.phases.lowering.*
import io.github.recrafter.lapis.phases.lowering.models.*
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
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

    fun generate(schemas: List<IrSchema>, patches: List<IrPatch>) {
        val schemaOriginatingFiles = schemas.mapNotNull { it.originatingFile }
        val patchOriginatingFiles = patches.mapNotNull { it.originatingFile }

        generateDescriptorWrappers(schemas.flatMap { it.descriptors }, schemaOriginatingFiles + patchOriginatingFiles)
        patches.forEach { generateMixin(it) }

        generateExtensions(schemaOriginatingFiles + patchOriginatingFiles)

        generateMixinConfig(patches)
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

    private fun generateMixin(patch: IrPatch) {
        val originatingFiles = listOfNotNull(patch.originatingFile)
        buildKotlinFile(patch.impl.className) {
            suppressWarnings(KSuppressWarning.RedundantVisibilityModifier)
            addType(buildPatchImplClass(patch))
        }.writeTo(codeGenerator, aggregating = false, originatingFiles)
        buildJavaFile(patch.mixin.className) {
            buildMixinClass(patch)
        }.writeTo(codeGenerator, aggregating = false, originatingFiles)
        patch.extension?.let { generateMixinExtension(patch, it) }
    }

    private fun buildPatchImplClass(patch: IrPatch): KPClass =
        buildKotlinClass(patch.impl.className.simpleName) {
            setModifiers(IrModifier.PUBLIC)
            val instanceParameterName = "instance"
            val constructorParameters = patch.impl.constructorParameters.map { parameter ->
                when (parameter) {
                    is IrPatchImplConstructorInstanceParameter -> {
                        IrParameter(instanceParameterName, patch.mixin.instanceTypeName)
                    }
                }
            }
            if (constructorParameters.isNotEmpty()) {
                setConstructor(constructorParameters)
            }
            setSuperClass(
                patch.className,
                constructorArguments = patch.constructorArguments.map { argument ->
                    when (argument) {
                        is IrPatchConstructorOriginArgument -> {
                            buildKotlinCodeBlock("%L") { arg(instanceParameterName) }
                        }
                    }
                }
            )
        }

    private fun buildMixinClass(patch: IrPatch): JPClass =
        buildJavaClass(patch.mixin.className.simpleName) {
            addAnnotation<Mixin> {
                setArgumentValue(Mixin::targets, patch.mixin.targetInternalName)
            }
            setModifiers(IrModifier.PUBLIC)
            val patchField = buildJavaField("patch".withInternalPrefix(), patch.className) {
                addAnnotation<Unique>()
                setModifiers(IrModifier.PRIVATE)
            }
            val getOrInitPatchMethod = buildJavaMethod("getOrInitPatch".withInternalPrefix()) {
                addAnnotation<Unique>()
                setModifiers(IrModifier.PRIVATE)
                setReturnType(patch.className)
                setBody {
                    if_(buildJavaCodeBlock("%N == null") { arg(patchField) }) {
                        val constructorArgumentCodeBlocks = patch.impl.constructorParameters.map { parameter ->
                            when (parameter) {
                                is IrPatchImplConstructorInstanceParameter -> {
                                    val isDoubleCastRequired = patch.mixin.instanceTypeName != KPAny.asIrClassName()
                                    val isObjectCastRequired = !patch.mixin.isInterfaceInstance
                                    buildJavaCodeBlock(
                                        buildString {
                                            if (isDoubleCastRequired) {
                                                append("(%T) ")
                                                if (isObjectCastRequired) {
                                                    append("(%T) ")
                                                }
                                            }
                                            append("this")
                                        }
                                    ) {
                                        if (isDoubleCastRequired) {
                                            arg(patch.mixin.instanceTypeName)
                                            if (isObjectCastRequired) {
                                                arg(Object::class.asIrTypeName())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        code_(
                            buildString {
                                append("%N = new %T(")
                                append(constructorArgumentCodeBlocks.joinToString { "%L" })
                                append(")")
                            }
                        ) {
                            arg(patchField)
                            arg(patch.impl.className)
                            constructorArgumentCodeBlocks.forEach { arg(it) }
                        }
                    }
                    return_("%N") { arg(patchField) }
                }
            }
            addField(patchField)
            addMethod(getOrInitPatchMethod)
            patch.extension?.let { extension ->
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
                                arg(getOrInitPatchMethod)
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
            addMethods(patch.mixin.injections.map {
                buildMixinInjectionMethod(it, patch, getOrInitPatchMethod)
            })
        }

    private fun buildMixinInjectionMethod(
        injection: IrInjection,
        patch: IrPatch,
        getOrInitPatchImplMethod: JPMethod,
    ): JPMethod =
        buildJavaMethod(buildString {
            append(injection.name)
            injection.ordinal?.let { append("_ordinal${it}") }
        }) {
            val hasCancelArgument = injection.hookArguments.any { it is IrHookCancelArgument }
            when (injection) {
                is IrWrapMethodInjection -> addAnnotation<WrapMethod> {
                    setArgumentValue(WrapMethod::method, injection.methodMixinRef)
                }

                is IrInjectInjection -> addAnnotation<Inject> {
                    setArgumentValue(Inject::method, injection.methodMixinRef)
                    setArgumentValue<Inject, At>(Inject::at) {
                        setArgumentValue(
                            At::value,
                            when (injection) {
                                is IrConstructorHeadInjection -> "CTOR_HEAD"
                                is IrMethodHeadInjection -> "HEAD"
                                is IrReturnInjection -> if (injection.isTail) "TAIL" else "RETURN"
                            }
                        )
                        if (injection is IrConstructorHeadInjection) {
                            setArgumentValue(
                                At::args,
                                *injection.atArgs.map { "${it.first}=${it.second}" }.toTypedArray()
                            )
                        }
                        injection.ordinal?.let { setArgumentValue(At::ordinal, it) }
                        setArgumentValue(At::unsafe, true)
                    }
                    if (hasCancelArgument) {
                        setArgumentValue(Inject::cancellable, true)
                    }
                }

                is IrModifyVariableInjection -> addAnnotation<ModifyVariable> {
                    setArgumentValue(ModifyVariable::method, injection.methodMixinRef)
                    when (val local = injection.local) {
                        is IrNamedLocal -> setArgumentValue(ModifyVariable::name, local.name)
                        is IrPositionalLocal -> setArgumentValue(ModifyVariable::ordinal, local.ordinal)
                    }
                    setArgumentValue<ModifyVariable, At>(ModifyVariable::at) {
                        setArgumentValue(
                            At::value,
                            when (injection.op) {
                                Op.Get -> "LOAD"
                                Op.Set -> "STORE"
                            }
                        )
                        injection.ordinal?.let { setArgumentValue(At::ordinal, it) }
                        setArgumentValue(At::unsafe, true)
                    }
                }

                is IrModifyReturnValueInjection -> addAnnotation<ModifyReturnValue> {
                    setArgumentValue(ModifyReturnValue::method, injection.methodMixinRef)
                    setArgumentValue<ModifyReturnValue, At>(ModifyReturnValue::at) {
                        setArgumentValue(At::value, "RETURN")
                        injection.ordinal?.let { setArgumentValue(At::ordinal, it) }
                        setArgumentValue(At::unsafe, true)
                    }
                }

                is IrWrapOperationInjection -> addAnnotation<WrapOperation> {
                    setArgumentValue(WrapOperation::method, injection.methodMixinRef)
                    setArgumentValue<WrapOperation, At>(WrapOperation::at) {
                        setArgumentValue(At::value, if (injection.isConstructorCall) "NEW" else "INVOKE")
                        setArgumentValue(At::target, injection.targetMixinRef)
                        injection.ordinal?.let { setArgumentValue(At::ordinal, it) }
                        setArgumentValue(At::unsafe, true)
                    }
                }

                is IrModifyExpressionValueInjection -> addAnnotation<ModifyExpressionValue> {
                    setArgumentValue(ModifyExpressionValue::method, injection.methodMixinRef)
                    setArgumentValue<ModifyExpressionValue, At>(ModifyExpressionValue::at) {
                        setArgumentValue(At::value, "CONSTANT")
                        setArgumentValue(
                            At::args,
                            *injection.atArgs.map { "${it.first}=${it.second}" }.toTypedArray()
                        )
                        injection.ordinal?.let { setArgumentValue(At::ordinal, it) }
                        setArgumentValue(At::unsafe, true)
                    }
                }

                is IrFieldGetInjection, is IrFieldSetInjection -> addAnnotation<WrapOperation> {
                    setArgumentValue(WrapOperation::method, injection.methodMixinRef)
                    setArgumentValue<WrapOperation, At>(WrapOperation::at) {
                        setArgumentValue(At::value, "FIELD")
                        setArgumentValue(At::target, injection.targetMixinRef)
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
                        setArgumentValue(At::opcode, opcode)
                        injection.ordinal?.let { setArgumentValue(At::ordinal, it) }
                        setArgumentValue(At::unsafe, true)
                    }
                }

                is IrArrayInjection -> addAnnotation<Redirect> {
                    setArgumentValue(Redirect::method, injection.methodMixinRef)
                    setArgumentValue<Redirect, At>(Redirect::at) {
                        setArgumentValue(At::value, "FIELD")
                        setArgumentValue(At::target, injection.targetMixinRef)
                        setArgumentValue(
                            At::opcode,
                            if (injection.isStaticTarget) Opcodes.GETSTATIC else Opcodes.GETFIELD
                        )
                        val arrayOp = when (injection.op) {
                            Op.Get -> "get"
                            Op.Set -> "set"
                        }
                        setArgumentValue(At::args, "array=$arrayOp")
                        injection.ordinal?.let { setArgumentValue(At::ordinal, it) }
                        setArgumentValue(At::unsafe, true)
                    }
                }

                is IrInstanceofInjection -> addAnnotation<WrapOperation> {
                    setArgumentValue(WrapOperation::method, injection.methodMixinRef)
                    setArgumentValue<WrapOperation, Constant>(WrapOperation::constant) {
                        setArgumentValue(Constant::classValue, injection.className)
                        injection.ordinal?.let { setArgumentValue(Constant::ordinal, it) }
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
                    is IrInjectionReceiverParameter -> {
                        buildJavaParameter(receiverParameterName, parameter.typeName) {
                            if (parameter.isCoerce) {
                                addAnnotation<Coerce>()
                            }
                        }
                    }

                    is IrInjectionArgumentParameter -> {
                        val name = parameter.name ?: parameter.index.toString()
                        buildJavaParameter(name.withInternalPrefix(ARGUMENT), parameter.typeName)
                    }

                    is IrInjectionOperationParameter -> {
                        buildJavaParameter(
                            originalParameterName,
                            Operation::class.asIrParameterizedTypeName(parameter.returnTypeName.orVoid())
                        )
                    }

                    is IrInjectionValueParameter -> buildJavaParameter(valueParameterName, parameter.typeName)

                    is IrInjectionLocalParameter -> {
                        val typeName = parameter.varImplBuiltin?.let {
                            if (it == LocalVarImplBuiltin.ObjectLocalVar) {
                                it.referenceTypeName.parameterizedBy(parameter.typeName)
                            } else {
                                it.referenceTypeName
                            }
                        } ?: parameter.typeName
                        when (parameter) {
                            is IrInjectionBodyLocalParameter -> {
                                buildJavaParameter(parameter.name.withInternalPrefix(LOCAL), typeName) {
                                    addAnnotation<Local> {
                                        when (val local = parameter.local) {
                                            is IrNamedLocal -> setArgumentValue(Local::name, local.name)
                                            is IrPositionalLocal -> setArgumentValue(Local::ordinal, local.ordinal)
                                        }
                                    }
                                }
                            }

                            is IrInjectionParamLocalParameter -> {
                                buildJavaParameter(parameter.name.withInternalPrefix(PARAM), typeName) {
                                    addAnnotation<Local> {
                                        setArgumentValue(Local::index, parameter.localIndex)
                                        setArgumentValue(Local::argsOnly, true)
                                    }
                                }
                            }

                            is IrInjectionShareParameter -> {
                                buildJavaParameter(parameter.name.withInternalPrefix(SHARE), typeName) {
                                    addAnnotation<Share> {
                                        setArgumentValue(Share::value, parameter.key)
                                        if (parameter.isExported) {
                                            setArgumentValue(Share::namespace, options.modId)
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
                                ?.let { CallbackInfoReturnable::class.asIrParameterizedTypeName(it) }
                                ?: CallbackInfo::class.asIrTypeName()
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
                        buildJavaCodeBlock(
                            buildString {
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
                        ) {
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
                            arg(patch.className)
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

    private fun generateMixinExtension(patch: IrPatch, extension: IrExtension) {
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
        }.writeTo(codeGenerator, aggregating = false, listOfNotNull(patch.originatingFile))

        extensionProperties += extension.kinds.filterIsInstance<IrPropertyGetterExtension>().map { getter ->
            buildKotlinProperty(getter.name, getter.typeName) {
                setReceiverType(patch.mixin.instanceTypeName)
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
                setReceiverType(patch.mixin.instanceTypeName)
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

    private fun generateMixinConfig(patches: List<IrPatch>) {
        val contents = configJson.encodeToString(
            MixinConfig.of(
                mixinPackage = options.mixinPackageName,
                qualifiedNames = patches.groupBy { it.side }.mapValues { (_, patches) ->
                    patches.map { it.mixin.className.qualifiedName }
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
                    ownerClassName = schema.originTypeName.rawClassName,
                    removeFinal = schema.removeFinal,
                )
            }
            schema.descriptors.filter { it.makePublic }.forEach { descriptor ->
                entries += when (descriptor) {
                    is IrInvokableDescriptor -> {
                        MethodEntry(
                            ownerClassName = schema.originTypeName.rawClassName,
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
                            ownerClassName = schema.originTypeName.rawClassName,
                            name = descriptor.bytecodeName,
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
