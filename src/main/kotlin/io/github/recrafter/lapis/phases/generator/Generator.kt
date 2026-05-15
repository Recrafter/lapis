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
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ksp.writeTo
import io.github.recrafter.lapis.LapisLogger
import io.github.recrafter.lapis.LapisOptions
import io.github.recrafter.lapis.annotations.InitStrategy
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.extensions.common.Builder
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.withInternalPrefix
import io.github.recrafter.lapis.phases.builtins.Builtins
import io.github.recrafter.lapis.phases.builtins.LocalVarImplBuiltin
import io.github.recrafter.lapis.phases.builtins.SimpleBuiltin
import io.github.recrafter.lapis.phases.common.JavaModifiers
import io.github.recrafter.lapis.phases.common.JvmClassName
import io.github.recrafter.lapis.phases.generator.builders.*
import io.github.recrafter.lapis.phases.generator.models.GenExtensionPack
import io.github.recrafter.lapis.phases.generator.models.GenExtensionPackAccumulator
import io.github.recrafter.lapis.phases.generator.models.GenInternalPrefix.*
import io.github.recrafter.lapis.phases.generator.models.GenMixinConfig
import io.github.recrafter.lapis.phases.generator.models.GenTweakAccessorConfig
import io.github.recrafter.lapis.phases.lowering.*
import io.github.recrafter.lapis.phases.lowering.models.*
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrLambdaTypeName
import io.github.recrafter.lapis.phases.lowering.types.orVoid
import kotlinx.serialization.json.Json
import org.objectweb.asm.Opcodes
import org.spongepowered.asm.mixin.*
import org.spongepowered.asm.mixin.gen.Accessor
import org.spongepowered.asm.mixin.gen.Invoker
import org.spongepowered.asm.mixin.injection.*
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

