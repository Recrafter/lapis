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
import io.github.recrafter.lapis.layers.generator.accessor.AccessorConfigEntry
import io.github.recrafter.lapis.layers.generator.accessor.ClassEntry
import io.github.recrafter.lapis.layers.generator.accessor.FieldEntry
import io.github.recrafter.lapis.layers.generator.accessor.MethodEntry
import io.github.recrafter.lapis.layers.generator.builders.IrJavaCodeBlockBuilder
import io.github.recrafter.lapis.layers.generator.builtins.Builtin
import io.github.recrafter.lapis.layers.generator.builtins.Builtins
import io.github.recrafter.lapis.layers.generator.builtins.DescBuiltin
import io.github.recrafter.lapis.layers.lowering.*
import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import io.github.recrafter.lapis.layers.lowering.types.orVoid
import kotlinx.serialization.json.Json
import org.objectweb.asm.Opcodes
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

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

    private fun generateDescriptors(descriptors: List<IrDesc>, dependencies: KSPDependencies) {
        if (descriptors.isEmpty()) {
            return
        }
        buildKotlinFile(options.generatedPackageName, "_Descriptors") {
            suppressWarnings(
                KWarning.RedundantVisibilityModifier,
                KWarning.NothingToInline,
            )
            descriptors.forEach { desc ->
                when (desc) {
                    is IrInvokableDesc -> {
                        desc.callWrapper?.let { builtins.generateDescWrapper(this, DescBuiltin.Call, it) }
                        desc.cancelWrapper?.let { builtins.generateDescWrapper(this, DescBuiltin.Cancel, it) }
                    }

                    is IrFieldDesc -> {
                        desc.fieldGetWrapper?.let { builtins.generateDescWrapper(this, DescBuiltin.FieldGet, it) }
                        desc.fieldWriteWrapper?.let { builtins.generateDescWrapper(this, DescBuiltin.FieldWrite, it) }
                    }
                }
            }
        }.writeTo(codeGenerator, dependencies)
    }

    private fun generateMixin(mixin: IrMixin) {
        if (mixin.isNotEmpty()) {
            val dependencies = listOfNotNull(mixin.containingFile).toDependencies()
            buildKotlinFile(mixin.patchImplClassName) {
                suppressWarnings(KWarning.RedundantVisibilityModifier)
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
                listOf(IrParameter("instance", mixin.targetClassName)),
                IrModifier.OVERRIDE
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
            val getOrInitPatchMethod = buildJavaMethod("getOrInitPatch".withInternalPrefix()) {
                addAnnotation<Unique>()
                setModifiers(IrModifier.PRIVATE)
                setReturnType(mixin.patchClassName)
                setBody {
                    val patchNotInitializedCondition = buildJavaCodeBlock("%N == null") {
                        arg(patchField)
                    }
                    if_(patchNotInitializedCondition) {
                        code("%N = new %T((%T) (%T) this)") {
                            arg(patchField)
                            arg(mixin.patchImplClassName)
                            arg(mixin.targetClassName)
                            arg(Object::class.asIr())
                        }
                    }
                    return_("%N") { arg(patchField) }
                }
            }
            addField(patchField)
            addMethod(getOrInitPatchMethod)
            mixin.extension?.let { extension ->
                addSuperInterface(extension.className)
                addMethods(extension.kinds.map { method ->
                    buildJavaMethod(method.getInternalName(options.modId)) {
                        setModifiers(IrModifier.PUBLIC, IrModifier.OVERRIDE)
                        setParameters(method.parameters)
                        setReturnType(method.returnTypeName)
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
                            if (method.returnTypeName != null) {
                                return_(format, args)
                            } else {
                                code(format, args)
                            }
                        }
                    }
                })
            }
            addMethods(mixin.injections.map {
                buildMixinInjectionMethod(mixin, it, getOrInitPatchMethod)
            })
        }

    private fun buildMixinInjectionMethod(
        mixin: IrMixin,
        injection: IrInjection,
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
                    setStringArrayMember(WrapMethod::method, injection.methodMixinRef)
                }

                is IrWrapOperationInjection -> addAnnotation<WrapOperation> {
                    setStringArrayMember(WrapOperation::method, injection.methodMixinRef)
                    setAnnotationArrayMember<WrapOperation, At>(WrapOperation::at) {
                        setStringMember(At::value, "INVOKE")
                        setStringMember(At::target, injection.targetMixinRef)
                        setIntMember(At::ordinal, injection.ordinal)
                    }
                }

                is IrModifyExpressionValueInjection -> addAnnotation<ModifyExpressionValue> {
                    setStringArrayMember(ModifyExpressionValue::method, injection.methodMixinRef)
                    setAnnotationArrayMember<ModifyExpressionValue, At>(ModifyExpressionValue::at) {
                        setStringMember(At::value, "CONSTANT")
                        setStringArrayMember(
                            At::args,
                            *injection.args.map { "${it.first}=${it.second}" }.toTypedArray()
                        )
                        setIntMember(At::ordinal, injection.ordinal)
                    }
                }

                is IrFieldGetInjection -> addAnnotation<WrapOperation> {
                    setStringArrayMember(WrapOperation::method, injection.methodMixinRef)
                    setAnnotationArrayMember<WrapOperation, At>(WrapOperation::at) {
                        setStringMember(At::value, "FIELD")
                        setStringMember(At::target, injection.targetMixinRef)
                        setIntMember(At::opcode, if (injection.isStatic) Opcodes.GETSTATIC else Opcodes.GETFIELD)
                        setIntMember(At::ordinal, injection.ordinal)
                    }
                }

                is IrFieldWriteInjection -> addAnnotation<WrapOperation> {
                    setStringArrayMember(WrapOperation::method, injection.methodMixinRef)
                    setAnnotationArrayMember<WrapOperation, At>(WrapOperation::at) {
                        setStringMember(At::value, "FIELD")
                        setStringMember(At::target, injection.targetMixinRef)
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
                        buildJavaParameter(receiverParameterName, parameter.typeName)
                    }

                    is IrInjectionArgumentParameter -> {
                        val name = parameter.name ?: parameter.index.toString()
                        buildJavaParameter(name.withInternalPrefix("argument"), parameter.typeName)
                    }

                    is IrInjectionOperationParameter -> {
                        buildJavaParameter(
                            originalParameterName,
                            Operation::class.asIr().generic(parameter.returnTypeName.orVoid())
                        )
                    }

                    is IrInjectionValueParameter -> {
                        buildJavaParameter("value".withInternalPrefix(), parameter.typeName)
                    }

                    is IrInjectionLocalParameter -> {
                        buildJavaParameter(parameter.name.withInternalPrefix("local"), parameter.typeName) {
                            addAnnotation<Local> {
                                setIntMember(Local::ordinal, parameter.ordinal)
                            }
                        }
                    }

                    is IrInjectionParamParameter -> {
                        val name = parameter.name ?: parameter.index.toString()
                        buildJavaParameter(name.withInternalPrefix("parameter"), parameter.typeName) {
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
                                ?.let { CallbackInfoReturnable::class.asIr().generic(it) }
                                ?: CallbackInfo::class.asIr()
                        ) {
                            addAnnotation<Cancellable>()
                        }
                    }
                }
            })
            setReturnType(injection.returnTypeName)
            val hookArgumentCodeBlocks = injection.hookArguments.map { argument ->
                when (argument) {
                    is IrHookOriginValueArgument -> {
                        buildJavaCodeBlock("%L") {
                            arg("value".withInternalPrefix())
                        }
                    }

                    is IrHookOriginDescWrapperArgument -> {
                        val constructorArgumentCodeBlocks = buildList {
                            when (injection) {
                                is IrWrapMethodInjection if !injection.isStatic -> {
                                    add(buildJavaCodeBlock("(%T) (%T) this") {
                                        arg(mixin.targetClassName)
                                        arg(Object::class.asIr())
                                    })
                                    add(buildJavaCodeBlock("%L") { arg(false) })
                                }

                                is IrWrapOperationInjection if !injection.isStatic -> {
                                    add(buildJavaCodeBlock(receiverParameterName))
                                    add(buildJavaCodeBlock("%L") { arg(true) })
                                }

                                is IrFieldGetInjection if !injection.isStatic -> {
                                    add(buildJavaCodeBlock(receiverParameterName))
                                }

                                is IrFieldWriteInjection if !injection.isStatic -> {
                                    add(buildJavaCodeBlock(receiverParameterName))
                                    add(buildJavaCodeBlock("newValue".withInternalPrefix("argument")))
                                }

                                else -> {}
                            }
                            val wrapper = argument.wrapper
                            if (wrapper is IrDescCallWrapper) {
                                addAll(wrapper.parameters.mapIndexed { index, parameter ->
                                    val name = parameter.name ?: index.toString()
                                    buildJavaCodeBlock(name.withInternalPrefix("argument"))
                                })
                            }
                            add(buildJavaCodeBlock(originalParameterName))
                        }
                        buildJavaCodeBlock(buildString {
                            append("new %T(")
                            append(constructorArgumentCodeBlocks.joinToString { "%L" })
                            append(")")
                        }) {
                            arg(argument.wrapper.className)
                            constructorArgumentCodeBlocks.forEach { arg(it) }
                        }
                    }

                    is IrHookCancelArgument -> {
                        val constructorArgumentCodeBlocks = buildList {
                            addAll(argument.wrapper.parameters.mapIndexed { index, parameter ->
                                val name = parameter.name ?: index.toString()
                                buildJavaCodeBlock(name.withInternalPrefix("parameter"))
                            })
                            add(buildJavaCodeBlock(callbackParameterName))
                        }
                        buildJavaCodeBlock(buildString {
                            append("new %T(")
                            append(constructorArgumentCodeBlocks.joinToString { "%L" })
                            append(")")
                        }) {
                            arg(argument.wrapper.className)
                            constructorArgumentCodeBlocks.forEach { arg(it) }
                        }
                    }

                    is IrHookParamArgument -> buildJavaCodeBlock("%L") {
                        arg(argument.name.withInternalPrefix("param"))
                    }

                    is IrHookLocalArgument -> buildJavaCodeBlock("%L") {
                        arg(argument.name.withInternalPrefix("local"))
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
                    if (injection.returnTypeName != null) {
                        return_(format, args)
                    } else {
                        code(format, args)
                    }
                }
                if (hasCallback) {
                    try_(
                        tryBody = invokeHook,
                        exceptionClassName = builtins[Builtin.CancelSignal],
                        catchBody = if (injection.returnTypeName != null) {
                            { return_(injection.returnTypeName.javaPrimitiveType?.defaultValue.toString()) }
                        } else null
                    )
                } else {
                    buildJavaCodeBlock(invokeHook)
                }
            }
        }
    }

    private fun generateMixinExtension(mixin: IrMixin, extension: IrExtension) {
        buildJavaFile(extension.className) {
            buildJavaInterface(extension.className.simpleName) {
                setModifiers(IrModifier.PUBLIC)
                addMethods(extension.kinds.map { method ->
                    buildJavaMethod(method.getInternalName(options.modId)) {
                        setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
                        setParameters(method.parameters)
                        setReturnType(method.returnTypeName)
                    }
                })
            }
        }.writeTo(codeGenerator, listOfNotNull(mixin.containingFile).toDependencies())

        extensionProperties += extension.kinds.filterIsInstance<IrFieldGetterExtension>().map { getter ->
            buildKotlinProperty(getter.name, getter.typeName) {
                setReceiverType(mixin.targetClassName)
                setGetter {
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%L()") {
                            arg(extension.className)
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
                                arg(extension.className)
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
                setReceiverType(mixin.targetClassName)
                setParameters(method.parameters)
                setReturnType(method.returnTypeName)
                setBody {
                    return_("(this as %T).%L(%L)") {
                        arg(extension.className)
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
            path = options.mixinConfigName,
            contents = configJson.encodeToString(
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
            ),
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
            header?.let {
                appendLine(it)
                appendLine()
            }
            var lastOwner: IrClassName? = null
            sortedEntries.forEach { entry ->
                if (lastOwner != entry.ownerClassName) {
                    if (lastOwner != null) {
                        appendLine()
                    }
                    appendLine("# ${entry.ownerClassName.nestedName}")
                    lastOwner = entry.ownerClassName
                }
                appendLine(directive(entry))
            }
        }

        options.accessWidenerConfigName?.let { name ->
            val header = if (options.isUnobfuscated) "classTweaker v1 official" else "accessWidener v2 named"
            codeGenerator.createResourceFile(
                path = name,
                contents = formatConfig(header) { it.awEntry },
                aggregating = true,
            )
        }
        options.accessTransformerConfigName?.let { name ->
            codeGenerator.createResourceFile(
                path = name,
                contents = formatConfig { it.atEntry },
                aggregating = true,
            )
        }
    }
}

fun String.withInternalPrefix(prefix: String = LapisMeta.NAME.lowercase()): String =
    "_${prefix}_$this"

private val configJson: Json = Json { prettyPrint = true }
