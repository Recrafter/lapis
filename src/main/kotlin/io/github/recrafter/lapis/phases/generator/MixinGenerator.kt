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
import io.github.recrafter.lapis.LapisOptions
import io.github.recrafter.lapis.annotations.InitStrategy
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.extensions.InternalPrefix.*
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ksp.createResourceFile
import io.github.recrafter.lapis.extensions.withInternalPrefix
import io.github.recrafter.lapis.phases.builtins.Builtins
import io.github.recrafter.lapis.phases.builtins.DescriptorWrapperBuiltin
import io.github.recrafter.lapis.phases.builtins.LocalVarImplBuiltin
import io.github.recrafter.lapis.phases.builtins.SimpleBuiltin
import io.github.recrafter.lapis.phases.common.JvmClassName
import io.github.recrafter.lapis.phases.generator.builders.*
import io.github.recrafter.lapis.phases.lowering.IrModifier
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.asIrParameterizedTypeName
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.models.*
import io.github.recrafter.lapis.phases.lowering.types.IrTypeVariableName
import io.github.recrafter.lapis.phases.lowering.types.orVoid
import kotlinx.serialization.json.Json
import org.objectweb.asm.Opcodes
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Mutable
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.gen.Accessor
import org.spongepowered.asm.mixin.gen.Invoker
import org.spongepowered.asm.mixin.injection.*
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