class Generator(
    private val options: LapisOptions,
    private val builtins: Builtins,
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: LapisLogger,
) {
    fun generate(schemas: List<IrSchema>, patches: List<IrPatch>) {
        schemas.forEach { schema ->
            val extensionPackAccumulator = GenExtensionPackAccumulator()
            schema.descriptors.forEach { descriptor ->
                when (descriptor) {
                    is IrInvokableDescriptor -> with(descriptor) {
                        bodyWrapperImpl?.let { generateDescriptorWrapperImpl(it, extensionPackAccumulator) }
                        callWrapperImpl?.let { generateDescriptorWrapperImpl(it, extensionPackAccumulator) }
                        cancelWrapperImpl?.let { generateDescriptorWrapperImpl(it, extensionPackAccumulator) }
                    }

                    is IrFieldDescriptor -> with(descriptor) {
                        fieldGetWrapperImpl?.let { generateDescriptorWrapperImpl(it, extensionPackAccumulator) }
                        fieldSetWrapperImpl?.let { generateDescriptorWrapperImpl(it, extensionPackAccumulator) }
                        arrayGetWrapperImpl?.let { generateDescriptorWrapperImpl(it, extensionPackAccumulator) }
                        arraySetWrapperImpl?.let { generateDescriptorWrapperImpl(it, extensionPackAccumulator) }
                    }
                }
            }
            schema.mixinAccessor?.let { generateMixinAccessor(it, extensionPackAccumulator) }
            if (extensionPackAccumulator.isNotEmpty()) {
                generateExtensionPack(schema.className, extensionPackAccumulator)
            }
        }
        patches.forEach { patch ->
            val extensionPackAccumulator = GenExtensionPackAccumulator()
            patch.impl?.let { generatePatchImpl(it, patch) }
            patch.mixin.externalBridge?.let { generateMixinExternalBridge(it, patch, extensionPackAccumulator) }
            patch.mixin.internalBridge?.let { generateMixinInternalBridge(it) }
            generateMixin(patch.mixin, patch.className, patch.impl, extensionPackAccumulator)
            if (extensionPackAccumulator.isNotEmpty()) {
                generateExtensionPack(patch.className, extensionPackAccumulator)
            }
        }
        generateMixinConfig(schemas.mapNotNull { it.mixinAccessor } + patches.map { it.mixin })
        val tweakAccessors = schemas.mapNotNull { it.tweakAccessor }
        if (tweakAccessors.isNotEmpty()) {
            generateTweakAccessorConfigs(tweakAccessors)
        }
    }

    private fun <T : IrDescriptorWrapperImpl<T>> generateDescriptorWrapperImpl(
        impl: T,
        extensionPackAccumulator: GenExtensionPackAccumulator,
    ) {
        val superClassTypeName = builtins[impl.wrapperBuiltin].parameterizedBy(impl.descriptorClassName)
        val result = builtins.generateDescriptorWrapperImpl(impl, superClassTypeName)
        extensionPackAccumulator.accumulate(result.extensionPackEntities, impl.originatingFiles)
        generateKotlinFile(impl, aggregating = false, suppressNames = listOf("NOTHING_TO_INLINE")) {
            setConstructor(result.constructorParameters)
            addProperties(result.constructorParameters.map { it.toKotlinConstructorProperty() })
            addSuperInterface(superClassTypeName)
        }
    }

    private fun generateMixin(
        mixin: IrMixin,
        patchClassName: IrClassName,
        patchImpl: IrPatchImpl?,
        extensionPackAccumulator: GenExtensionPackAccumulator,
    ) {
        generateJavaFile(mixin, aggregating = false) {
            addAnnotation<Mixin> {
                setArgumentValue(Mixin::targets, listOf(mixin.targetInternalName))
            }
            setModifiers(IrModifier.ABSTRACT)
            val patchImplEntity = patchImpl?.let { generatePatchInitializer(this, it, mixin) }
            mixin.externalBridge?.let { bridge ->
                addSuperInterface(bridge.className)
                addMethods(bridge.entries.flatMap { it.kinds }.map { kind ->
                    buildJavaMethod(kind.name) {
                        addAnnotation<Override>()
                        setParameters(kind.parameters)
                        setReturnType(kind.returnTypeName)
                        setBody {
                            val patchImplFormat = patchImplEntity?.callFormat
                                ?: lapisError("Patch impl entity cannot be null")
                            code_("$patchImplFormat.%L(${kind.parameters.format})", isReturn = kind.isReturn) {
                                patchImplEntity(); +kind.sourceJvmName; kind.parameters.forEach { +it }
                            }
                        }
                    }
                })
            }
            val staticBridgeSync = mutableListOf<Pair<IrMixinInternalBridgeShadowEntry, GenJavaEntity>>()
            mixin.internalBridge?.let { bridge ->
                addSuperInterface(bridge.className)
                addMethods(bridge.entries.flatMap { entry ->
                    val shadowMemberReference = when (entry) {
                        is IrMixinInternalBridgeShadowPropertyEntry -> {
                            buildJavaField(entry.mappingName, entry.typeName, visibility = null) {
                                if (entry.setter != null) {
                                    addAnnotation<Mutable>()
                                }
                                if (entry.isFinal) {
                                    addAnnotation<Final>()
                                }
                                addAnnotation<Shadow>()
                                val fixedModifiers = mutableListOf<JPModifier>()
                                entry.modifiers.forEach { modifier ->
                                    if (modifier != JPModifier.FINAL) {
                                        fixedModifiers += modifier
                                    }
                                }
                                addModifiers(*fixedModifiers.toTypedArray())
                            }.also(::addField).let(::GenJavaFieldEntity)
                        }

                        is IrMixinInternalBridgeShadowFunctionEntry -> {
                            buildJavaMethod(
                                name = entry.mappingName,
                                visibility = if (entry.isStatic) IrVisibilityModifier.PUBLIC else null,
                            ) {
                                addAnnotation<Shadow>()
                                if (entry.isStatic) {
                                    addModifiers(*entry.modifiers.toTypedArray())
                                } else {
                                    addModifiers(JPModifier.ABSTRACT)
                                    val fixedModifiers = mutableListOf<JPModifier>()
                                    entry.modifiers.forEach { modifier ->
                                        if (modifier == JPModifier.PRIVATE) {
                                            fixedModifiers += JPModifier.PROTECTED
                                        }
                                        if (modifier !in JavaModifiers.abstractIllegals) {
                                            fixedModifiers += modifier
                                        }
                                    }
                                    addModifiers(*fixedModifiers.toTypedArray())
                                }
                                setParameters(entry.parameters)
                                setReturnType(entry.returnTypeName)
                                if (entry.isStatic) setStubBody()
                            }.also(::addMethod).let { GenJavaMethodEntity(it, entry.parameters) }
                        }
                    }
                    if (entry.isStatic) {
                        staticBridgeSync += entry to shadowMemberReference
                    }
                    entry.kinds.map { kind ->
                        buildJavaMethod(kind.name) {
                            addAnnotation<Override>()
                            setParameters(kind.parameters)
                            setReturnType(kind.returnTypeName)
                            setBody {
                                when (kind) {
                                    is IrMixinBridgePropertyEntry.IrMixinBridgeEntryPropertyGetter -> {
                                        return_(shadowMemberReference.callFormat) { shadowMemberReference() }
                                    }

                                    is IrMixinBridgePropertyEntry.IrMixinBridgeEntryPropertySetter -> {
                                        code_("${shadowMemberReference.callFormat} = %N") {
                                            shadowMemberReference(); +kind.parameter
                                        }
                                    }

                                    is IrMixinBridgeFunctionEntry -> {
                                        code_(shadowMemberReference.callFormat, isReturn = kind.isReturn) {
                                            shadowMemberReference()
                                        }
                                    }
                                }
                            }
                        }
                    }
                })
            }
            val hasStaticInjections = mixin.injections.any { it.isStatic }
            val syncStaticBridgeMethod = if (
                mixin.internalBridge != null && staticBridgeSync.isNotEmpty() && hasStaticInjections
            ) {
                val staticBridge = IrMixinStaticBridge(
                    originatingFiles = mixin.internalBridge.originatingFiles,
                    className = patchClassName.derived("StaticBridge"),
                    entries = staticBridgeSync.map { it.first },
                )
                generateStaticBridge(staticBridge, patchClassName.inner("Companion"), extensionPackAccumulator)
                buildJavaMethod("syncStaticBridge".withInternalPrefix(), visibility = IrVisibilityModifier.PRIVATE) {
                    addAnnotation<Unique>()
                    setModifiers(IrModifier.STATIC)
                    setBody {
                        staticBridgeSync.forEach { (entry, shadowMember) ->
                            when (entry) {
                                is IrMixinInternalBridgeShadowPropertyEntry -> {
                                    code_("%T.%L = %L") {
                                        val lambdaCodeBlock = buildJavaCodeBlock {
                                            lambda_(expression = shadowMember.toCodeBlock(asCall = true))
                                        }
                                        +staticBridge.className; +entry.getter.sourceJvmName; +lambdaCodeBlock
                                    }
                                    entry.setter?.let { setter ->
                                        code_("%T.%L = %L") {
                                            val lambdaCodeBlock = buildJavaCodeBlock {
                                                lambda_(parameters = setter.parameters, inline = true) {
                                                    code_("${shadowMember.callFormat} = %N") {
                                                        shadowMember(); +setter.parameter
                                                    }
                                                    return_("%T.INSTANCE") { +KPUnit.asIrClassName() }
                                                }
                                            }
                                            +staticBridge.className; +setter.sourceJvmName; +lambdaCodeBlock
                                        }
                                    }
                                }

                                is IrMixinInternalBridgeShadowFunctionEntry -> {
                                    code_("%T.%L = %L") {
                                        val value = buildJavaCodeBlock {
                                            if (entry.hasBigArity || entry.returnTypeName != null) {
                                                add("%T::${shadowMember.referenceFormat}") {
                                                    +mixin.className; +shadowMember
                                                }
                                            } else {
                                                lambda_(parameters = entry.parameters, inline = true) {
                                                    code_(shadowMember.callFormat) { shadowMember() }
                                                    return_("%T.INSTANCE") { +KPUnit.asIrClassName() }
                                                }
                                            }
                                        }
                                        +staticBridge.className; +entry.sourceJvmName; +value
                                    }
                                }
                            }
                        }
                    }
                }.also(::addMethod)
            } else null
            addMethods(mixin.injections.map {
                buildMixinInjectionMethod(it, patchClassName, patchImplEntity, syncStaticBridgeMethod)
            })
        }
    }

    private fun generatePatchImpl(impl: IrPatchImpl, patch: IrPatch) {
        generateKotlinFile(impl, aggregating = false) {
            val instanceParameter = IrParameter("instance", patch.mixin.targetInstanceTypeName)
            val (internalBridgeParameter, internalBridgeEntries) = patch.mixin.internalBridge.let { bridge ->
                val shadowEntries = bridge?.entries.orEmpty()
                if (bridge != null && shadowEntries.isNotEmpty()) {
                    IrParameter("internal", bridge.className) to shadowEntries
                } else {
                    null to emptyList()
                }
            }
            val constructorParameters = impl.constructorParameters.map { parameter ->
                when (parameter) {
                    is IrPatchImplConstructorInstanceParameter -> instanceParameter

                    is IrPatchImplConstructorInternalBridgeParameter -> {
                        internalBridgeParameter ?: lapisError("Internal bridge parameter cannot be null")
                    }
                }
            }
            if (constructorParameters.isNotEmpty()) {
                setConstructor(constructorParameters)
                internalBridgeParameter?.let {
                    addProperty(it.toKotlinConstructorProperty(IrVisibilityModifier.PRIVATE))
                }
            }
            setSuperClass(
                patch.className,
                constructorArguments = patch.constructorArguments.map { argument ->
                    when (argument) {
                        is IrPatchConstructorOriginArgument -> instanceParameter.toKotlinCodeBlock()
                    }
                }
            )
            internalBridgeParameter?.let {
                internalBridgeEntries.forEach { entry ->
                    when (entry) {
                        is IrMixinBridgePropertyEntry -> {
                            addProperty(buildKotlinProperty(entry.sourceName, entry.typeName) {
                                setModifiers(IrModifier.OVERRIDE)
                                setGetter {
                                    setBody {
                                        return_("%N.%L()") { +internalBridgeParameter; +entry.getter.name }
                                    }
                                }
                                entry.setter?.let { setter ->
                                    setSetter {
                                        setParameters(setter.parameters)
                                        setBody {
                                            code_("%N.%L(%N)") {
                                                +internalBridgeParameter; +setter.name; +setter.parameter
                                            }
                                        }
                                    }
                                }
                            })
                        }

                        is IrMixinBridgeFunctionEntry -> {
                            addFunction(buildKotlinFunction(entry.sourceName) {
                                setModifiers(IrModifier.OVERRIDE)
                                setParameters(entry.parameters)
                                setReturnType(entry.returnTypeName)
                                setBody {
                                    code_("%N.%L(${entry.parameters.format})", isReturn = entry.isReturn) {
                                        +internalBridgeParameter; +entry.name; entry.parameters.forEach { +it }
                                    }
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    private fun generatePatchInitializer(
        destination: JPClassBuilder,
        impl: IrPatchImpl,
        mixin: IrMixin
    ): GenJavaEntity {
        val constructorArgumentCodeBlocks = impl.constructorParameters.map { parameter ->
            when (parameter) {
                is IrPatchImplConstructorInstanceParameter -> {
                    if (mixin.targetInstanceTypeName != KPAny.asIrClassName()) {
                        if (!mixin.isInterfaceTarget) {
                            buildJavaCodeBlock("(%T) (%T) this") { +mixin.targetInstanceTypeName; +Object::class }
                        } else {
                            buildJavaCodeBlock("(%T) this") { +mixin.targetInstanceTypeName }
                        }
                    } else {
                        buildJavaCodeBlock("this")
                    }
                }

                is IrPatchImplConstructorInternalBridgeParameter -> buildJavaCodeBlock("this")
            }
        }
        val initializerCodeBlock = buildJavaCodeBlock("new %T(${constructorArgumentCodeBlocks.format})") {
            +impl.className; constructorArgumentCodeBlocks.forEach { +it }
        }
        val isEagerStrategy = impl.initStrategy == InitStrategy.Eager
        val isSynchronizedStrategy = impl.initStrategy == InitStrategy.Synchronized
        val isThreadSafeStrategy = impl.initStrategy == InitStrategy.Volatile || isSynchronizedStrategy
        val patchField = buildJavaField(
            name = "patch".withInternalPrefix(),
            typeName = impl.className,
            visibility = IrVisibilityModifier.PRIVATE,
        ) {
            addAnnotation<Unique>()
            setModifiers(
                listOfNotNull(
                    if (isEagerStrategy) IrModifier.FINAL else null,
                    if (isThreadSafeStrategy) IrModifier.VOLATILE else null,
                )
            )
            if (isEagerStrategy) {
                initializer(initializerCodeBlock)
            }
        }.also(destination::addField)
        if (isEagerStrategy) {
            return GenJavaFieldEntity(patchField)
        }
        val synchronizedLockField = if (isSynchronizedStrategy) {
            buildJavaField(
                name = "patchLock".withInternalPrefix(),
                typeName = Object::class.asIrTypeName(),
                visibility = IrVisibilityModifier.PRIVATE,
            ) {
                addAnnotation<Unique>()
                setModifiers(IrModifier.FINAL)
                initializer(buildJavaCodeBlock("new %T()") { +Object::class })
            }.also(destination::addField)
        } else null
        val getOrInitPatchMethod = buildJavaMethod(
            name = "getOrInitPatch".withInternalPrefix(),
            visibility = IrVisibilityModifier.PRIVATE
        ) {
            addAnnotation<Unique>()
            setReturnType(impl.className)
            setBody {
                fun GenJavaMethodBody.ifFieldNull_(body: Builder<IrJavaCodeBlock>) {
                    if_(buildJavaCodeBlock("%N == null") { +patchField }, body)
                }

                fun GenJavaMethodBody.initField_(value: JPCodeBlock) {
                    code_("%N = %L") { +patchField; +value }
                }
                if (isThreadSafeStrategy) {
                    val localName = "local"
                    code_("%T %L = %N") { +impl.className; +localName; +patchField }

                    fun GenJavaMethodBody.ifLocalNull_(body: Builder<IrJavaCodeBlock>) {
                        if_(buildJavaCodeBlock("%L == null") { +localName }, body)
                    }

                    fun GenJavaMethodBody.initLocal_(value: JPCodeBlock) {
                        code_(buildJavaCodeBlock("%L = %L") { +localName; +value })
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
        return GenJavaMethodEntity(getOrInitPatchMethod)
    }

    private fun buildMixinInjectionMethod(
        injection: IrInjection,
        patchClassName: IrClassName,
        patchImplMember: GenJavaEntity?,
        syncStaticBridgeMethod: JPMethod?,
    ): JPMethod =
        buildJavaMethod(
            name = injection.jvmName + injection.ordinal?.let { "_ordinal${it}" }.orEmpty(),
            visibility = IrVisibilityModifier.PRIVATE
        ) {
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
            if (injection.isStatic) {
                setModifiers(IrModifier.STATIC)
            }
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
                        val constructorArgumentCodeBlocks = buildList {
                            if (injection.hasReceiver) {
                                add(receiverParameterName.toJavaCodeBlock())
                            }
                            if (injection is IrFieldSetInjection) {
                                add("value".withInternalPrefix(ARGUMENT).toJavaCodeBlock())
                            }
                            if (injection is IrArrayInjection) {
                                add("array".withInternalPrefix(ARGUMENT).toJavaCodeBlock())
                                add("index".withInternalPrefix(ARGUMENT).toJavaCodeBlock())
                                if (injection.op == Op.Set) {
                                    add("value".withInternalPrefix(ARGUMENT).toJavaCodeBlock())
                                }
                            }
                            val impl = argument.wrapperImpl
                            if (impl is IrInvokableDescriptorWrapperImpl) {
                                addAll(impl.parameters.mapIndexed { index, parameter ->
                                    (parameter.name ?: index.toString()).withInternalPrefix(ARGUMENT).toJavaCodeBlock()
                                })
                            }
                            if (injection !is IrArrayInjection) {
                                add(originalParameterName.toJavaCodeBlock())
                            }
                        }
                        buildJavaCodeBlock("new %T(${constructorArgumentCodeBlocks.format})") {
                            +argument.wrapperImpl.className; constructorArgumentCodeBlocks.forEach { +it }
                        }
                    }

                    is IrHookCancelDescriptorWrapperImplArgument -> {
                        buildJavaCodeBlock("new %T(%L)") {
                            +argument.wrapperImpl.className; +callbackParameterName
                        }
                    }

                    is IrHookOriginInstanceofWrapperImplArgument -> {
                        buildJavaCodeBlock("new %T(%L, %L)") {
                            +builtins[SimpleBuiltin.Instanceof]; +valueParameterName; +originalParameterName
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
                        argument.varBuiltin?.let {
                            val builtinTypeFormat = if (it == LocalVarImplBuiltin.ObjectLocalVar) "%T<>" else "%T"
                            buildJavaCodeBlock("new $builtinTypeFormat(%L)") { +builtins[it]; +localName }
                        } ?: localName.toJavaCodeBlock()
                    }
                }
            }
            setBody {
                if (injection.isStatic) {
                    syncStaticBridgeMethod?.let {
                        code_("%N()") { +it }
                    }
                }
                val invokeHook: Builder<IrJavaCodeBlock> = {
                    val patchInstanceFormat = if (injection.isStatic) {
                        "%T.Companion"
                    } else {
                        patchImplMember?.callFormat ?: lapisError("Patch impl cannot be null")
                    }
                    code_("$patchInstanceFormat.%L(${hookArgumentCodeBlocks.format})", isReturn = injection.isReturn) {
                        if (injection.isStatic) {
                            +patchClassName
                        } else {
                            (patchImplMember ?: lapisError("Patch impl cannot be null"))()
                        }
                        +injection.jvmName; hookArgumentCodeBlocks.forEach { +it }
                    }
                }
                if (hasCancelArgument) {
                    try_(
                        block = invokeHook,
                        catchingClassName = builtins[SimpleBuiltin.CancelSignal],
                        catch_ = injection.returnTypeName?.let {
                            { return_(it.getJavaPrimitiveType(allowVoid = false)?.primitiveDefaultValue ?: "null") }
                        },
                    )
                } else {
                    buildJavaCodeBlock(invokeHook)
                }
            }
        }

    private fun generateMixinExternalBridge(
        bridge: IrMixinExternalBridge,
        patch: IrPatch,
        extensionPackAccumulator: GenExtensionPackAccumulator,
    ) {
        generateMixinBridge(bridge)
        val extensionPackEntities = mutableListOf<GenKotlinEntity>()
        bridge.entries.forEach { entry ->
            when (entry) {
                is IrMixinExternalBridgePropertyEntry -> {
                    extensionPackEntities += buildKotlinProperty(entry.sourceName, entry.typeName) {
                        setReceiverType(patch.mixin.targetInstanceTypeName)
                        setGetter {
                            setModifiers(IrModifier.INLINE)
                            setBody {
                                return_("(this as %T).%L()") { +bridge.className; +entry.getter.name }
                            }
                        }
                        entry.setter?.let { setter ->
                            setSetter {
                                setModifiers(IrModifier.INLINE)
                                setParameters(setter.parameters)
                                setBody {
                                    code_("(this as %T).%L(%N)") { +bridge.className; +setter.name; +setter.parameter }
                                }
                            }
                        }
                    }.let(::GenKotlinPropertyEntity)
                }

                is IrMixinExternalBridgeFunctionEntry -> {
                    extensionPackEntities += buildKotlinFunction(entry.sourceName) {
                        setModifiers(IrModifier.INLINE)
                        setReceiverType(patch.mixin.targetInstanceTypeName)
                        setParameters(entry.parameters)
                        setReturnType(entry.returnTypeName)
                        setBody {
                            code_("(this as %T).%L(${entry.parameters.format})", isReturn = entry.isReturn) {
                                +bridge.className; +entry.name; entry.parameters.forEach { +it }
                            }
                        }
                    }.let(::GenKotlinFunctionEntity)
                }
            }
        }
        extensionPackAccumulator.accumulate(extensionPackEntities, bridge.originatingFiles)
    }

    private fun generateMixinInternalBridge(bridge: IrMixinInternalBridge) {
        generateMixinBridge(bridge)
    }

    private fun generateMixinBridge(bridge: IrMixinBridge) {
        generateKotlinFile(bridge, aggregating = false) {
            addFunctions(bridge.entries.flatMap { it.kinds }.map { kind ->
                buildKotlinFunction(kind.name) {
                    setModifiers(IrModifier.ABSTRACT)
                    setParameters(kind.parameters)
                    setReturnType(kind.returnTypeName)
                }
            })
        }
    }

    class IrMixinStaticBridge(
        override val originatingFiles: List<KSFile>,
        override val className: IrClassName,
        val entries: List<IrMixinInternalBridgeShadowEntry>,
    ) : IrKotlinClassBlueprint(IrKotlinClassKind.OBJECT)

    private fun generateStaticBridge(
        bridge: IrMixinStaticBridge,
        patchCompanionClassName: IrClassName,
        extensionPackAccumulator: GenExtensionPackAccumulator,
    ) {
        val extensionPackEntities = mutableListOf<GenKotlinEntity>()
        generateKotlinFile(bridge, aggregating = false, suppressNames = listOf("NOTHING_TO_INLINE")) {
            addProperties(bridge.entries.flatMap { it.kinds }.map { kind ->
                val typeName = when (kind) {
                    is IrMixinBridgePropertyEntry.IrMixinBridgeEntryPropertyGetter -> {
                        IrLambdaTypeName.of(returnTypeName = kind.typeName)
                    }

                    is IrMixinBridgePropertyEntry.IrMixinBridgeEntryPropertySetter -> {
                        IrLambdaTypeName.of(parameters = listOf(IrSetterParameter(kind.typeName)))
                    }

                    is IrMixinBridgeFunctionEntry -> {
                        if (kind.hasBigArity) {
                            val funInterfaceClassName = bridge.className.inner("Proxy_" + kind.sourceJvmName)
                            addType(buildKotlinInterface(funInterfaceClassName.simpleName) {
                                addModifiers(KModifier.PUBLIC, KModifier.FUN)
                                addFunction(buildKotlinFunction("invoke") {
                                    setModifiers(IrModifier.ABSTRACT, IrModifier.OPERATOR)
                                    setParameters(kind.parameters)
                                    setReturnType(kind.returnTypeName)
                                })
                            })
                            funInterfaceClassName
                        } else {
                            IrLambdaTypeName.of(
                                parameters = kind.parameters,
                                returnTypeName = kind.returnTypeName
                            )
                        }
                    }
                }
                buildKotlinProperty(kind.sourceJvmName, typeName) {
                    addModifiers(KModifier.LATEINIT)
                    mutable(true)
                }
            })
            bridge.entries.forEach { entry ->
                when (entry) {
                    is IrMixinInternalBridgeShadowPropertyEntry -> {
                        extensionPackEntities += buildKotlinProperty(entry.sourceName, entry.typeName) {
                            setReceiverType(patchCompanionClassName)
                            setGetter {
                                setModifiers(IrModifier.INLINE)
                                setBody {
                                    return_("%T.%L()") { +bridge.className; +entry.getter.sourceJvmName }
                                }
                            }
                            entry.setter?.let { setter ->
                                setSetter {
                                    setModifiers(IrModifier.INLINE)
                                    setParameters(setter.parameters)
                                    setBody {
                                        code_("%T.%L(%N)") {
                                            +bridge.className; +setter.sourceJvmName; +setter.parameter
                                        }
                                    }
                                }
                            }
                        }.let(::GenKotlinPropertyEntity)
                    }

                    is IrMixinInternalBridgeShadowFunctionEntry -> {
                        extensionPackEntities += buildKotlinFunction(entry.sourceName) {
                            setModifiers(IrModifier.INLINE)
                            setReceiverType(patchCompanionClassName)
                            setParameters(entry.parameters)
                            setReturnType(entry.returnTypeName)
                            setBody {
                                code_("%T.%L(${entry.parameters.format})", isReturn = entry.isReturn) {
                                    +bridge.className; +entry.sourceJvmName; entry.parameters.forEach { +it }
                                }
                            }
                        }.let(::GenKotlinFunctionEntity)
                    }
                }
            }
        }
        extensionPackAccumulator.accumulate(extensionPackEntities, bridge.originatingFiles)
    }

    private fun generateMixinAccessor(
        accessor: IrMixinAccessor,
        extensionPackAccumulator: GenExtensionPackAccumulator,
    ) {
        val extensionPackEntities = mutableListOf<GenKotlinEntity>()
        generateJavaFile(accessor, aggregating = false) {
            addAnnotation<Mixin> {
                setArgumentValue(Mixin::targets, listOf(accessor.targetInternalName))
            }
            accessor.members.forEach { member ->
                val isDelegated = !accessor.isAccessibleSchema && !member.isStatic
                val delegateParameter = if (isDelegated) IrParameter("delegate", accessor.instanceTypeName) else null
                val jvmNamespace = if (isDelegated) member.descriptorClassName else null
                val isDescriptorExtension = member.isStatic || isDelegated
                val extensionReceiverTypeName = if (isDescriptorExtension) {
                    member.descriptorClassName
                } else {
                    accessor.instanceTypeName
                }
                val extensionName = if (isDescriptorExtension) "invoke" else member.name
                val interfaceCodeBlock = if (delegateParameter != null) {
                    buildKotlinCodeBlock("(%N as %T)") { +delegateParameter; +accessor.className }
                } else {
                    buildKotlinCodeBlock(if (member.isStatic) "%T" else "(this as %T)") { +accessor.className }
                }
                when (member) {
                    is IrMixinAccessorFieldMember -> {
                        member.ops.forEach { op ->
                            val name = (op.name.lowercase() + "_" + member.name).withInternalPrefix(ACCESS)
                            val parameters = when (op) {
                                Op.Get -> emptyList()
                                Op.Set -> listOf(IrSetterParameter(member.typeName))
                            }
                            val callable = buildJavaMethod(name) {
                                setModifiers(if (member.isStatic) IrModifier.STATIC else IrModifier.ABSTRACT)
                                if (op == Op.Set && member.removeFinal) {
                                    addAnnotation<Mutable>()
                                }
                                addAnnotation<Accessor> {
                                    setArgumentValue(Accessor::value, member.mappingName)
                                }
                                setParameters(parameters)
                                if (op == Op.Get) {
                                    setReturnType(member.typeName)
                                }
                                if (member.isStatic) setStubBody()
                            }.also(::addMethod).let { GenJavaMethodEntity(it, parameters) }

                            extensionPackEntities += buildKotlinFunction(extensionName) {
                                delegateParameter?.let { setContextParameters(listOf(it)) }
                                setReceiverType(extensionReceiverTypeName)
                                setModifiers(
                                    listOfNotNull(
                                        IrModifier.INLINE,
                                        if (isDescriptorExtension) IrModifier.OPERATOR else null,
                                    )
                                )
                                setParameters(parameters)
                                if (op == Op.Get) {
                                    setReturnType(member.typeName)
                                }
                                setBody {
                                    code_("%L.${callable.callFormat}", isReturn = op == Op.Get) {
                                        +interfaceCodeBlock; callable()
                                    }
                                }
                            }.let(::GenKotlinFunctionEntity)
                        }
                    }

                    is IrMixinAccessorMethodMember -> {
                        val invokerMethod = buildJavaMethod(member.name.withInternalPrefix(ACCESS)) {
                            setModifiers(if (member.isStatic) IrModifier.STATIC else IrModifier.ABSTRACT)
                            addAnnotation<Invoker> {
                                setArgumentValue(Invoker::value, member.mappingName)
                            }
                            setParameters(member.parameters)
                            setReturnType(member.returnTypeName)
                            if (member.isStatic) setStubBody()
                        }.also(::addMethod)
                        extensionPackEntities += buildKotlinFunction(
                            name = if (isDescriptorExtension) "invoke" else member.name,
                            jvmNamespace = jvmNamespace
                        ) {
                            setModifiers(
                                listOfNotNull(
                                    IrModifier.INLINE,
                                    if (isDescriptorExtension) IrModifier.OPERATOR else null,
                                )
                            )
                            delegateParameter?.let { setContextParameters(listOf(it)) }
                            setReceiverType(extensionReceiverTypeName)
                            setParameters(member.parameters)
                            setReturnType(member.returnTypeName)
                            setBody {
                                code_("%L.%N(${member.parameters.format})", isReturn = member.isReturn) {
                                    +interfaceCodeBlock; +invokerMethod; member.parameters.forEach { +it }
                                }
                            }
                        }.let(::GenKotlinFunctionEntity)
                    }
                }
            }
        }
        extensionPackAccumulator.accumulate(extensionPackEntities, accessor.originatingFiles)
    }

    private fun generateExtensionPack(sourceClassName: IrClassName, accumulator: GenExtensionPackAccumulator) {
        val extensionPack = GenExtensionPack(
            originatingFiles = accumulator.originatingFiles,
            packageName = sourceClassName.packageName,
            fileName = sourceClassName.simpleName + "_Extensions",
        )
        generateKotlinFile(extensionPack, aggregating = false, suppressNames = listOf("NOTHING_TO_INLINE")) {
            accumulator.entities.forEach { entity ->
                when (entity) {
                    is GenKotlinPropertyEntity -> addProperty(entity.property)
                    is GenKotlinFunctionEntity -> addFunction(entity.function)
                }
            }
        }
    }

    private fun generateMixinConfig(mixinBlueprints: List<IrMixinRelatedBlueprint>) {
        val mixinConfig = GenMixinConfig(mixinBlueprints.flatMap { it.originatingFiles }, options.mixinConfig)
        generateResourceFile(mixinConfig, aggregating = true) {
            val qualifiedNames = mixinBlueprints.map { it.side to it.className }.groupBy({ it.first }) { it.second }
            configJson.encodeToString(MixinConfig.of(options.generatedMixinPackageName, qualifiedNames))
        }
    }

    private fun generateTweakAccessorConfigs(tweakAccessors: List<IrTweakAccessor>) {
        val originatingFiles = tweakAccessors.flatMap { it.originatingFiles }
        options.accessWidenerConfig?.let { configPath ->
            val tweakAccessorConfig = GenTweakAccessorConfig(originatingFiles, configPath)
            generateResourceFile(tweakAccessorConfig, aggregating = true) {
                val header = if (options.isUnobfuscated) "classTweaker v1 official" else "accessWidener v2 named"
                buildTweakAccessorConfig(tweakAccessors, header, buildTweak = IrTweakAccessorEntry::buildWidenerTweak)
            }
        }
        options.accessTransformerConfig?.let { configPath ->
            val tweakAccessorConfig = GenTweakAccessorConfig(originatingFiles, configPath)
            generateResourceFile(tweakAccessorConfig, aggregating = true) {
                buildTweakAccessorConfig(tweakAccessors, buildTweak = IrTweakAccessorEntry::buildTransformerTweak)
            }
        }
    }

    private fun buildTweakAccessorConfig(
        tweakAccessors: List<IrTweakAccessor>,
        header: String? = null,
        buildTweak: (IrTweakAccessorEntry, JvmClassName) -> String
    ): String = buildString {
        header?.let {
            appendLine(it)
            appendLine()
        }
        tweakAccessors.forEach { tweakAccessor ->
            appendLine("# ${tweakAccessor.ownerJvmClassName.nestedName}")
            tweakAccessor.entries.forEach { entry ->
                appendLine(buildTweak(entry, tweakAccessor.ownerJvmClassName))
            }
            appendLine()
        }
    }

    private fun generateKotlinFile(
        blueprint: IrKotlinFileBlueprint,
        aggregating: Boolean,
        suppressNames: List<String> = emptyList(),
        builder: Builder<KPFileBuilder> = {}
    ) {
        val file = buildKotlinFile(blueprint.packageName, blueprint.fileName) {
            if (suppressNames.isNotEmpty()) {
                addAnnotation<Suppress> {
                    setArgumentValue(Suppress::names, *suppressNames.toTypedArray())
                }
            }
            builder()
        }
        file.writeTo(codeGenerator, aggregating, blueprint.originatingFiles)
    }

    private fun generateKotlinFile(
        blueprint: IrKotlinClassBlueprint,
        aggregating: Boolean,
        suppressNames: List<String> = emptyList(),
        builder: Builder<KPClassBuilder> = {}
    ) {
        val name = blueprint.className.simpleName
        val file = buildKotlinFile(blueprint.className) {
            if (suppressNames.isNotEmpty()) {
                addAnnotation<Suppress> {
                    setArgumentValue(Suppress::names, *suppressNames.toTypedArray())
                }
            }
            addType(
                when (blueprint.classKind) {
                    IrKotlinClassKind.CLASS -> buildKotlinClass(name, builder = builder)
                    IrKotlinClassKind.INTERFACE -> buildKotlinInterface(name, builder = builder)
                    IrKotlinClassKind.OBJECT -> buildKotlinObject(name, builder = builder)
                }
            )
        }
        file.writeTo(codeGenerator, aggregating, blueprint.originatingFiles)
    }

    private fun generateJavaFile(
        blueprint: IrJavaBlueprint,
        aggregating: Boolean,
        builder: Builder<JPClassBuilder> = {}
    ) {
        val name = blueprint.className.simpleName
        val file = buildJavaFile(blueprint.className) {
            when (blueprint.classKind) {
                IrJavaClassKind.CLASS -> buildJavaClass(name, builder = builder)
                IrJavaClassKind.INTERFACE -> buildJavaInterface(name, builder = builder)
            }
        }
        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating, *blueprint.originatingFiles.toTypedArray()),
            packageName = blueprint.className.packageName,
            fileName = name,
            extensionName = "java",
        ).writer().use { file.writeTo(it) }
    }

    private fun generateResourceFile(blueprint: IrResourceBlueprint, aggregating: Boolean, buildText: () -> String) {
        val text = buildText().trimEnd() + "\n"
        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating, *blueprint.originatingFiles.toTypedArray()),
            packageName = "",
            fileName = blueprint.path,
            extensionName = "",
        ).writer().use { it.write(text) }
    }
}

private val configJson: Json = Json { prettyPrint = true }
