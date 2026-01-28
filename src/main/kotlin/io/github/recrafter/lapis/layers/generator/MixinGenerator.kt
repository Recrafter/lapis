package io.github.recrafter.lapis.layers.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import io.github.recrafter.lapis.extensions.capitalizeWithPrefix
import io.github.recrafter.lapis.extensions.common.asIr
import io.github.recrafter.lapis.extensions.common.unsafeLazy
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ksp.KspDependencies
import io.github.recrafter.lapis.extensions.ksp.KspLogger
import io.github.recrafter.lapis.extensions.ksp.createResourceFile
import io.github.recrafter.lapis.extensions.ksp.flatten
import io.github.recrafter.lapis.layers.lowering.*
import io.github.recrafter.lapis.options.Options
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Mutable
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.gen.Accessor
import org.spongepowered.asm.mixin.gen.Invoker
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import kotlin.reflect.KClass

class MixinGenerator(
    val options: Options,
    val codeGenerator: CodeGenerator,
    val logger: KspLogger,
) {
    private val configJson: Json by unsafeLazy {
        Json { prettyPrint = true }
    }
    private val extensionProperties: MutableList<KPProperty> = mutableListOf()
    private val extensionFunctions: MutableList<KPFunction> = mutableListOf()

    fun generate(descriptorImpls: List<IrDescriptorImpl>, rootMixins: List<IrMixin>) {
        generateDescriptorImpls(descriptorImpls)
        rootMixins.forEach { generateRootMixin(it) }

        generateExtensions(rootMixins.map { it.dependencies }.flatten())
        generateMixinConfig(rootMixins)
    }

    private fun generateDescriptorImpls(descriptorImpls: List<IrDescriptorImpl>) {
        val operationParameterName = "_operation"
        val receiverParameterName = "_receiver"
        val receiverExtensionName = "getReceiver"
        buildKotlinFile(options.generatedPackageName, "DescriptorImpls") {
            descriptorImpls.forEach { impl ->
                addType(buildKotlinClass(impl.type.simpleName) {
                    setConstructor(
                        buildList {
                            impl.receiverType?.let { add(IrParameter(receiverParameterName, it)) }
                            addAll(impl.parameters.map { parameter ->
                                IrParameter(parameter.name, parameter.type)
                            })
                            add(
                                IrParameter(
                                    operationParameterName,
                                    Operation::class.asIr().parameterizedBy(impl.returnType.orVoid())
                                )
                            )
                        }
                    )
                    setSuperClassType(impl.superType)
                })
                addProperties(impl.parameters.map { parameter ->
                    buildKotlinProperty(parameter.name, parameter.type) {
                        setReceiverType(impl.superType)
                        getter(buildKotlinGetter {
                            addStatement(
                                "return (this as %T).%L",
                                impl.type.kotlin,
                                parameter.name,
                            )
                        })
                    }
                })
                impl.receiverType?.let { returnType ->
                    addFunction(buildKotlinFunction(receiverExtensionName) {
                        setReceiverType(impl.superType)
                        setReturnType(returnType)
                        addStatement(
                            "return (this as %T).%L",
                            impl.type.kotlin,
                            receiverParameterName,
                        )
                    })
                }
                addFunction(buildKotlinFunction("invoke") {
                    setReceiverType(impl.superType)
                    val parameters = mutableListOf<String>()
                    impl.receiverType?.let {
                        parameters += receiverParameterName
                        addParameter(buildKotlinParameter(receiverParameterName, it) {
                            defaultValue("this.%L()", receiverExtensionName)
                        })
                    }
                    impl.parameters.forEach { parameter ->
                        parameters += parameter.name
                        addParameter(buildKotlinParameter(parameter.name, parameter.type) {
                            defaultValue("this.%L", parameter.name)
                        })
                    }
                    setReturnType(impl.returnType)
                    addStatement(
                        buildString {
                            if (impl.returnType != null) {
                                append("return ")
                            }
                            append("(this as %T).%L.call(%L)")
                        },
                        impl.type.kotlin,
                        operationParameterName,
                        parameters.joinToString(),
                    )
                })
            }
        }.writeTo(codeGenerator, descriptorImpls.map { it.dependencies }.flatten())
    }

    private fun generateRootMixin(mixin: IrMixin) {
        buildPatchImplType(mixin)
            .toKotlinFile(mixin.patchImplType.packageName)
            .writeTo(codeGenerator, mixin.dependencies)
        buildMixinClassType(null, mixin)
            .toJavaFile(mixin.type.packageName)
            .writeTo(codeGenerator, mixin.dependencies)
    }

    private fun buildPatchImplType(mixin: IrMixin): KPType =
        buildKotlinClass(mixin.patchImplType.simpleName) {
            setSuperClassType(mixin.patchType)
            primaryConstructor(buildKotlinConstructor {
                addParameter("instance", mixin.targetType.kotlin)
            })
            addProperty(buildKotlinProperty("instance", mixin.targetType) {
                addModifiers(KPModifier.OVERRIDE)
                initializer("instance")
            })
            addProperties(mixin.accessor?.kinds?.filterIsInstance<IrFieldGetterAccessor>()?.map { getter ->
                buildKotlinProperty(getter.name, getter.type) {
                    addModifiers(KPModifier.OVERRIDE)
                    getter(buildKotlinGetter {
                        addStatement(
                            when {
                                getter.isStatic -> "return %T.%L()"
                                else -> "return (instance as %T).%L()"
                            },
                            mixin.accessor.type.kotlin,
                            getter.internalName,
                        )
                    })
                    mixin.accessor.kinds.find { it is IrFieldSetterAccessor && it.name == getter.name }?.let { setter ->
                        mutable(true)
                        setter(buildKotlinSetter {
                            addParameters(setter.parameters)
                            addStatement(
                                when {
                                    setter.isStatic -> "%T.%L(%L)"
                                    else -> "(instance as %T).%L(%L)"
                                },
                                mixin.accessor.type.kotlin,
                                setter.internalName,
                                setter.parameters.joinToString { it.name },
                            )
                        })
                    }
                }
            }.orEmpty())
            addFunctions(mixin.accessor?.kinds?.filterIsInstance<IrMethodAccessor>()?.map { method ->
                buildKotlinFunction(method.name) {
                    addModifiers(KPModifier.OVERRIDE)
                    addParameters(method.parameters)
                    setReturnType(method.returnType)
                    addStatement(
                        buildString {
                            if (method.returnType != null) {
                                append("return ")
                            }
                            append(
                                if (method.isStatic) "%T.%L(%L)"
                                else "(instance as %T).%L(%L)"
                            )
                        },
                        mixin.accessor.type.kotlin,
                        method.internalName,
                        method.parameters.joinToString { it.name },
                    )
                }
            }.orEmpty())
            addTypes(mixin.innerMixins.map { buildPatchImplType(it) })
        }

    private fun buildMixinClassType(parentMixin: IrMixin?, mixin: IrMixin): JPType {
        mixin.accessor?.let { generateMixinAccessor(mixin, it) }
        mixin.extension?.let { generateMixinExtension(mixin, it) }
        return buildJavaClass(mixin.type.simpleName) {
            addModifiers(JPModifier.PUBLIC)
            if (parentMixin != null) {
                addModifiers(JPModifier.STATIC)
            }
            addAnnotation<Mixin> {
                addClassMember("value", mixin.targetType)
            }
            addField(buildJavaField("patch", mixin.patchType) {
                addModifiers(JPModifier.PRIVATE)
                addAnnotation<Unique>()
            })
            addMethod(buildJavaMethod("getOrInitPatch") {
                addModifiers(JPModifier.PRIVATE)
                addAnnotation<Unique>()
                setReturnType(mixin.patchType)
                addIfStatement(buildJavaCodeBlock("patch == null")) {
                    add(
                        "patch = new %T((%T) (%T) this)"
                    ) {
                        arg(mixin.patchImplType)
                        arg(mixin.targetType)
                        arg(Object::class.asIr())
                    }
                }
                addStatement("return patch")
            })
            mixin.extension?.let { extension ->
                addSuperinterface(extension.type.java)
                addMethods(extension.kinds.map { method ->
                    buildJavaMethod(method.internalName) {
                        addModifiers(JPModifier.PUBLIC)
                        addAnnotation<Override>()
                        addParameters(method.parameters)
                        setReturnType(method.returnType)
                        addStatement(
                            buildJavaCodeBlock(
                                buildString {
                                    if (method.returnType != null) {
                                        append("return ")
                                    }
                                    append("getOrInitPatch().%L(%L)")
                                }
                            ) {
                                arg(
                                    when (method) {
                                        is IrFieldGetterExtension -> method.name.capitalizeWithPrefix("get")
                                        is IrFieldSetterExtension -> method.name.capitalizeWithPrefix("set")
                                        else -> method.name
                                    }
                                )
                                arg(method.parameters.joinToString { it.name })
                            }
                        )
                    }
                })
            }
            addMethods(mixin.injections.map { buildMixinInjectionMethod(it) })
            addTypes(mixin.innerMixins.map { buildMixinClassType(mixin, it) })
        }
    }

    private fun buildMixinInjectionMethod(injection: IrInjection): JPMethod =
        buildJavaMethod(injection.name) {
            addModifiers(JPModifier.PRIVATE)
            when (injection) {
                is IrWrapMethodInjection -> {
                    addAnnotation<WrapMethod> {
                        addStringMember("method", injection.method)
                    }
                }

                is IrWrapOperationInjection -> {
                    addAnnotation<WrapOperation> {
                        addStringMember("method", injection.method)
                        addAnnotationMember<At>("at") {
                            addStringMember("value", "INVOKE")
                            addStringMember("target", injection.target)
                            injection.ordinal?.let { addIntMember("ordinal", it) }
                        }
                    }
                }

                is IrModifyConstantValueInjection -> {
                    addAnnotation<ModifyExpressionValue> {
                        addStringMember("method", injection.method)
                        addAnnotationMember<At>("at") {
                            addStringMember("value", "CONSTANT")
                            addStringMember("args", "${injection.literalTypeName}Value=${injection.literalValue}")
                            injection.ordinal?.let { addIntMember("ordinal", it) }
                        }
                    }
                }
            }
            addParameters(injection.parameters.map { parameter ->
                when (parameter) {
                    is IrInjectionReceiverParameter -> {
                        buildJavaParameter("_receiver", parameter.type)
                    }

                    is IrInjectionArgumentParameter -> {
                        buildJavaParameter(parameter.name, parameter.type)
                    }

                    is IrInjectionOperationParameter -> {
                        buildJavaParameter(
                            "_original", Operation::class.asIr().parameterizedBy(parameter.returnType.orVoid())
                        )
                    }

                    is IrInjectionLiteralParameter -> {
                        buildJavaParameter("_original", parameter.type)
                    }

                    is IrInjectionLocalParameter -> {
                        buildJavaParameter(parameter.name, parameter.type)
                    }

                    is IrInjectionCallbackParameter -> {
                        buildJavaParameter("_callback", CallbackInfo::class.asIr())
                    }
                }
            })
            setReturnType(injection.returnType)
            val hookArgumentCodeBlocks = injection.hookArguments.map { argument ->
                when (argument) {
                    is IrHookCancelerArgument -> buildJavaCodeBlock("null")
                    is IrHookLiteralArgument -> buildJavaCodeBlock("_original")
                    is IrHookNamedLocalArgument -> buildJavaCodeBlock("null")
                    is IrHookOrdinalArgument -> buildJavaCodeBlock("%L") { arg(argument.ordinal) }
                    is IrHookParameterArgument -> buildJavaCodeBlock("null")
                    is IrHookPositionalLocalArgument -> buildJavaCodeBlock("null")
                    is IrHookReturnerArgument -> buildJavaCodeBlock("null")
                    is IrHookTargetArgument -> buildJavaCodeBlock("null")
                }
            }
            addStatement(
                buildJavaCodeBlock(
                    buildString {
                        if (injection.returnType != null) {
                            append("return ")
                        }
                        append("getOrInitPatch().%L(")
                        append(hookArgumentCodeBlocks.joinToString { "%L" })
                        append(")")
                    }
                ) {
                    arg(injection.hookName)
                    hookArgumentCodeBlocks.forEach { arg(it) }
                }
            )
        }

    private fun generateMixinAccessor(mixin: IrMixin, accessor: IrAccessor) {
        buildJavaInterface(accessor.type.simpleName) {
            addAnnotation<Mixin> {
                addClassMember("value", mixin.targetType)
            }
            addModifiers(JPModifier.PUBLIC)
            addMethods(accessor.kinds.map { method ->
                buildJavaMethod(method.internalName) {
                    addModifiers(
                        JPModifier.PUBLIC,
                        if (method.isStatic) JPModifier.STATIC else JPModifier.ABSTRACT,
                    )
                    if (method is IrMethodAccessor) {
                        addAnnotation<Invoker> {
                            addStringMember("value", method.vanillaName)
                        }
                    } else {
                        addAnnotation<Accessor> {
                            addStringMember("value", method.vanillaName)
                        }
                        if (method is IrFieldSetterAccessor) {
                            addAnnotation<Mutable>()
                        }
                    }
                    addParameters(method.parameters)
                    setReturnType(method.returnType)
                    if (method.isStatic) {
                        addStatement("throw new ${IllegalStateException::class.simpleName}()")
                    }
                }
            })
        }.toJavaFile(accessor.type.packageName).writeTo(codeGenerator, mixin.dependencies)

        extensionProperties += accessor.kinds.filterIsInstance<IrFieldGetterAccessor>().map { getter ->
            buildKotlinProperty(getter.name, getter.type) {
                if (getter.isStatic) {
                    setReceiverType(KClass::class.asIr().parameterizedBy(mixin.targetType))
                } else {
                    setReceiverType(mixin.targetType)
                }
                getter(buildKotlinGetter {
                    addStatement(
                        when {
                            getter.isStatic -> "return %T.%L()"
                            else -> "return (this as %T).%L()"
                        },
                        accessor.type.kotlin,
                        getter.internalName,
                    )
                })
                accessor.kinds.find { it is IrFieldSetterAccessor && it.name == getter.name }?.let { setter ->
                    mutable(true)
                    setter(buildKotlinSetter {
                        addParameters(setter.parameters)
                        addStatement(
                            when {
                                setter.isStatic -> "%T.%L(%L)"
                                else -> "(this as %T).%L(%L)"
                            },
                            accessor.type.kotlin,
                            setter.internalName,
                            setter.parameters.joinToString { it.name },
                        )
                    })
                }
            }
        }

        extensionFunctions += accessor.kinds.filterIsInstance<IrMethodAccessor>().map { method ->
            buildKotlinFunction(method.name) {
                if (method.isStatic) {
                    setReceiverType(KClass::class.asIr().parameterizedBy(mixin.targetType))
                } else {
                    setReceiverType(mixin.targetType)
                }
                addParameters(method.parameters)
                setReturnType(method.returnType)
                addStatement(
                    buildString {
                        if (method.returnType != null) {
                            append("return ")
                        }
                        append(
                            if (method.isStatic) "%T.%L(%L)"
                            else "(this as %T).%L(%L)"
                        )
                    },
                    accessor.type.kotlin,
                    method.internalName,
                    method.parameters.joinToString { it.name },
                )
            }
        }
    }

    private fun generateMixinExtension(mixin: IrMixin, extension: IrExtension) {
        buildKotlinInterface(extension.type.simpleName) {
            addFunctions(extension.kinds.map { method ->
                buildKotlinFunction(method.internalName) {
                    addModifiers(KPModifier.ABSTRACT)
                    addParameters(method.parameters)
                    setReturnType(method.returnType)
                }
            })
        }.toKotlinFile(extension.type.packageName).writeTo(codeGenerator, mixin.dependencies)

        extensionProperties += extension.kinds.filterIsInstance<IrFieldGetterExtension>().map { getter ->
            buildKotlinProperty(getter.name, getter.type) {
                setReceiverType(mixin.targetType)
                getter(buildKotlinGetter {
                    addStatement(
                        "return (this as %T).%L()",
                        extension.type.kotlin,
                        getter.internalName,
                    )
                })
                extension.kinds.find { it is IrFieldSetterExtension && it.name == getter.name }?.let { setter ->
                    mutable(true)
                    setter(buildKotlinSetter {
                        addParameters(setter.parameters)
                        addStatement(
                            "(this as %T).%L(%L)",
                            extension.type.kotlin,
                            setter.internalName,
                            setter.parameters.joinToString { it.name },
                        )
                    })
                }
            }
        }
        extensionFunctions += extension.kinds.filterIsInstance<IrMethodExtension>().map { method ->
            buildKotlinFunction(method.name) {
                setReceiverType(mixin.targetType)
                addParameters(method.parameters)
                setReturnType(method.returnType)
                addStatement(
                    "return (this as %T).%L(%L)",
                    extension.type.kotlin,
                    method.internalName,
                    method.parameters.joinToString { it.name },
                )
            }
        }
    }

    private fun generateExtensions(dependencies: KspDependencies) {
        if (extensionProperties.isEmpty() && extensionFunctions.isEmpty()) {
            return
        }
        buildKotlinFile(options.generatedPackageName, "Extensions") {
            addProperties(extensionProperties)
            addFunctions(extensionFunctions)
        }.writeTo(codeGenerator, dependencies)
    }

    private fun generateMixinConfig(rootMixins: List<IrMixin>) {
        codeGenerator.createResourceFile(
            path = "${options.modId}.mixins.json",
            contents = configJson.encodeToString(
                MixinConfig.of(
                    mixinPackage = options.mixinPackageName,
                    refmapFileName = options.refmapFileName,
                    qualifiedNames = rootMixins.flatMap { it.flattenTree() }.groupBy { it.side }
                        .mapValues { (_, mixins) ->
                            mixins.map { it.type.qualifiedName }
                        },
                ),
            ),
            aggregating = true,
        )
    }
}