class MixinGenerator(
    private val options: LapisOptions,
    private val builtins: Builtins,
    private val codeGenerator: CodeGenerator,
    private val logger: LapisLogger,
) {
    private val extensionProperties: MutableList<KPProperty> = mutableListOf()
    private val extensionFunctions: MutableList<KPFunction> = mutableListOf()
    private val extensionOriginatingFiles: MutableList<KSFile> = mutableListOf()

    fun generate(schemas: List<IrSchema>, patches: List<IrPatch>) {
        generateDescriptorWrapperImpls(schemas.flatMap { it.descriptors })
        patches.forEach { patch ->
            patch.impl?.let { generatePatchImpl(patch, it) }
            patch.mixin.bridge?.let { generateMixinBridge(patch, it) }
            generateMixin(patch)
        }
        schemas.mapNotNull { it.mixinAccessor }.forEach { generateMixinAccessor(it) }
        generateExtensions()

        generateMixinConfig(schemas.mapNotNull { it.mixinAccessor }, patches)
        val tweakerAccessors = schemas.mapNotNull { it.tweakerAccessor }
        if (tweakerAccessors.isNotEmpty()) {
            generateTweakerAccessorConfigs(tweakerAccessors)
        }
    }

    private fun generateDescriptorWrapperImpls(descriptors: List<IrDescriptor>) {
        if (descriptors.isEmpty()) {
            return
        }
        val originatingFiles = mutableListOf<KSFile>()
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
                            originatingFiles += it.originatingFiles
                        }
                        descriptor.callWrapperImpl?.let {
                            builtins.generateDescriptorWrapperImpl(this, DescriptorWrapperBuiltin.Call, it)
                            originatingFiles += it.originatingFiles
                        }
                        descriptor.cancelWrapperImpl?.let {
                            builtins.generateDescriptorWrapperImpl(this, DescriptorWrapperBuiltin.Cancel, it)
                            originatingFiles += it.originatingFiles
                        }
                    }

                    is IrFieldDescriptor -> {
                        descriptor.fieldGetWrapperImpl?.let {
                            builtins.generateDescriptorWrapperImpl(this, DescriptorWrapperBuiltin.FieldGet, it)
                            originatingFiles += it.originatingFiles
                        }
                        descriptor.fieldSetWrapperImpl?.let {
                            builtins.generateDescriptorWrapperImpl(this, DescriptorWrapperBuiltin.FieldSet, it)
                            originatingFiles += it.originatingFiles
                        }
                        descriptor.arrayGetWrapperImpl?.let {
                            builtins.generateDescriptorWrapperImpl(this, DescriptorWrapperBuiltin.ArrayGet, it)
                            originatingFiles += it.originatingFiles
                        }
                        descriptor.arraySetWrapperImpl?.let {
                            builtins.generateDescriptorWrapperImpl(this, DescriptorWrapperBuiltin.ArraySet, it)
                            originatingFiles += it.originatingFiles
                        }
                    }
                }
            }
        }.writeTo(codeGenerator, aggregating = true, originatingFiles)
    }

    private fun generateMixin(patch: IrPatch) {
        buildJavaFile(patch.mixin.className) {
            buildJavaClass(patch.mixin.className.simpleName) {
                addAnnotation<Mixin> {
                    setArgumentValue(Mixin::targets, patch.mixin.targetInternalName)
                }
                setModifiers(IrModifier.PUBLIC)
                val patchImplReference = patch.impl?.let { generatePatchInitializer(this, it, patch.mixin) }
                patch.mixin.bridge?.let { bridge ->
                    if (patchImplReference == null) {
                        lapisError("Patch impl reference cannot be null")
                    }
                    addSuperInterface(bridge.className)
                    addMethods(bridge.functions.flatMap { function ->
                        function.accessors.map { accessor ->
                            buildJavaMethod(accessor.name) {
                                setModifiers(IrModifier.PUBLIC, IrModifier.OVERRIDE)
                                setParameters(accessor.parameters)
                                setReturnType(accessor.returnTypeName)
                                setBody {
                                    when (function.impl) {
                                        is IrBridgeFunctionExtensionImpl -> {
                                            val parametersFormat = accessor.parameters.joinToString { "%N" }
                                            code_(
                                                format = "${patchImplReference.format}.%L($parametersFormat)",
                                                isReturn = accessor.returnTypeName != null,
                                            ) {
                                                when (patchImplReference) {
                                                    is PatchImplFieldReference -> arg(patchImplReference.field)
                                                    is PatchImplMethodReference -> arg(patchImplReference.method)
                                                }
                                                arg(accessor.sourceJvmName)
                                                accessor.parameters.forEach(::arg)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    })
                }
                addMethods(patch.mixin.injections.map {
                    buildMixinInjectionMethod(it, patch, patchImplReference)
                })
            }
        }.writeTo(codeGenerator, aggregating = false, patch.mixin.originatingFiles)
    }

    private fun generatePatchImpl(patch: IrPatch, impl: IrPatchImpl) {
        buildKotlinFile(impl.className) {
            suppressWarnings(KSuppressWarning.RedundantVisibilityModifier)
            addType(buildPatchImplClass(patch, impl))
        }.writeTo(codeGenerator, aggregating = false, impl.originatingFiles)
    }

    private fun buildPatchImplClass(patch: IrPatch, impl: IrPatchImpl): KPClass =
        buildKotlinClass(impl.className.simpleName) {
            setModifiers(IrModifier.PUBLIC)
            val instanceParameterName = "instance"
            val constructorParameters = impl.constructorParameters.map { parameter ->
                when (parameter) {
                    is IrPatchImplConstructorInstanceParameter -> {
                        IrParameter(instanceParameterName, patch.mixin.targetInstanceTypeName)
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
                        is IrPatchConstructorOriginArgument -> instanceParameterName.toKotlinCodeBlock()
                    }
                }
            )
        }

    private fun generatePatchInitializer(
        destination: JPClassBuilder,
        impl: IrPatchImpl,
        mixin: IrMixin,
    ): PatchImplReference {
        val constructorArgumentCodeBlocks = impl.constructorParameters.map { parameter ->
            when (parameter) {
                is IrPatchImplConstructorInstanceParameter -> {
                    val isDoubleCastRequired = mixin.targetInstanceTypeName != KPAny.asIrClassName()
                    val isObjectCastRequired = !mixin.isInterfaceTarget
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
                            arg(mixin.targetInstanceTypeName)
                            if (isObjectCastRequired) {
                                arg(Object::class)
                            }
                        }
                    }
                }
            }
        }
        val initializerCodeBlock = buildJavaCodeBlock(
            buildString {
                append("new %T(")
                append(constructorArgumentCodeBlocks.joinToString { "%L" })
                append(")")
            }
        ) {
            arg(impl.className)
            constructorArgumentCodeBlocks.forEach(::arg)
        }
        val isEagerStrategy = impl.initStrategy == InitStrategy.Eager
        val isSynchronizedStrategy = impl.initStrategy == InitStrategy.Synchronized
        val isThreadSafeStrategy = impl.initStrategy == InitStrategy.Volatile || isSynchronizedStrategy
        val patchField = buildJavaField("patch".withInternalPrefix(), impl.className) {
            addAnnotation<Unique>()
            setModifiers(
                listOfNotNull(
                    IrModifier.PRIVATE,
                    if (isEagerStrategy) IrModifier.FINAL else null,
                    if (isThreadSafeStrategy) IrModifier.VOLATILE else null,
                )
            )
            if (isEagerStrategy) {
                initializer(initializerCodeBlock)
            }
        }.also(destination::addField)
        if (isEagerStrategy) {
            return PatchImplFieldReference(patchField)
        }
        val synchronizedLockField = if (isSynchronizedStrategy) {
            buildJavaField("patchLock".withInternalPrefix(), Object::class.asIrTypeName()) {
                addAnnotation<Unique>()
                setModifiers(IrModifier.PRIVATE, IrModifier.FINAL)
                initializer(buildJavaCodeBlock("new %T()") { arg(Object::class) })
            }.also(destination::addField)
        } else null
        val getOrInitPatchMethod = buildJavaMethod("getOrInitPatch".withInternalPrefix()) {
            addAnnotation<Unique>()
            setModifiers(IrModifier.PRIVATE)
            setReturnType(impl.className)
            setBody {
                fun IrJavaMethodBody.ifFieldNull_(body: Builder<IrJavaCodeBlock>) {
                    if_(buildJavaCodeBlock("%N == null") { arg(patchField) }, body)
                }

                fun IrJavaMethodBody.initField_(value: JPCodeBlock) {
                    code_("%N = %L") { arg(patchField); arg(value) }
                }
                if (isThreadSafeStrategy) {
                    val localName = "local"
                    code_("%T %L = %N") { arg(impl.className); arg(localName); arg(patchField) }

                    fun IrJavaMethodBody.ifLocalNull_(body: Builder<IrJavaCodeBlock>) {
                        if_(buildJavaCodeBlock("%L == null") { arg(localName) }, body)
                    }

                    fun IrJavaMethodBody.initLocal_(value: JPCodeBlock) {
                        code_(buildJavaCodeBlock("%L = %L") { arg(localName); arg(value) })
                    }

                    ifLocalNull_ {
                        if (synchronizedLockField != null) {
                            synchronized_(synchronizedLockField.toCodeBlock()) {
                                initLocal_(patchField.toCodeBlock())
                                ifLocalNull_ {
                                    initLocal_(initializerCodeBlock)
                                    initField_(localName.toJavaCodeBlock())
                                }
                            }
                        } else {
                            initLocal_(initializerCodeBlock)
                            initField_(localName.toJavaCodeBlock())
                        }
                    }
                    return_(localName.toJavaCodeBlock())
                } else {
                    ifFieldNull_ {
                        initField_(initializerCodeBlock)
                    }
                    return_(patchField.toCodeBlock())
                }
            }
        }.also(destination::addMethod)
        return PatchImplMethodReference(getOrInitPatchMethod)
    }

    sealed interface PatchImplReference {
        val format: String
    }

    class PatchImplFieldReference(val field: JPField) : PatchImplReference {
        override val format: String = "%N"
    }

    class PatchImplMethodReference(val method: JPMethod) : PatchImplReference {
        override val format: String = "%N()"
    }

    private fun buildMixinInjectionMethod(
        injection: IrInjection,
        patch: IrPatch,
        patchImplReference: PatchImplReference?,
    ): JPMethod =
        buildJavaMethod(buildString {
            append(injection.jvmName)
            injection.ordinal?.let { append("_ordinal${it}") }
        }) {
            val hasCancelArgument = injection.hookArguments.any { it is IrHookCancelDescriptorWrapperImplArgument }
            when (injection) {
                is IrWrapMethodInjection -> addAnnotation<WrapMethod> {
                    setArgumentValue(WrapMethod::method, injection.methodMixinReference)
                }

                is IrInjectInjection -> addAnnotation<Inject> {
                    setArgumentValue(Inject::method, injection.methodMixinReference)
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
                    setArgumentValue(ModifyVariable::method, injection.methodMixinReference)
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
                    setArgumentValue(ModifyReturnValue::method, injection.methodMixinReference)
                    setArgumentValue<ModifyReturnValue, At>(ModifyReturnValue::at) {
                        setArgumentValue(At::value, "RETURN")
                        injection.ordinal?.let { setArgumentValue(At::ordinal, it) }
                        setArgumentValue(At::unsafe, true)
                    }
                }

                is IrWrapOperationInjection -> addAnnotation<WrapOperation> {
                    setArgumentValue(WrapOperation::method, injection.methodMixinReference)
                    setArgumentValue<WrapOperation, At>(WrapOperation::at) {
                        setArgumentValue(At::value, if (injection.isConstructorCall) "NEW" else "INVOKE")
                        setArgumentValue(At::target, injection.targetMixinReference)
                        injection.ordinal?.let { setArgumentValue(At::ordinal, it) }
                        setArgumentValue(At::unsafe, true)
                    }
                }

                is IrModifyExpressionValueInjection -> addAnnotation<ModifyExpressionValue> {
                    setArgumentValue(ModifyExpressionValue::method, injection.methodMixinReference)
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
                    setArgumentValue(WrapOperation::method, injection.methodMixinReference)
                    setArgumentValue<WrapOperation, At>(WrapOperation::at) {
                        setArgumentValue(At::value, "FIELD")
                        setArgumentValue(At::target, injection.targetMixinReference)
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
                    setArgumentValue(Redirect::method, injection.methodMixinReference)
                    setArgumentValue<Redirect, At>(Redirect::at) {
                        setArgumentValue(At::value, "FIELD")
                        setArgumentValue(At::target, injection.targetMixinReference)
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
                    setArgumentValue(WrapOperation::method, injection.methodMixinReference)
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
                                        parameter.namespace?.let { setArgumentValue(Share::namespace, it) }
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
                    is IrHookOriginValueArgument -> valueParameterName.toJavaCodeBlock()
                    is IrHookOriginDescriptorWrapperImplArgument<*> -> {
                        val descriptorWrapperConstructorArgumentCodeBlocks = buildList {
                            val impl = argument.wrapperImpl
                            if (
                                injection is IrTargetInjection &&
                                injection !is IrWrapMethodInjection &&
                                injection !is IrArrayInjection &&
                                !injection.isStaticTarget
                            ) {
                                add(receiverParameterName.toJavaCodeBlock())
                            }
                            if (injection is IrFieldSetInjection) {
                                add("value".withInternalPrefix(ARGUMENT).toJavaCodeBlock())
                            }
                            if (impl is IrInvokableDescriptorWrapperImpl) {
                                addAll(impl.parameters.mapIndexed { index, parameter ->
                                    (parameter.name ?: index.toString()).withInternalPrefix(ARGUMENT).toJavaCodeBlock()
                                })
                            }
                            if (injection is IrInstanceofInjection) {
                                add("value".withInternalPrefix(ARGUMENT).toJavaCodeBlock())
                            }
                            if (injection is IrArrayInjection) {
                                add("array".withInternalPrefix(ARGUMENT).toJavaCodeBlock())
                                add("index".withInternalPrefix(ARGUMENT).toJavaCodeBlock())
                                if (injection.op == Op.Set) {
                                    add("value".withInternalPrefix(ARGUMENT).toJavaCodeBlock())
                                }
                            } else {
                                add(originalParameterName.toJavaCodeBlock())
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
                            descriptorWrapperConstructorArgumentCodeBlocks.forEach(::arg)
                        }
                    }

                    is IrHookOriginInstanceofWrapperImplArgument -> {
                        buildJavaCodeBlock("new %T(%L, %L)") {
                            arg(builtins[SimpleBuiltin.Instanceof])
                            arg(valueParameterName)
                            arg(originalParameterName)
                        }
                    }

                    is IrHookCancelDescriptorWrapperImplArgument -> {
                        buildJavaCodeBlock("new %T(%L)") {
                            arg(argument.wrapperImpl.className)
                            arg(callbackParameterName)
                        }
                    }

                    is IrHookOrdinalArgument -> injection.ordinal?.toJavaCodeBlock() ?: lapisError("Ordinal not found")

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
                                append("%T.")
                                append(
                                    if (patch.isObject) "INSTANCE"
                                    else "Companion"
                                )
                            } else {
                                append(patchImplReference?.format ?: lapisError("Patch impl reference cannot be null"))
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
                            when (patchImplReference) {
                                is PatchImplFieldReference -> arg(patchImplReference.field)
                                is PatchImplMethodReference -> arg(patchImplReference.method)
                                else -> lapisError("Patch impl reference cannot be null")
                            }
                        }
                        arg(injection.jvmName)
                        hookArgumentCodeBlocks.forEach(::arg)
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

    private fun generateMixinBridge(patch: IrPatch, bridge: IrBridge) {
        buildKotlinFile(bridge.className) {
            addType(buildKotlinInterface(bridge.className.simpleName) {
                setModifiers(IrModifier.PUBLIC)
                addFunctions(bridge.functions.flatMap { function ->
                    function.accessors.map { accessor ->
                        buildKotlinFunction(accessor.name) {
                            setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
                            setParameters(accessor.parameters)
                            setReturnType(accessor.returnTypeName)
                        }
                    }
                })
            })
        }.writeTo(codeGenerator, aggregating = false, bridge.originatingFiles)

        val bridgeExtensions = bridge.functions.filter { it.impl is IrBridgeFunctionExtensionImpl }
        extensionProperties += bridgeExtensions.filterIsInstance<IrBridgeFunctionProperty>().map { function ->
            buildKotlinProperty(function.sourceName, function.typeName) {
                setReceiverType(patch.mixin.targetInstanceTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%L()") {
                            arg(bridge.className)
                            arg(function.getter.name)
                        }
                    }
                }
                function.setter?.let { setter ->
                    setSetter {
                        setModifiers(IrModifier.INLINE)
                        setParameters(setter.parameters)
                        setBody {
                            val parametersFormat = setter.parameters.joinToString { "%N" }
                            code_("(this as %T).%L($parametersFormat)") {
                                arg(bridge.className)
                                arg(setter.name)
                                setter.parameters.forEach(::arg)
                            }
                        }
                    }
                }
            }
        }
        extensionFunctions += bridgeExtensions.filterIsInstance<IrBridgeFunctionFunction>().map { function ->
            buildKotlinFunction(function.sourceName) {
                setModifiers(IrModifier.INLINE)
                setReceiverType(patch.mixin.targetInstanceTypeName)
                setParameters(function.parameters)
                setReturnType(function.returnTypeName)
                setBody {
                    val parametersFormat = function.parameters.joinToString { "%N" }
                    code_(
                        format = "(this as %T).%L($parametersFormat)",
                        isReturn = function.returnTypeName != null,
                    ) {
                        arg(bridge.className)
                        arg(function.name)
                        function.parameters.forEach(::arg)
                    }
                }
            }
        }
    }

    private fun generateMixinAccessor(accessor: IrMixinAccessor) {
        buildJavaFile(accessor.className) {
            buildJavaInterface(accessor.className.simpleName) {
                addAnnotation<Mixin> {
                    setArgumentValue(Mixin::targets, accessor.targetInternalName)
                }
                setModifiers(IrModifier.PUBLIC)
                accessor.members.forEach { member ->
                    when (member) {
                        is IrMixinAccessorFieldMember -> {
                            val setterParameters = listOf(IrSetterParameter(member.typeName))
                            val opMethods = member.ops.associateWith { op ->
                                val accessorMethod = buildJavaMethod("_access_${op.name.lowercase()}_" + member.name) {
                                    setModifiers(
                                        IrModifier.PUBLIC,
                                        if (member.isStatic) IrModifier.STATIC else IrModifier.ABSTRACT
                                    )
                                    addAnnotation<Accessor> {
                                        setArgumentValue(Accessor::value, member.mappingName)
                                    }
                                    if (op == Op.Set) {
                                        if (member.removeFinal) {
                                            addAnnotation<Mutable>()
                                        }
                                        setParameters(setterParameters)
                                    }
                                    if (op == Op.Get) {
                                        setReturnType(member.typeName)
                                    }
                                    if (member.isStatic) {
                                        setStubBody()
                                    }
                                }.also(::addMethod)
                                accessorMethod
                            }
                            extensionProperties += buildKotlinProperty(
                                if (member.isStatic) "value" else member.name,
                                member.typeName
                            ) {
                                setReceiverType(
                                    if (member.isStatic) member.descriptorClassName
                                    else accessor.receiverTypeName
                                )
                                setGetter {
                                    setModifiers(IrModifier.INLINE)
                                    setBody {
                                        val getter = opMethods[Op.Get]
                                        if (getter != null) {
                                            val interfaceFormat = if (member.isStatic) "%T" else "(this as %T)"
                                            return_("$interfaceFormat.%N()") {
                                                arg(accessor.className)
                                                arg(getter)
                                            }
                                        } else {
                                            throw_("%T()") { arg(UnsupportedOperationException::class.asIrTypeName()) }
                                        }
                                    }
                                }
                                opMethods[Op.Set]?.let { setter ->
                                    setSetter {
                                        setModifiers(IrModifier.INLINE)
                                        setParameters(setterParameters)
                                        setBody {
                                            val interfaceFormat = if (member.isStatic) "%T" else "(this as %T)"
                                            val parametersFormat = setterParameters.joinToString { "%N" }
                                            code_("$interfaceFormat.%N($parametersFormat)") {
                                                arg(accessor.className)
                                                arg(setter)
                                                setterParameters.forEach(::arg)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        is IrMixinAccessorMethodMember -> {
                            val invokerMethod = buildJavaMethod("_access_" + member.name) {
                                setModifiers(
                                    IrModifier.PUBLIC,
                                    if (member.isStatic) IrModifier.STATIC else IrModifier.ABSTRACT
                                )
                                addAnnotation<Invoker> {
                                    setArgumentValue(Invoker::value, member.mappingName)
                                }
                                setParameters(member.parameters)
                                setReturnType(member.returnTypeName)
                                if (member.isStatic) {
                                    setStubBody()
                                }
                            }
                            addMethod(invokerMethod)
                            val isInaccessibleInstance = !accessor.isAccessibleSchema && !member.isStatic
                            extensionFunctions += buildKotlinFunction(
                                member.name,
                                jvmNamespace = if (isInaccessibleInstance) accessor.schemaClassName else null
                            ) {
                                setModifiers(IrModifier.INLINE)
                                if (isInaccessibleInstance) {
                                    setVariableTypes(IrTypeVariableName.of("T", accessor.schemaClassName))
                                }
                                setReceiverType(
                                    if (member.isStatic) accessor.schemaClassName
                                    else accessor.receiverTypeName
                                )
                                setParameters(member.parameters)
                                setReturnType(
                                    if (isInaccessibleInstance) member.returnTypeName?.makeNullable()
                                    else member.returnTypeName
                                )
                                setBody {
                                    val guestFormat = if (isInaccessibleInstance) "?" else ""
                                    val interfaceFormat = if (member.isStatic) "%T" else "(this as$guestFormat %T)"
                                    val parametersFormat = member.parameters.joinToString { "%N" }
                                    code_(
                                        format = "$interfaceFormat$guestFormat.%N($parametersFormat)",
                                        isReturn = member.returnTypeName != null,
                                    ) {
                                        arg(accessor.className)
                                        arg(invokerMethod)
                                        member.parameters.forEach(::arg)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.writeTo(codeGenerator, aggregating = false, accessor.originatingFiles)
    }

    private fun generateExtensions() {
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
        }.writeTo(codeGenerator, aggregating = true, extensionOriginatingFiles)
    }

    private fun generateMixinConfig(mixinAccessors: List<IrMixinAccessor>, patches: List<IrPatch>) {
        val qualifiedNames = buildList {
            addAll(mixinAccessors.map { it.schemaSide to it.className.qualifiedName })
            addAll(patches.map { it.side to it.mixin.className.qualifiedName })
        }.groupBy({ it.first }, { it.second })
        val config = configJson.encodeToString(
            MixinConfig.of(
                mixinPackage = options.mixinPackageName,
                qualifiedNames = qualifiedNames,
            )
        )
        val originatingFiles = (mixinAccessors + patches.map { it.mixin }).flatMap { it.originatingFiles }
        codeGenerator.createResourceFile(options.mixinConfig, config, aggregating = true, originatingFiles)
        logger.info(buildString {
            appendLine("Mixin config generated:")
            append(config)
        })
    }

    private fun generateTweakerAccessorConfigs(accessors: List<IrTweakerAccessor>) {
        val originatingFiles = accessors.flatMap { it.originatingFiles }
        options.accessWidenerConfig?.let { configPath ->
            val header = if (options.isUnobfuscated) "classTweaker v1 official" else "accessWidener v2 named"
            val config = buildTweakerAccessorConfig(accessors, header, IrTweakerAccessorEntry::buildWidenerTweak)
            codeGenerator.createResourceFile(configPath, config, aggregating = true, originatingFiles)
            logger.info(buildString {
                appendLine("Access Widener config generated:")
                append(config)
            })
        }
        options.accessTransformerConfig?.let { configPath ->
            val config = buildTweakerAccessorConfig(accessors, tweak = IrTweakerAccessorEntry::buildTransformerTweak)
            codeGenerator.createResourceFile(configPath, config, aggregating = true, originatingFiles)
            logger.info(buildString {
                appendLine("Access Transformer config generated:")
                append(config)
            })
        }
    }

    private fun buildTweakerAccessorConfig(
        accessors: List<IrTweakerAccessor>,
        header: String? = null,
        tweak: (IrTweakerAccessorEntry, JvmClassName) -> String
    ): String = buildString {
        header?.let {
            appendLine(it)
            appendLine()
        }
        accessors.forEach {
            appendLine("# ${it.ownerJvmClassName.nestedName}")
            it.entries.forEach { entry ->
                appendLine(tweak(entry, it.ownerJvmClassName))
            }
            appendLine()
        }
    }
}

private val configJson: Json = Json { prettyPrint = true }
