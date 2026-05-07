package io.github.recrafter.lapis.phases.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
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
import io.github.recrafter.lapis.extensions.withInternalPrefix
import io.github.recrafter.lapis.phases.builtins.Builtins
import io.github.recrafter.lapis.phases.builtins.LocalVarImplBuiltin
import io.github.recrafter.lapis.phases.builtins.SimpleBuiltin
import io.github.recrafter.lapis.phases.common.JvmClassName
import io.github.recrafter.lapis.phases.generator.builders.*
import io.github.recrafter.lapis.phases.lowering.IrModifier
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.asIrParameterizedTypeName
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.models.*
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
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
import java.io.File

class MixinGenerator(
    private val options: LapisOptions,
    private val builtins: Builtins,
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: LapisLogger,
) {
    fun generate(schemas: List<IrSchema>, patches: List<IrPatch>) {
        generateDescriptorWrapperImpls(schemas.flatMap { it.descriptors })
        patches.forEach { patch ->
            patch.impl?.let { generatePatchImpl(patch, it) }
            patch.mixin.bridge?.let { generateBridge(patch, it) }
            generateMixin(patch)
        }
        schemas.mapNotNull { it.mixinAccessor }.forEach(::generateMixinAccessor)

        generateMixinConfig(patches.map { it.mixin } + schemas.mapNotNull { it.mixinAccessor })
        val tweakAccessors = schemas.mapNotNull { it.tweakAccessor }
        if (tweakAccessors.isNotEmpty()) {
            generateTweakAccessorConfigs(tweakAccessors)
        }
    }

    private fun generateDescriptorWrapperImpls(descriptors: List<IrDescriptor>) {
        descriptors.forEach { descriptor ->
            when (descriptor) {
                is IrInvokableDescriptor -> {
                    descriptor.bodyWrapperImpl?.let { generateDescriptorWrapperImpl(it) }
                    descriptor.callWrapperImpl?.let { generateDescriptorWrapperImpl(it) }
                    descriptor.cancelWrapperImpl?.let { generateDescriptorWrapperImpl(it) }
                }

                is IrFieldDescriptor -> {
                    descriptor.fieldGetWrapperImpl?.let { generateDescriptorWrapperImpl(it) }
                    descriptor.fieldSetWrapperImpl?.let { generateDescriptorWrapperImpl(it) }
                    descriptor.arrayGetWrapperImpl?.let { generateDescriptorWrapperImpl(it) }
                    descriptor.arraySetWrapperImpl?.let { generateDescriptorWrapperImpl(it) }
                }
            }
        }
    }

    private fun <T : IrDescriptorWrapperImpl<T>> generateDescriptorWrapperImpl(impl: T) {
        generateKotlinFile(impl, aggregating = false) {
            suppressWarnings(
                KSuppressWarning.RedundantVisibilityModifier,
                KSuppressWarning.NothingToInline,
                KSuppressWarning.LocalVariableName,
            )
            val superClassTypeName = builtins[impl.wrapperBuiltin].parameterizedBy(impl.descriptorClassName)
            builtins.generateDescriptorWrapperImpl(this, impl, superClassTypeName)
        }
    }

    private fun generateMixin(patch: IrPatch) {
        generateJavaFile(patch.mixin, aggregating = false) {
            addAnnotation<Mixin> {
                setArgumentValue(Mixin::targets, listOf(patch.mixin.targetInternalName))
            }
            setModifiers(IrModifier.PUBLIC)
            val patchImplReference = patch.impl?.let { generatePatchInitializer(this, it, patch.mixin) }
            patch.mixin.bridge?.let { bridge ->
                addSuperInterface(bridge.className)
                addMethods(bridge.entries.flatMap { entry ->
                    entry.kinds.map { kind ->
                        buildJavaMethod(kind.name) {
                            setModifiers(IrModifier.PUBLIC, IrModifier.OVERRIDE)
                            setParameters(kind.parameters)
                            setReturnType(kind.returnTypeName)
                            setBody {
                                when (entry.impl) {
                                    is IrMixinBridgeEntryExtensionImpl -> {
                                        val patchImplReferenceFormat = patchImplReference?.format
                                            ?: lapisError("Patch impl reference cannot be null")
                                        val parametersFormat = kind.parameters.joinToString { "%N" }
                                        code_(
                                            format = "$patchImplReferenceFormat.%L($parametersFormat)",
                                            isReturn = kind.returnTypeName != null,
                                        ) {
                                            when (patchImplReference) {
                                                is PatchImplFieldReference -> arg(patchImplReference.field)
                                                is PatchImplMethodReference -> arg(patchImplReference.method)
                                            }
                                            arg(kind.sourceJvmName)
                                            kind.parameters.forEach(::arg)
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
    }

    private fun generatePatchImpl(patch: IrPatch, impl: IrPatchImpl) {
        generateKotlinFile(impl, aggregating = false) {
            suppressWarnings(KSuppressWarning.RedundantVisibilityModifier)
            addType(buildKotlinClass(impl.className.simpleName) {
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
            })
        }
    }

    private fun generatePatchInitializer(
        destination: JPClassBuilder,
        impl: IrPatchImpl,
        mixin: IrMixin,
    ): PatchImplReference {
        val constructorArgumentCodeBlocks = impl.constructorParameters.map { parameter ->
            when (parameter) {
                is IrPatchImplConstructorInstanceParameter -> {
                    if (mixin.targetInstanceTypeName != KPAny.asIrClassName()) {
                        if (!mixin.isInterfaceTarget) {
                            buildJavaCodeBlock("(%T) (%T) this") {
                                arg(mixin.targetInstanceTypeName)
                                arg(Object::class)
                            }
                        } else {
                            buildJavaCodeBlock("(%T) this") { arg(mixin.targetInstanceTypeName) }
                        }
                    } else {
                        buildJavaCodeBlock("this")
                    }
                }
            }
        }
        val codeBlocksFormat = constructorArgumentCodeBlocks.joinToString { "%L" }
        val initializerCodeBlock = buildJavaCodeBlock("new %T($codeBlocksFormat)") {
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

    private sealed interface PatchImplReference {
        val format: String
    }

    private class PatchImplFieldReference(val field: JPField) : PatchImplReference {
        override val format: String = "%N"
    }

    private class PatchImplMethodReference(val method: JPMethod) : PatchImplReference {
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
                    setArgumentValue(WrapMethod::method, listOf(injection.methodMixinReference))
                }

                is IrInjectInjection -> addAnnotation<Inject> {
                    setArgumentValue(Inject::method, listOf(injection.methodMixinReference))
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
                            setArgumentValue(At::args, injection.atArgs.map { "${it.first}=${it.second}" })
                        }
                        injection.ordinal?.let { setArgumentValue(At::ordinal, it) }
                        setArgumentValue(At::unsafe, true)
                    }
                    if (hasCancelArgument) {
                        setArgumentValue(Inject::cancellable, true)
                    }
                }

                is IrModifyVariableInjection -> addAnnotation<ModifyVariable> {
                    setArgumentValue(ModifyVariable::method, listOf(injection.methodMixinReference))
                    when (val local = injection.local) {
                        is IrNamedLocal -> setArgumentValue(ModifyVariable::name, listOf(local.name))
                        is IrPositionalLocal -> setArgumentValue(ModifyVariable::ordinal, local.ordinal)
                    }
                    setArgumentValue<ModifyVariable, At>(ModifyVariable::at) {
                        val atCode = when (injection.op) {
                            Op.Get -> "LOAD"
                            Op.Set -> "STORE"
                        }
                        setArgumentValue(At::value, atCode)
                        injection.ordinal?.let { setArgumentValue(At::ordinal, it) }
                        setArgumentValue(At::unsafe, true)
                    }
                }

                is IrModifyReturnValueInjection -> addAnnotation<ModifyReturnValue> {
                    setArgumentValue(ModifyReturnValue::method, listOf(injection.methodMixinReference))
                    setArgumentValue<ModifyReturnValue, At>(ModifyReturnValue::at) {
                        setArgumentValue(At::value, "RETURN")
                        injection.ordinal?.let { setArgumentValue(At::ordinal, it) }
                        setArgumentValue(At::unsafe, true)
                    }
                }

                is IrWrapOperationInjection -> addAnnotation<WrapOperation> {
                    setArgumentValue(WrapOperation::method, listOf(injection.methodMixinReference))
                    setArgumentValue<WrapOperation, At>(WrapOperation::at) {
                        setArgumentValue(At::value, if (injection.isConstructorCall) "NEW" else "INVOKE")
                        setArgumentValue(At::target, injection.targetMixinReference)
                        injection.ordinal?.let { setArgumentValue(At::ordinal, it) }
                        setArgumentValue(At::unsafe, true)
                    }
                }

                is IrModifyExpressionValueInjection -> addAnnotation<ModifyExpressionValue> {
                    setArgumentValue(ModifyExpressionValue::method, listOf(injection.methodMixinReference))
                    setArgumentValue<ModifyExpressionValue, At>(ModifyExpressionValue::at) {
                        setArgumentValue(At::value, "CONSTANT")
                        setArgumentValue(At::args, injection.atArgs.map { "${it.first}=${it.second}" })
                        injection.ordinal?.let { setArgumentValue(At::ordinal, it) }
                        setArgumentValue(At::unsafe, true)
                    }
                }

                is IrFieldGetInjection, is IrFieldSetInjection -> addAnnotation<WrapOperation> {
                    setArgumentValue(WrapOperation::method, listOf(injection.methodMixinReference))
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
                    setArgumentValue(Redirect::method, listOf(injection.methodMixinReference))
                    setArgumentValue<Redirect, At>(Redirect::at) {
                        setArgumentValue(At::value, "FIELD")
                        setArgumentValue(At::target, injection.targetMixinReference)
                        setArgumentValue(
                            At::opcode,
                            if (injection.isStaticTarget) Opcodes.GETSTATIC else Opcodes.GETFIELD
                        )
                        setArgumentValue(At::args, injection.atArgs.map { "${it.first}=${it.second}" })
                        injection.ordinal?.let { setArgumentValue(At::ordinal, it) }
                        setArgumentValue(At::unsafe, true)
                    }
                }

                is IrInstanceofInjection -> addAnnotation<WrapOperation> {
                    setArgumentValue(WrapOperation::method, listOf(injection.methodMixinReference))
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
                        buildJavaParameter(parameter.name.withInternalPrefix(ARGUMENT), parameter.typeName)
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
                                            is IrNamedLocal -> setArgumentValue(Local::name, listOf(local.name))
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
                        val codeBlocksFormat = descriptorWrapperConstructorArgumentCodeBlocks.joinToString { "%L" }
                        buildJavaCodeBlock("new %T($codeBlocksFormat)") {
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

                    is IrHookOrdinalArgument -> injection.ordinal?.toJavaCodeBlock()
                        ?: lapisError("Ordinal cannot be null")

                    is IrHookLocalArgument -> {
                        val localName = argument.name.withInternalPrefix(
                            when {
                                argument.isBody -> LOCAL
                                argument.isShare -> SHARE
                                injection is IrInjectInjection -> ARGUMENT
                                else -> PARAM
                            }
                        )
                        if (argument.varBuiltin != null) {
                            buildJavaCodeBlock(
                                buildString {
                                    append("new %T")
                                    if (argument.varBuiltin == LocalVarImplBuiltin.ObjectLocalVar) {
                                        append("<>")
                                    }
                                    append("(%L)")
                                }
                            ) { arg(builtins[argument.varBuiltin]); arg(localName) }
                        } else {
                            buildJavaCodeBlock("%L") { arg(localName) }
                        }
                    }
                }
            }
            setBody {
                val invokeHook: Builder<IrJavaCodeBlock> = {
                    val patchInstanceFormat = if (injection.isStatic) {
                        "%T." + if (patch.isObject) "INSTANCE" else "Companion"
                    } else {
                        patchImplReference?.format ?: lapisError("Patch impl reference cannot be null")
                    }
                    val codeBlocksFormat = hookArgumentCodeBlocks.joinToString { "%L" }
                    code_(
                        format = "$patchInstanceFormat.%L($codeBlocksFormat)",
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

    private fun generateBridge(patch: IrPatch, bridge: IrMixinBridge) {
        generateKotlinFile(bridge, aggregating = false) {
            suppressWarnings(KSuppressWarning.RedundantVisibilityModifier)
            addType(buildKotlinInterface(bridge.className.simpleName) {
                setModifiers(IrModifier.PUBLIC)
                addFunctions(bridge.entries.flatMap { entry ->
                    entry.kinds.map { kind ->
                        buildKotlinFunction(kind.name) {
                            setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
                            setParameters(kind.parameters)
                            setReturnType(kind.returnTypeName)
                        }
                    }
                })
            })
        }
        val bridgeExtensions = bridge.entries.filter { it.impl is IrMixinBridgeEntryExtensionImpl }
        val extensionProperties = bridgeExtensions.filterIsInstance<IrMixinBridgeEntryProperty>().map { function ->
            buildKotlinProperty(function.sourceName, function.typeName) {
                setReceiverType(patch.mixin.targetInstanceTypeName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%L()") { arg(bridge.className); arg(function.getter.name) }
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
        val extensionFunctions = bridgeExtensions.filterIsInstance<IrMixinBridgeEntryFunctionKind>().map { function ->
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
        if (extensionProperties.isNotEmpty() || extensionFunctions.isNotEmpty()) {
            generateExtensions(
                sourceClassName = patch.className,
                properties = extensionProperties,
                functions = extensionFunctions,
                originatingFiles = bridge.originatingFiles,
            )
        }
    }

    private fun generateMixinAccessor(accessor: IrMixinAccessor) {
        val extensionProperties = mutableListOf<KPProperty>()
        val extensionFunctions = mutableListOf<KPFunction>()
        generateJavaFile(accessor, aggregating = false) {
            addAnnotation<Mixin> {
                setArgumentValue(Mixin::targets, listOf(accessor.targetInternalName))
            }
            setModifiers(IrModifier.PUBLIC)
            accessor.members.forEach { member ->
                val isDelegated = !accessor.isAccessibleSchema && !member.isStatic
                val delegateParameter = if (isDelegated) {
                    IrParameter("delegate", accessor.receiverTypeName)
                } else null
                val jvmNamespace = if (isDelegated) member.schemaReceiverClassName else null
                val extensionReceiverTypeName = if (member.isStatic || isDelegated) {
                    member.schemaReceiverClassName
                } else {
                    accessor.receiverTypeName
                }
                val interfaceCodeBlock = buildKotlinCodeBlock(
                    when {
                        delegateParameter != null -> "(%N as %T)"
                        member.isStatic -> "%T"
                        else -> "(this as %T)"
                    }
                ) { delegateParameter?.let(::arg); arg(accessor.className) }
                when (member) {
                    is IrMixinAccessorFieldMember -> {
                        val methods = member.ops.associateWith { op ->
                            buildJavaMethod((op.name.lowercase() + "_" + member.name).withInternalPrefix(ACCESS)) {
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
                                    setParameters(listOf(IrSetterParameter(member.typeName)))
                                }
                                if (op == Op.Get) {
                                    setReturnType(member.typeName)
                                }
                                if (member.isStatic) {
                                    setStubBody()
                                }
                            }.also(::addMethod)
                        }
                        extensionProperties += buildKotlinProperty(
                            if (member.isStatic || isDelegated) "value" else member.name,
                            member.typeName,
                            jvmNamespace = jvmNamespace
                        ) {
                            delegateParameter?.let { setContextParameters(listOf(it)) }
                            setReceiverType(extensionReceiverTypeName)
                            setGetter {
                                setModifiers(IrModifier.INLINE)
                                methods[Op.Get]?.let { getterMethod ->
                                    setBody {
                                        return_("%L.%N()") { arg(interfaceCodeBlock); arg(getterMethod) }
                                    }
                                } ?: setStubBody()
                            }
                            methods[Op.Set]?.let { setterMethod ->
                                setSetter {
                                    val setterParameter = IrSetterParameter(member.typeName)
                                    setModifiers(IrModifier.INLINE)
                                    setParameters(listOf(setterParameter))
                                    setBody {
                                        code_("%L.%N(%N)") {
                                            arg(interfaceCodeBlock)
                                            arg(setterMethod)
                                            arg(setterParameter)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is IrMixinAccessorMethodMember -> {
                        val invokerMethod = buildJavaMethod(member.name.withInternalPrefix(ACCESS)) {
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
                        }.also(::addMethod)
                        extensionFunctions += buildKotlinFunction(member.name, jvmNamespace = jvmNamespace) {
                            setModifiers(IrModifier.INLINE)
                            delegateParameter?.let { setContextParameters(listOf(it)) }
                            setReceiverType(extensionReceiverTypeName)
                            setParameters(member.parameters)
                            setReturnType(member.returnTypeName)
                            setBody {
                                val parametersFormat = member.parameters.joinToString { "%N" }
                                code_(
                                    format = "%L.%N($parametersFormat)",
                                    isReturn = member.returnTypeName != null,
                                ) {
                                    arg(interfaceCodeBlock)
                                    arg(invokerMethod)
                                    member.parameters.forEach(::arg)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (extensionProperties.isNotEmpty() || extensionFunctions.isNotEmpty()) {
            generateExtensions(
                sourceClassName = accessor.schemaClassName,
                properties = extensionProperties,
                functions = extensionFunctions,
                originatingFiles = accessor.originatingFiles,
            )
        }
    }

    private class IrExtensions(
        override val originatingFiles: List<KSFile>,
        override val className: IrClassName,
    ) : IrKotlinBlueprint()

    private fun generateExtensions(
        sourceClassName: IrClassName,
        properties: List<KPProperty>,
        functions: List<KPFunction>,
        originatingFiles: List<KSFile>,
    ) {
        val blueprint = IrExtensions(originatingFiles, sourceClassName.derived("Extensions"))
        generateKotlinFile(blueprint, aggregating = false) {
            suppressWarnings(
                KSuppressWarning.RedundantVisibilityModifier,
                KSuppressWarning.UnusedReceiverParameter,
                KSuppressWarning.NothingToInline,
                KSuppressWarning.CastNeverSucceeds,
            )
            addProperties(properties)
            addFunctions(functions)
        }
    }

    private class IrMixinConfig(
        override val originatingFiles: List<KSFile>,
        path: String,
    ) : IrResourceBlueprint(path)

    private fun generateMixinConfig(mixinBlueprints: List<IrMixinRelatedBlueprint>) {
        val configBlueprint = IrMixinConfig(mixinBlueprints.flatMap { it.originatingFiles }, options.mixinConfig)
        generateResourceFile(configBlueprint, aggregating = true) {
            val qualifiedNames = mixinBlueprints.map { it.side to it.className }.groupBy({ it.first }) { it.second }
            configJson.encodeToString(MixinConfig.of(options.generatedMixinPackageName, qualifiedNames))
        }
    }

    private class IrTweakAccessorConfig(
        override val originatingFiles: List<KSFile>,
        path: String,
    ) : IrResourceBlueprint(path)

    private fun generateTweakAccessorConfigs(accessors: List<IrTweakAccessor>) {
        val originatingFiles = accessors.flatMap { it.originatingFiles }
        options.accessWidenerConfig?.let { configPath ->
            val blueprint = IrTweakAccessorConfig(originatingFiles, configPath)
            generateResourceFile(blueprint, aggregating = true) {
                val header = if (options.isUnobfuscated) "classTweaker v1 official" else "accessWidener v2 named"
                buildTweakAccessorConfig(accessors, header, buildTweak = IrTweakAccessorEntry::buildWidenerTweak)
            }
        }
        options.accessTransformerConfig?.let { configPath ->
            val blueprint = IrTweakAccessorConfig(originatingFiles, configPath)
            generateResourceFile(blueprint, aggregating = true) {
                buildTweakAccessorConfig(accessors, buildTweak = IrTweakAccessorEntry::buildTransformerTweak)
            }
        }
    }

    private fun buildTweakAccessorConfig(
        accessors: List<IrTweakAccessor>,
        header: String? = null,
        buildTweak: (IrTweakAccessorEntry, JvmClassName) -> String
    ): String = buildString {
        header?.let {
            appendLine(it)
            appendLine()
        }
        accessors.forEach {
            appendLine("# ${it.ownerJvmClassName.nestedName}")
            it.entries.forEach { entry ->
                appendLine(buildTweak(entry, it.ownerJvmClassName))
            }
            appendLine()
        }
    }

    private fun generateKotlinFile(
        blueprint: IrKotlinBlueprint,
        aggregating: Boolean,
        builder: Builder<KPFileBuilder> = {}
    ) {
        buildKotlinFile(blueprint.className, builder).writeTo(codeGenerator, aggregating, blueprint.originatingFiles)
    }

    private fun generateJavaFile(
        blueprint: IrJavaBlueprint,
        aggregating: Boolean,
        builder: Builder<JPClassBuilder> = {}
    ) {
        buildJavaFile(blueprint.className) {
            if (blueprint.isInterface) buildJavaInterface(blueprint.className.simpleName, builder)
            else buildJavaClass(blueprint.className.simpleName, builder)
        }.writeTo(codeGenerator, aggregating, blueprint.originatingFiles)
    }

    private fun generateResourceFile(blueprint: IrResourceBlueprint, aggregating: Boolean, builder: () -> String) {
        val file = File(blueprint.path)
        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating, *blueprint.originatingFiles.toList().toTypedArray()),
            packageName = file.parent?.replace(File.separatorChar, '.').orEmpty(),
            fileName = file.nameWithoutExtension,
            extensionName = file.extension,
        ).bufferedWriter().use { it.write(builder().trimEnd() + "\n") }
    }
}

private val configJson: Json = Json { prettyPrint = true }
