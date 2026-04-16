package io.github.recrafter.lapis.layers.generator

import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.llamalad7.mixinextras.injector.ModifyReturnValue
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Cancellable
import com.llamalad7.mixinextras.sugar.Local
import io.github.recrafter.lapis.LapisLogger
import io.github.recrafter.lapis.Options
import io.github.recrafter.lapis.extensions.InternalPrefix.*
import io.github.recrafter.lapis.extensions.capitalize
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ks.toDependencies
import io.github.recrafter.lapis.extensions.ksp.KSPCodeGenerator
import io.github.recrafter.lapis.extensions.ksp.KSPDependencies
import io.github.recrafter.lapis.extensions.ksp.createResourceFile
import io.github.recrafter.lapis.extensions.withInternalPrefix
import io.github.recrafter.lapis.layers.generator.accessor.AccessorConfigEntry
import io.github.recrafter.lapis.layers.generator.accessor.ClassEntry
import io.github.recrafter.lapis.layers.generator.accessor.FieldEntry
import io.github.recrafter.lapis.layers.generator.accessor.MethodEntry
import io.github.recrafter.lapis.layers.generator.builders.Builder
import io.github.recrafter.lapis.layers.generator.builders.IrJavaCodeBlock
import io.github.recrafter.lapis.layers.builtins.ExternalBuiltin
import io.github.recrafter.lapis.layers.builtins.Builtins
import io.github.recrafter.lapis.layers.builtins.DescBuiltin
import io.github.recrafter.lapis.layers.builtins.InternalBuiltin
import io.github.recrafter.lapis.layers.lowering.IrModifier
import io.github.recrafter.lapis.layers.lowering.asIr
import io.github.recrafter.lapis.layers.lowering.models.*
import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import io.github.recrafter.lapis.layers.lowering.types.binaryName
import io.github.recrafter.lapis.layers.lowering.types.orVoid
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
    private val codeGenerator: KSPCodeGenerator,
    private val logger: LapisLogger,
) {
    private val extensionProperties: MutableList<KPProperty> = mutableListOf()
    private val extensionFunctions: MutableList<KPFunction> = mutableListOf()

    fun generate(schemas: List<IrSchema>, mixins: List<IrMixin>) {
        val schemaContainingFiles = schemas.mapNotNull { it.containingFile }
        generateDescriptorWrappers(
            schemas.flatMap { it.descriptors },
            schemaContainingFiles.toDependencies()
        )
        mixins.forEach { generateMixin(it) }

        val allContainingFiles = schemaContainingFiles + mixins.mapNotNull { it.containingFile }
        generateExtensions(allContainingFiles.toDependencies())

        generateMixinConfig(mixins)
        generateAccessorConfig(schemas)
    }

    private fun generateDescriptorWrappers(descriptors: List<IrDesc>, dependencies: KSPDependencies) {
        if (descriptors.isEmpty()) {
            return
        }
        buildKotlinFile(options.generatedPackageName, "_Descriptors") {
            suppressWarnings(
                KSuppressWarning.RedundantVisibilityModifier,
                KSuppressWarning.NothingToInline,
                KSuppressWarning.LocalVariableName,
            )
            descriptors.forEach { desc ->
                when (desc) {
                    is IrInvokableDesc -> {
                        desc.bodyWrapper?.let { builtins.generateDescWrapper(this, DescBuiltin.Body, it) }
                        desc.callWrapper?.let { builtins.generateDescWrapper(this, DescBuiltin.Call, it) }
                        desc.cancelWrapper?.let { builtins.generateDescWrapper(this, DescBuiltin.Cancel, it) }
                    }

                    is IrFieldDesc -> {
                        desc.fieldGetWrapper?.let { builtins.generateDescWrapper(this, DescBuiltin.FieldGet, it) }
                        desc.fieldSetWrapper?.let { builtins.generateDescWrapper(this, DescBuiltin.FieldSet, it) }
                        desc.arrayGetWrapper?.let { builtins.generateDescWrapper(this, DescBuiltin.ArrayGet, it) }
                        desc.arraySetWrapper?.let { builtins.generateDescWrapper(this, DescBuiltin.ArraySet, it) }
                    }
                }
            }
        }.writeTo(codeGenerator, dependencies)
    }

    private fun generateMixin(mixin: IrMixin) {
        if (mixin.isNotEmpty()) {
            val dependencies = listOfNotNull(mixin.containingFile).toDependencies()
            buildKotlinFile(mixin.patchImplClassName) {
                suppressWarnings(KSuppressWarning.RedundantVisibilityModifier)
                addType(buildPatchImplClass(mixin))
            }.writeTo(codeGenerator, dependencies)

            buildJavaFile(mixin.className) {
                buildMixinClass(mixin)
            }.writeTo(codeGenerator, dependencies)
        }
        mixin.extension?.let { generateMixinExtension(mixin, it) }
    }

    private fun buildPatchImplClass(mixin: IrMixin): KPClass =
        buildKotlinClass(mixin.patchImplClassName.simpleName) {
            setModifiers(IrModifier.PUBLIC)
            setSuperClass(mixin.patchClassName)
            setConstructor(
                listOf(
                    IrParameter(
                        "instance",
                        mixin.targetClassName,
                        listOf(IrModifier.PUBLIC, IrModifier.OVERRIDE)
                    )
                ),
            )
        }

    private fun buildMixinClass(mixin: IrMixin): JPClass =
        buildJavaClass(mixin.className.simpleName) {
            addAnnotation<Mixin> {
                setClassArrayMember(Mixin::value, mixin.targetClassName)
            }
            setModifiers(IrModifier.PUBLIC)
            val patchField = buildJavaField("patch".withInternalPrefix(), mixin.patchClassName) {
                addAnnotation<Unique>()
                setModifiers(IrModifier.PRIVATE)
            }
            addField(patchField)
            val getOrInitPatchMethod = buildJavaMethod("getOrInitPatch".withInternalPrefix()) {
                addAnnotation<Unique>()
                setModifiers(IrModifier.PRIVATE)
                setReturnType(mixin.patchClassName)
                setBody {
                    val isNotInitializedCondition = buildJavaCodeBlock("%N == null") {
                        arg(patchField)
                    }
                    if_(isNotInitializedCondition) {
                        code_("%N = new %T((%T) (%T) this)") {
                            arg(patchField)
                            arg(mixin.patchImplClassName)
                            arg(mixin.targetClassName)
                            arg(Object::class.asIr())
                        }
                    }
                    return_("%N") { arg(patchField) }
                }
            }
            addMethod(getOrInitPatchMethod)
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
            addMethods(mixin.injections.map {
                buildMixinInjectionMethod(it, mixin, getOrInitPatchMethod)
            })
        }

    private fun buildMixinInjectionMethod(
        injection: IrInjection,
        mixin: IrMixin,
        getOrInitPatchMethod: JPMethod
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
                        setStringMember(At::value, if (injection.isSet) "STORE" else "LOAD")
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
                        setIntMember(At::opcode, if (injection.isStaticTarget) Opcodes.GETSTATIC else Opcodes.GETFIELD)
                        setStringArrayMember(At::args, "array=${if (injection.isSet) "set" else "get"}")
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
            if (injection.isStatic) {
                setModifiers(IrModifier.PRIVATE, IrModifier.STATIC)
            } else {
                setModifiers(IrModifier.PRIVATE)
            }
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
                            Operation::class.asIr().parameterizedBy(parameter.returnTypeName.orVoid())
                        )
                    }

                    is IrInjectionValueParameter -> buildJavaParameter(valueParameterName, parameter.typeName)
                    is IrInjectionLocalParameter -> {
                        buildJavaParameter(parameter.name.withInternalPrefix(LOCAL), parameter.typeName) {
                            addAnnotation<Local> {
                                when (val local = parameter.local) {
                                    is IrNamedLocal -> setStringArrayMember(Local::name, local.name)
                                    is IrPositionalLocal -> setIntMember(Local::ordinal, local.ordinal)
                                }
                            }
                        }
                    }

                    is IrInjectionParamParameter -> {
                        val name = parameter.name ?: parameter.index.toString()
                        buildJavaParameter(name.withInternalPrefix(PARAM), parameter.typeName) {
                            addAnnotation<Local> {
                                setIntMember(Local::index, parameter.localIndex)
                                setBooleanMember(Local::argsOnly, true)
                            }
                        }
                    }

                    is IrInjectionCallbackParameter -> {
                        buildJavaParameter(
                            callbackParameterName,
                            parameter.returnTypeName
                                ?.let { CallbackInfoReturnable::class.asIr().parameterizedBy(it) }
                                ?: CallbackInfo::class.asIr()
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

                    is IrHookOriginDescWrapperArgument<*> -> {
                        val descWrapperConstructorArgumentCodeBlocks = buildList {
                            val wrapper = argument.wrapper
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
                            if (wrapper is IrInvokableDescWrapper) {
                                addAll(wrapper.parameters.mapIndexed { index, parameter ->
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
                                if (injection.isSet) {
                                    add(buildJavaCodeBlock("value".withInternalPrefix(ARGUMENT)))
                                }
                            } else {
                                add(buildJavaCodeBlock(originalParameterName))
                            }
                        }
                        buildJavaCodeBlock(buildString {
                            append("new %T(")
                            append(descWrapperConstructorArgumentCodeBlocks.joinToString { "%L" })
                            append(")")
                        }) {
                            arg(argument.wrapper.className)
                            descWrapperConstructorArgumentCodeBlocks.forEach { arg(it) }
                        }
                    }

                    is IrHookOriginInstanceofArgument -> {
                        buildJavaCodeBlock("new %T(%L, %L)") {
                            arg(builtins[ExternalBuiltin.Instanceof])
                            arg(valueParameterName)
                            arg(originalParameterName)
                        }
                    }

                    is IrHookCancelArgument -> {
                        buildJavaCodeBlock("new %T(%L)") {
                            arg(argument.wrapper.className)
                            arg(callbackParameterName)
                        }
                    }

                    is IrHookOrdinalArgument -> {
                        buildJavaCodeBlock("%L") {
                            arg(injection.ordinal ?: lapisError("Ordinal not found"))
                        }
                    }

                    is IrHookParamArgument -> buildJavaCodeBlock("%L") {
                        arg(
                            argument.name.withInternalPrefix(
                                if (injection is IrInjectInjection) ARGUMENT
                                else PARAM
                            )
                        )
                    }

                    is IrHookLocalArgument -> buildJavaCodeBlock("%L") {
                        arg(argument.name.withInternalPrefix(LOCAL))
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
                            arg(mixin.patchImplClassName)
                        } else {
                            arg(getOrInitPatchMethod)
                        }
                        arg(injection.name)
                        hookArgumentCodeBlocks.forEach { arg(it) }
                    }
                }
                if (hasCancelArgument) {
                    try_(
                        try_ = invokeHook,
                        catchingClassName = builtins[InternalBuiltin.CancelSignal],
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
        }.writeTo(codeGenerator, listOfNotNull(mixin.containingFile).toDependencies())

        extensionProperties += extension.kinds.filterIsInstance<IrPropertyGetterExtension>().map { getter ->
            buildKotlinProperty(getter.name, getter.typeName) {
                setReceiverType(mixin.targetClassName)
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
                setReceiverType(mixin.targetClassName)
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

    private fun generateExtensions(dependencies: KSPDependencies) {
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
        }.writeTo(codeGenerator, dependencies)
    }

    private fun generateMixinConfig(mixins: List<IrMixin>) {
        val contents = configJson.encodeToString(
            MixinConfig.of(
                mixinPackage = options.mixinPackageName,
                qualifiedNames = mixins.groupBy { it.side }.mapValues { (_, mixins) ->
                    mixins.mapNotNull { mixin ->
                        if (mixin.isNotEmpty()) {
                            mixin.className.qualifiedName
                        } else {
                            null
                        }
                    }
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
                    ownerClassName = schema.targetClassName,
                    removeFinal = schema.removeFinal,
                )
            }
            schema.descriptors.filter { it.makePublic }.forEach { desc ->
                entries += when (desc) {
                    is IrInvokableDesc -> {
                        MethodEntry(
                            ownerClassName = schema.targetClassName,
                            name = desc.binaryName,
                            parameterTypes = desc.parameters.map { it.typeName },
                            returnTypeName = when (desc) {
                                is IrConstructorDesc -> null
                                else -> desc.returnTypeName
                            },
                            removeFinal = desc.removeFinal,
                            isConstructor = desc is IrConstructorDesc,
                        )
                    }

                    is IrFieldDesc -> {
                        FieldEntry(
                            ownerClassName = schema.targetClassName,
                            name = desc.targetName,
                            typeName = desc.typeName,
                            removeFinal = desc.removeFinal,
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
