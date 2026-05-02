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

        generateDescriptorWrapperImpls(
            schemas.flatMap { it.descriptors },
            schemaOriginatingFiles + patchOriginatingFiles
        )
        patches.forEach { patch ->
            val originatingFiles = listOfNotNull(patch.originatingFile)
            patch.impl?.let { generatePatchImpl(patch, it, originatingFiles) }
            patch.mixin.bridge?.let { generateMixinBridge(patch, it, originatingFiles) }
            generateMixin(patch, originatingFiles)
        }
        generateAccessors(schemas)
        generateExtensions(schemaOriginatingFiles + patchOriginatingFiles)

        generateMixinConfig(patches)
    }

    private fun generateDescriptorWrapperImpls(descriptors: List<IrDescriptor>, originatingFiles: List<KSFile>) {
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

    private fun generateMixin(patch: IrPatch, originatingFiles: List<KSFile>) {
        buildJavaFile(patch.mixin.className) {
            buildMixinClass(patch)
        }.writeTo(codeGenerator, aggregating = false, originatingFiles)
    }

    private fun generatePatchImpl(patch: IrPatch, impl: IrPatchImpl, originatingFiles: List<KSFile>) {
        buildKotlinFile(impl.className) {
            suppressWarnings(KSuppressWarning.RedundantVisibilityModifier)
            addType(buildPatchImplClass(patch, impl))
        }.writeTo(codeGenerator, aggregating = false, originatingFiles)
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

    private fun buildMixinClass(patch: IrPatch): JPClass =
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
                                        code_(
                                            format = "${patchImplReference.format}.%L(%L)",
                                            isReturn = accessor.returnTypeName != null
                                        ) {
                                            when (patchImplReference) {
                                                is PatchImplFieldReference -> arg(patchImplReference.field)
                                                is PatchImplMethodReference -> arg(patchImplReference.method)
                                            }
                                            arg(accessor.sourceJvmName)
                                            arg(accessor.parameters.joinToString { it.name })
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

    private fun generateMixinBridge(patch: IrPatch, bridge: IrBridge, originatingFiles: List<KSFile>) {
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
        }.writeTo(codeGenerator, aggregating = false, originatingFiles)

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
                            code_("(this as %T).%L(%L)") {
                                arg(bridge.className)
                                arg(setter.name)
                                arg(setter.parameters.joinToString { it.name })
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
                    return_("(this as %T).%L(%L)") {
                        arg(bridge.className)
                        arg(function.name)
                        arg(function.parameters.joinToString { it.name })
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
        val config = configJson.encodeToString(
            MixinConfig.of(
                mixinPackage = options.mixinPackageName,
                qualifiedNames = patches.groupBy { it.side }.mapValues { (_, patches) ->
                    patches.map { it.mixin.className.qualifiedName }
                },
            )
        )
        codeGenerator.createResourceFile(options.mixinConfig, config, aggregating = true)
        logger.info(buildString {
            appendLine("Mixin config generated:")
            append(config)
        })
    }

    private fun generateAccessors(schemas: List<IrSchema>) {
        val tweakerAccessors = schemas.mapNotNull { it.tweakerAccessor }
        if (tweakerAccessors.isNotEmpty()) {
            generateTweakerAccessorConfigs(tweakerAccessors)
        }
    }

    private fun generateTweakerAccessorConfigs(accessors: List<IrTweakerAccessor>) {
        options.accessWidenerConfig?.let { configPath ->
            val header = if (options.isUnobfuscated) "classTweaker v1 official" else "accessWidener v2 named"
            val config = buildAccessorTweakerConfig(accessors, header, IrTweakerAccessorEntry::buildWidenerTweak)
            codeGenerator.createResourceFile(configPath, config, aggregating = true)
            logger.info(buildString {
                appendLine("Access Widener config generated:")
                append(config)
            })
        }
        options.accessTransformerConfig?.let { configPath ->
            val config = buildAccessorTweakerConfig(accessors, tweak = IrTweakerAccessorEntry::buildTransformerTweak)
            codeGenerator.createResourceFile(configPath, config, aggregating = true)
            logger.info(buildString {
                appendLine("Access Transformer config generated:")
                append(config)
            })
        }
    }

    private fun buildAccessorTweakerConfig(
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
