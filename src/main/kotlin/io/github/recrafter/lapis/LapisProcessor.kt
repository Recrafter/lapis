package io.github.recrafter.lapis

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.llamalad7.mixinextras.expression.Definition
import com.llamalad7.mixinextras.expression.Definitions
import com.llamalad7.mixinextras.expression.Expression
import com.llamalad7.mixinextras.expression.Expressions
import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.llamalad7.mixinextras.injector.ModifyReceiver
import com.llamalad7.mixinextras.injector.ModifyReturnValue
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Cancellable
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.asClassName
import io.github.recrafter.lapis.api.*
import io.github.recrafter.lapis.api.annotations.*
import io.github.recrafter.lapis.config.MixinConfig
import io.github.recrafter.lapis.extensions.addIfNotNull
import io.github.recrafter.lapis.extensions.atName
import io.github.recrafter.lapis.extensions.capitalizeWithPrefix
import io.github.recrafter.lapis.extensions.common.nameOfCallable
import io.github.recrafter.lapis.extensions.common.nullIfNot
import io.github.recrafter.lapis.extensions.common.unsafeLazy
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ksp.*
import io.github.recrafter.lapis.extensions.psi.PsiCallExpression
import io.github.recrafter.lapis.extensions.psi.PsiCallableExpression
import io.github.recrafter.lapis.extensions.psi.calleeName
import io.github.recrafter.lapis.extensions.psi.findPsiFunction
import io.github.recrafter.lapis.kj.KJClassName
import io.github.recrafter.lapis.kj.KJTypeName
import io.github.recrafter.lapis.utils.Descriptors
import io.github.recrafter.lapis.utils.LoggingProcessor
import io.github.recrafter.lapis.utils.PsiCompanion
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.spongepowered.asm.mixin.*
import org.spongepowered.asm.mixin.gen.Accessor
import org.spongepowered.asm.mixin.gen.Invoker
import org.spongepowered.asm.mixin.injection.*
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import javax.lang.model.element.Modifier
import kotlin.reflect.KClass

internal class LapisProcessor(
    arguments: Map<String, String>,
    private val generator: CodeGenerator,
    logger: KspLogger,
) : LoggingProcessor(logger) {

    private val modId: String by arguments
    private val mixinsPackage: String by arguments
    private val refmapFileName: String by arguments

    private val psiCompanion: PsiCompanion = PsiCompanion(logger)

    private val modPackage: String = mixinsPackage.substringBeforeLast(".")
    private val generatedPackage: String = "$mixinsPackage.generated"

    private val mixins: MutableMap<KJClassName, GeneratedMixin> = mutableMapOf()

    private val extensions: MutableMap<KJClassName, GeneratedExtension> = mutableMapOf()
    private val factories: MutableMap<KJClassName, GeneratedFactory> = mutableMapOf()

    private val wideners: MutableSet<String> = mutableSetOf()

    private val configJson: Json by unsafeLazy {
        Json { prettyPrint = true }
    }

    override fun run(resolver: Resolver) {
        resolver.forEachSymbolsAnnotatedWith<LaPatch> { symbol, patch, annotation ->
            kspRequire(symbol is KspClass && symbol.isClass && symbol.isAbstract, symbol)
            symbol.parentDeclaration?.let { parent ->
                val parentPatchAnnotation = parent.getSingleAnnotationOrNull<LaPatch>()
                kspRequire(parent is KspClass && parent.isClass && parentPatchAnnotation != null, symbol)
                if (patch.widener.isNotEmpty()) {
                    kspRequire(parentPatchAnnotation.widener.isNotEmpty(), parent)
                }
            }
            if (patch.widener.isNotEmpty()) {
                wideners += if (symbol.parentDeclaration != null) {
                    generateSequence(symbol) { it.parentDeclaration as? KspClass }
                        .toList()
                        .mapNotNull { it.getSingleAnnotationOrNull<LaPatch>() }
                        .asReversed()
                        .joinToString("$") { it.widener.removePrefix(".") }
                } else {
                    patch.widener
                }
            }

            val targetClassName = annotation.getKspClassArgument(LaPatch::target.name).asKJClassName()
            val patchSuperType = symbol.getSuperClassTypeOrNull()
            kspRequire(patchSuperType?.declaration?.isInstance<LapisPatch<*>>() == true, symbol)
            val patchGenericType = patchSuperType.genericTypes().singleOrNull()?.asKJTypeName()
            kspRequire(patchGenericType == targetClassName.typeName, symbol)

            val implClassName = KJClassName(modPackage, symbol.name + "_Impl")
            val mixinClassName = KJClassName(generatedPackage, symbol.name + "_Mixin")
            val accessorClassName = KJClassName(generatedPackage, symbol.name + "_Accessor")
            val bridgeClassName = KJClassName(modPackage, symbol.name + "_Bridge")
            val factoryClassName = KJClassName(modPackage, targetClassName.simpleName + "KFactory")

            val thisToBridgeCast = buildKotlinCast(to = bridgeClassName)
            val thisToAccessorCast = buildKotlinCast(to = accessorClassName)
            val implInstanceToAccessorCast = buildKotlinCast(
                from = KPCodeBlock.of(LapisPatch<*>::instance.name),
                to = accessorClassName
            )

            val implProperties = mutableListOf<KPProperty>()
            val implFunctions = mutableListOf<KPFunction>()
            val bridgeFunctions = mutableListOf<KPFunction>()
            val mixinMethods = mutableListOf<JPMethod>()
            val accessorMethods = mutableListOf<JPMethod>()
            val extensionProperties = mutableListOf<KPProperty>()
            val extensionFunctions = mutableListOf<KPFunction>()
            val factoryProperties = mutableListOf<KPProperty>()
            val factoryFunctions = mutableListOf<KPFunction>()
            val topLevelFunctions = mutableListOf<KPFunction>()

            val lazyPatchGetterCall = JPCodeBlock.of("getOrInitPatch()")
            symbol.properties.forEach { property ->
                restrictMixinAnnotations(property)
                if (property.isPrivate) {
                    return@forEach
                }
                val accessAnnotation = property.getSingleAnnotationOrNull<LaAccess>()
                if (accessAnnotation != null) {
                    kspRequire(property.isAbstract(), property)
                    val isStatic = property.hasAnnotation<LaStatic>()
                    val propertyTypeName = property.type.asKJTypeName()
                    val nameByUser = property.name
                    val vanillaName = accessAnnotation.vanillaName.ifEmpty { nameByUser }
                    val accessorGetter = buildAccessorMethod(
                        AccessorMethodType.GETTER,
                        vanillaName,
                        isStatic,
                        propertyTypeName,
                        nameByUser
                    )
                    accessorMethods += accessorGetter
                    val accessorSetter = nullIfNot(property.isMutable) {
                        buildAccessorMethod(
                            AccessorMethodType.SETTER,
                            vanillaName,
                            isStatic,
                            propertyTypeName,
                            nameByUser,
                        )
                    }
                    accessorMethods.addIfNotNull(accessorSetter)
                    val factoryProperty = nullIfNot(isStatic) {
                        buildKotlinProperty(nameByUser, propertyTypeName) {
                            getter(buildKotlinGetter {
                                addInvokeFunctionStatement(true, accessorClassName, accessorGetter.name())
                            })
                            if (accessorSetter != null) {
                                mutable(true)
                                setter(buildKotlinSetter {
                                    setParameters(SETTER_ARGUMENT_NAME to propertyTypeName)
                                    addInvokeFunctionStatement(
                                        false, accessorClassName, accessorSetter.name(), listOf(SETTER_ARGUMENT_NAME)
                                    )
                                })
                            }
                        }
                    }
                    factoryProperties.addIfNotNull(factoryProperty)
                    if (!isStatic) {
                        extensionProperties += buildKotlinProperty(nameByUser, propertyTypeName) {
                            setReceiverType(targetClassName)
                            getter(buildKotlinGetter {
                                addModifiers(KModifier.INLINE)
                                if (factoryProperty != null) {
                                    addGetterStatement(factoryClassName, factoryProperty.name)
                                } else {
                                    addInvokeFunctionStatement(true, thisToAccessorCast, accessorGetter.name())
                                }
                            })
                            if (accessorSetter != null) {
                                mutable(true)
                                setter(buildKotlinSetter {
                                    addModifiers(KModifier.INLINE)
                                    setParameters(SETTER_ARGUMENT_NAME to propertyTypeName)
                                    if (factoryProperty != null) {
                                        addSetterStatement(factoryClassName, factoryProperty.name, SETTER_ARGUMENT_NAME)
                                    } else {
                                        addInvokeFunctionStatement(
                                            true,
                                            thisToAccessorCast,
                                            accessorSetter.name(),
                                            listOf(SETTER_ARGUMENT_NAME)
                                        )
                                    }
                                })
                            }
                        }
                    }
                    implProperties += buildKotlinProperty(nameByUser, propertyTypeName) {
                        addModifiers(KModifier.OVERRIDE)
                        getter(buildKotlinGetter {
                            if (factoryProperty != null) {
                                addGetterStatement(factoryClassName, factoryProperty.name)
                            } else {
                                addInvokeFunctionStatement(true, implInstanceToAccessorCast, accessorGetter.name())
                            }
                        })
                        if (accessorSetter != null) {
                            mutable(true)
                            setter(buildKotlinSetter {
                                setParameters(SETTER_ARGUMENT_NAME to propertyTypeName)
                                if (factoryProperty != null) {
                                    addSetterStatement(factoryClassName, factoryProperty.name, SETTER_ARGUMENT_NAME)
                                } else {
                                    addInvokeFunctionStatement(
                                        true,
                                        implInstanceToAccessorCast,
                                        accessorSetter.name(),
                                        listOf(SETTER_ARGUMENT_NAME)
                                    )
                                }
                            })
                        }
                    }
                    return@forEach
                }
                val nameByUser = property.name
                val getterName = nameByUser.capitalizeWithPrefix("get")
                val setterName = nameByUser.capitalizeWithPrefix("set")
                val bridgeGetterName = getterName.withModId()
                val bridgeSetterName = setterName.withModId()
                val propertyTypeName = property.type.asKJTypeName()
                bridgeFunctions += buildKotlinFunction(bridgeGetterName) {
                    addModifiers(KModifier.ABSTRACT)
                    setReturnType(propertyTypeName)
                }
                mixinMethods += buildJavaMethod(bridgeGetterName) {
                    addAnnotation<Override>()
                    addModifiers(Modifier.PUBLIC)
                    setReturnType(propertyTypeName)
                    addInvokeFunctionStatement(true, lazyPatchGetterCall, getterName)
                }
                if (property.isMutable) {
                    bridgeFunctions += buildKotlinFunction(bridgeSetterName) {
                        addModifiers(KModifier.ABSTRACT)
                        setParameters(SETTER_ARGUMENT_NAME to propertyTypeName)
                    }
                    mixinMethods += buildJavaMethod(bridgeSetterName) {
                        addAnnotation<Override>()
                        addModifiers(Modifier.PUBLIC)
                        setParameters(propertyTypeName to SETTER_ARGUMENT_NAME)
                        addInvokeFunctionStatement(false, lazyPatchGetterCall, setterName, listOf(SETTER_ARGUMENT_NAME))
                    }
                }
                extensionProperties += buildKotlinProperty(nameByUser, propertyTypeName) {
                    setReceiverType(targetClassName)
                    getter(buildKotlinGetter {
                        addModifiers(KModifier.INLINE)
                        addInvokeFunctionStatement(true, thisToBridgeCast, bridgeGetterName)
                    })
                    if (property.isMutable) {
                        mutable(true)
                        setter(buildKotlinSetter {
                            addModifiers(KModifier.INLINE)
                            setParameters(SETTER_ARGUMENT_NAME to propertyTypeName)
                            addInvokeFunctionStatement(
                                false,
                                thisToBridgeCast,
                                bridgeSetterName,
                                listOf(SETTER_ARGUMENT_NAME)
                            )
                        })
                    }
                }
            }
            symbol.functions.forEach { function ->
                restrictMixinAnnotations(function)
                val accessAnnotation = function.getSingleAnnotationOrNull<LaAccess>()
                if (accessAnnotation != null) {
                    kspRequire(function.isAbstract, function)
                    val nameByUser = function.name
                    val parameterList = function.parameters.asKJParameterList()
                    val returnType = function.getReturnTypeOrNull()
                    val hasReturnType = returnType != null
                    val isStatic = function.hasAnnotation<LaStatic>()
                    val isConstructor = function.hasAnnotation<LaConstructor>()
                    if (isConstructor) {
                        val invokerMethod = buildInvokerMethod(
                            Descriptors.CONSTRUCTOR_METHOD_NAME,
                            true,
                            targetClassName.typeName,
                            nameByUser,
                            parameterList.javaVersion
                        )
                        accessorMethods += invokerMethod
                        val factoryFunction = buildKotlinFunction(nameByUser) {
                            setParameters(parameterList.kotlinVersion)
                            setReturnType(targetClassName)
                            addInvokeFunctionStatement(
                                true, accessorClassName, invokerMethod.name(), parameterList.names
                            )
                        }
                        factoryFunctions += factoryFunction
                        topLevelFunctions += buildKotlinFunction(targetClassName.simpleName) {
                            addModifiers(KModifier.INLINE)
                            setParameters(parameterList.kotlinVersion)
                            setReturnType(targetClassName)
                            addInvokeFunctionStatement(
                                true, factoryClassName, factoryFunction.name, parameterList.names
                            )
                        }
                        implFunctions += buildKotlinFunction(nameByUser) {
                            addModifiers(KModifier.OVERRIDE)
                            setParameters(parameterList.kotlinVersion)
                            setReturnType(targetClassName)
                            addInvokeFunctionStatement(
                                true, accessorClassName, invokerMethod.name(), parameterList.names
                            )
                        }
                    } else {
                        val invoker = buildInvokerMethod(
                            accessAnnotation.vanillaName.ifEmpty { nameByUser },
                            isStatic,
                            returnType,
                            nameByUser,
                            parameterList.javaVersion
                        )
                        accessorMethods += invoker

                        val factoryFunction = nullIfNot(isStatic) {
                            buildKotlinFunction(nameByUser) {
                                setParameters(parameterList.kotlinVersion)
                                addInvokeFunctionStatement(
                                    hasReturnType, accessorClassName, invoker.name(), parameterList.names
                                )
                                setReturnType(returnType)
                            }
                        }
                        factoryFunctions.addIfNotNull(factoryFunction)

                        if (!isStatic) {
                            extensionFunctions += buildKotlinFunction(nameByUser) {
                                addModifiers(KModifier.INLINE)
                                setReceiverType(targetClassName)
                                setParameters(parameterList.kotlinVersion)
                                if (factoryFunction != null) {
                                    addInvokeFunctionStatement(
                                        hasReturnType, factoryClassName, factoryFunction.name, parameterList.names
                                    )
                                } else {
                                    addInvokeFunctionStatement(
                                        hasReturnType, thisToAccessorCast, invoker.name(), parameterList.names
                                    )
                                }
                                setReturnType(returnType)
                            }
                        }
                        implFunctions += buildKotlinFunction(nameByUser) {
                            addModifiers(KModifier.OVERRIDE)
                            setParameters(parameterList.kotlinVersion)
                            if (factoryFunction != null) {
                                addInvokeFunctionStatement(
                                    hasReturnType, factoryClassName, factoryFunction.name, parameterList.names
                                )
                            } else {
                                addInvokeFunctionStatement(
                                    hasReturnType, implInstanceToAccessorCast, invoker.name(), parameterList.names
                                )
                            }
                            setReturnType(returnType)
                        }
                    }
                    return@forEach
                }
                val hookAnnotation = function.getSingleAnnotationOrNull<LaHook>()
                if (hookAnnotation != null) {
                    kspRequire(function.isPublic, function)
                    mixinMethods += resolveHook(function, hookAnnotation, lazyPatchGetterCall)
                } else if (function.isPublic) {
                    val nameByUser = function.name
                    val bridgeName = nameByUser.withModId()
                    val parameterList = function.parameters.asKJParameterList()
                    val returnType = function.getReturnTypeOrNull()
                    val hasReturnType = returnType != null
                    bridgeFunctions += buildKotlinFunction(bridgeName) {
                        addModifiers(KModifier.ABSTRACT)
                        setParameters(parameterList.kotlinVersion)
                        setReturnType(returnType)
                    }
                    mixinMethods += buildJavaMethod(bridgeName) {
                        addAnnotation<Override>()
                        addModifiers(Modifier.PUBLIC)
                        setReturnType(returnType)
                        setParameters(parameterList.javaVersion)
                        addInvokeFunctionStatement(hasReturnType, lazyPatchGetterCall, nameByUser, parameterList.names)
                    }
                    extensionFunctions += buildKotlinFunction(nameByUser) {
                        addModifiers(KModifier.INLINE)
                        setReceiverType(targetClassName)
                        setParameters(parameterList.kotlinVersion)
                        setReturnType(returnType)
                        addInvokeFunctionStatement(hasReturnType, thisToBridgeCast, bridgeName, parameterList.names)
                    }
                    return@forEach
                }
            }
            accumulateMixin(
                patch.side,
                mixinClassName,
                accessorClassName,
                targetClassName.typeName,
                symbol.asKJClassName().typeName,
                implClassName,
                bridgeClassName,
            ) {
                symbols += symbol
                this.mixinMethods += mixinMethods
                this.accessorMethods += accessorMethods
                this.bridgeFunctions += bridgeFunctions
                this.implProperties += implProperties
                this.implFunctions += implFunctions
            }
            accumulateExtension(targetClassName) {
                symbols += symbol
                this.topLevelFunctions += topLevelFunctions
                properties += extensionProperties
                functions += extensionFunctions
            }
            accumulateFactory(factoryClassName) {
                symbols += symbol
                properties += factoryProperties
                functions += factoryFunctions
            }
        }
    }

    override fun finish() {
        val mixinQualifiedNames = mutableMapOf<LapisPatchSide, MutableList<String>>()
        mixins.forEach { (mixinClassName, mixin) ->
            if (mixin.accessorMethods.isNotEmpty()) {
                buildJavaInterface(mixin.accessorClassName.simpleName) {
                    addAnnotation<Mixin> {
                        addClassMember(DEFAULT_ANNOTATION_ELEMENT_NAME, mixin.targetTypeName)
                    }
                    addModifiers(Modifier.PUBLIC)
                    addMethods(mixin.accessorMethods)
                }.toJavaFile(mixin.accessorClassName.packageName).writeTo(generator, mixin.symbols.toDependencies())
                mixinQualifiedNames.getOrPut(mixin.side) { mutableListOf() }.add(mixin.accessorClassName.qualifiedName)
            }
            buildKotlinClass(mixin.implClassName.simpleName) {
                val propertyName = LapisPatch<*>::instance.name
                setConstructor(propertyName to mixin.targetTypeName)
                setSuperClassType(mixin.patchTypeName)
                addProperty(buildKotlinProperty(propertyName, mixin.targetTypeName) {
                    addModifiers(KModifier.OVERRIDE)
                    initializer(propertyName)
                })
                addProperties(mixin.implProperties)
                addFunctions(mixin.implFunctions)
            }.toKotlinFile(mixin.implClassName.packageName).writeTo(generator, mixin.symbols.toDependencies())
            if (mixin.bridgeFunctions.isNotEmpty()) {
                buildKotlinInterface(mixin.bridgeClassName.simpleName) {
                    addFunctions(mixin.bridgeFunctions)
                }.toKotlinFile(mixin.bridgeClassName.packageName).writeTo(generator, mixin.symbols.toDependencies())
            }
            buildJavaClass(mixinClassName.simpleName) {
                addAnnotation<Mixin> {
                    addClassMember(DEFAULT_ANNOTATION_ELEMENT_NAME, mixin.targetTypeName)
                }
                if (mixin.bridgeFunctions.isNotEmpty()) {
                    addSuperinterface(mixin.bridgeClassName.javaVersion)
                }
                val implFieldName = "patch"
                val implGetterName = implFieldName.capitalizeWithPrefix("getOrInit")
                addField(buildJavaField(mixin.implClassName.javaVersion, implFieldName) {
                    addAnnotation<Unique>()
                    addModifiers(Modifier.PRIVATE)
                })
                addMethod(buildJavaMethod(implGetterName) {
                    addAnnotation<Unique>()
                    addModifiers(Modifier.PRIVATE)
                    setReturnType(mixin.implClassName.javaVersion)
                    addIfStatement(JPCodeBlock.of("$implFieldName == ${null.toString()}")) {
                        val objectCast = buildJavaCast(to = JPObject)
                        val targetCast = buildJavaCast(to = mixin.targetTypeName.javaVersion, from = objectCast)
                        addStatement(
                            "\$L = new \$T(\$L)",
                            implFieldName,
                            mixin.implClassName.javaVersion,
                            targetCast
                        )
                    }
                    addReturnStatement(implFieldName)
                })
                addMethods(mixin.mixinMethods)
            }.toJavaFile(mixinClassName.packageName).writeTo(generator, mixin.symbols.toDependencies())
            mixinQualifiedNames.getOrPut(mixin.side) { mutableListOf() }.add(mixinClassName.qualifiedName)
        }
        extensions.forEach { (className, extension) ->
            if (extension.isEmpty()) {
                return@forEach
            }
            buildKotlinFile(className.packageName, className.simpleName + "Ext") {
                extension.typeAliases.forEach {
                    addTypeAlias(it)
                }
                addFunctions(extension.topLevelFunctions)
                addProperties(extension.properties)
                addFunctions(extension.functions)
            }.writeTo(generator, extension.symbols.toDependencies())
        }
        factories.forEach { (className, factory) ->
            if (factory.isEmpty()) {
                return@forEach
            }
            buildKotlinObject(className.simpleName) {
                addProperties(factory.properties)
                addFunctions(factory.functions)
            }.toKotlinFile(className.packageName).writeTo(generator, factory.symbols.toDependencies())
        }
        generator.createResourceFile(
            path = "wideners.txt",
            contents = wideners.joinToString("\n"),
            aggregating = true,
        )
        generator.createResourceFile(
            path = "$modId.mixins.json",
            contents = configJson.encodeToString(
                MixinConfig.of(mixinsPackage, refmapFileName, mixinQualifiedNames)
            ),
            aggregating = true,
        )
        reset()
    }

    override fun onError() {
        reset()
    }

    @OptIn(UnsafeCastFunction::class)
    private fun resolveHook(function: KspFunction, hookAnnotation: LaHook, lazyPatchGetterCall: JPCodeBlock): JPMethod {
        val handleParameter = function.parameters.singleOrNull {
            it.hasAnnotation<LaMethod>() || it.hasAnnotation<LaConstructor>()
        }
        kspRequire(handleParameter != null, function)
        val handleType = handleParameter.type.asKJTypeName().kotlinVersion.safeAs<KPParameterizedTypeName>()?.rawType
        kspRequire(
            !handleParameter.type.resolve().isMarkedNullable && handleType == LapisHandle::class.asClassName(),
            handleParameter
        )
        val handleRawType = handleType.asKJTypeName().javaVersion
        val handleFunctionType = handleParameter.type.resolve().genericTypes().singleOrNull()
        kspRequire(handleFunctionType?.isFunctionType == true, handleParameter)
        val psiParameters = psiCompanion.loadPsiFile(logger, function)
            .findPsiFunction { it.name == function.name }
            ?.valueParameters
        val psiHandleParameter = psiParameters?.firstOrNull { it.name == handleParameter.requireName() }
        kspRequire(psiHandleParameter != null, handleParameter) {
            resolvingError(handleParameter)
        }
        val psiHandleOfLambda = psiHandleParameter.defaultValue.safeAs<PsiCallExpression>()
        val handleTopLevelFactoryName = nameOfCallable<(() -> () -> Unit) -> LapisHandle<() -> Unit>> { ::handleOf }
        kspRequire(psiHandleOfLambda?.calleeName == handleTopLevelFactoryName, handleParameter)
        val psiHandleCallable = psiHandleOfLambda
            .lambdaArguments
            .singleOrNull()
            ?.getLambdaExpression()
            ?.bodyExpression
            ?.statements
            ?.singleOrNull()
            ?.safeAs<PsiCallableExpression>()
        kspRequire(psiHandleCallable != null, handleParameter)
        val mixinMethodParameters = mutableListOf<JPParameter>()
        val hookArgumentsArray = arrayOfNulls<HookArgument>(function.parameters.size)
        val signatureParameters = mutableListOf<KspValueParameter>()

        hookArgumentsArray[function.parameters.indexOf(handleParameter)] = NamedHookArgument(
            handleRawType,
            "_handle",
            JPCodeBlock.of("\$T.of()", handleRawType)
        )
        val handleFunctionGenericTypes = handleFunctionType.genericTypes().map { it.asKJTypeName() }
        val handleFunctionReturnType = handleFunctionGenericTypes.last()
        function.parameters.forEachIndexed { index, argument ->
            if (argument.annotations.toList().isEmpty()) {
                val parameter = function.parameters[index]
                hookArgumentsArray[index] = InlineHookArgument(JPCodeBlock.of(parameter.requireName()))
                signatureParameters += parameter
            }
        }
        mixinMethodParameters.addAll(signatureParameters.map {
            buildJavaParameter(it.type.asKJTypeName(), it.requireName())
        })

        val (annotation, methodReturnType) = if (hookAnnotation.wrap == LapisWrapKind.Constant) {
            val constParameter = function.parameters.singleOrNull { it.hasAnnotation<LaConst>() }
            kspRequire(constParameter != null, function)
            val constAnnotation = constParameter.annotations.singleOrNull()
            kspRequire(constAnnotation != null, function)
            val psiConstParameter = psiParameters.firstOrNull { it.name == constParameter.requireName() }
            kspRequire(psiConstParameter != null, constParameter) {
                psiCompanion.resolvingError(logger, constParameter)
            }
            val psiConstAnnotation = psiConstParameter.annotationEntries.singleOrNull()
            kspRequire(psiConstAnnotation != null, constParameter) {
                psiCompanion.resolvingError(logger, constParameter)
            }
            val psiConstArgumentName = psiConstAnnotation.valueArguments.singleOrNull()
                ?.getArgumentName()?.asName?.asString()
            kspRequire(psiConstArgumentName != null, constParameter)
            val constArgument = constAnnotation.arguments.find { psiConstArgumentName == it.requireName() }
            kspRequire(constArgument != null, constParameter)
            val constType = when (constArgument.value) {
                is Int -> KPInt
                is Float -> KPFloat
                is Long -> KPLong
                is Double -> KPDouble
                is String -> KPString
                else -> kspError(constArgument)
            }.asKJTypeName()
            val constParameterType = constParameter.type.asKJTypeName()
            kspRequire(constType == constParameterType, constParameter)
            mixinMethodParameters.add(buildJavaParameter(constParameterType, "_original"))
            hookArgumentsArray[function.parameters.indexOf(constParameter)] = InlineHookArgument(
                JPCodeBlock.of("_original")
            )
            buildJavaAnnotation<ModifyExpressionValue> {
                addStringMember(
                    "method",
                    Descriptors.forMethod(
                        psiHandleCallable.callableReference.text,
                        null,
                        handleFunctionGenericTypes.drop(1).dropLast(1),
                        handleFunctionReturnType
                    )
                )
                addAnnotationMember<At>("at") {
                    addStringMember(DEFAULT_ANNOTATION_ELEMENT_NAME, "CONSTANT")
                    addStringMember(
                        "args",
                        constArgument.requireName() + "Value=" + constArgument.value
                    )
                    addIntMember("ordinal", 0)
                }
            } to constParameterType
        } else {
            val targetParameter = function.parameters.singleOrNull { it.hasAnnotation<LaTarget>() }
            kspRequire(targetParameter != null, function)
            val targetType =
                targetParameter.type.asKJTypeName().kotlinVersion.safeAs<KPParameterizedTypeName>()?.rawType
            kspRequire(
                !targetParameter.type.resolve().isMarkedNullable && targetType == LapisTarget::class.asClassName(),
                targetParameter
            )
            val targetGenericType = targetParameter.type.resolve().genericTypes().singleOrNull()
            kspRequire(targetGenericType != null, targetParameter)
            kspRequire(targetGenericType.isFunctionType, targetParameter)
            val targetFunctionGenericTypes = targetGenericType.genericTypes().map { it.asKJTypeName() }
            val targetFunctionReturnType = targetFunctionGenericTypes.last()
            val psiTargetParameter = psiParameters.firstOrNull { it.name == targetParameter.requireName() }
            kspRequire(psiTargetParameter != null, handleParameter) {
                psiCompanion.resolvingError(logger, targetParameter)
            }
            val psiTargetOfLambda = psiTargetParameter.defaultValue.safeAs<PsiCallExpression>()
            val targetTopLevelFactoryName = nameOfCallable<(() -> () -> Unit) -> LapisTarget<() -> Unit>> { ::targetOf }
            kspRequire(psiTargetOfLambda?.calleeName == targetTopLevelFactoryName, handleParameter)
            val psiTargetCallable = psiTargetOfLambda
                .lambdaArguments
                .singleOrNull()
                ?.getLambdaExpression()
                ?.bodyExpression
                ?.statements
                ?.singleOrNull()
                ?.safeAs<PsiCallableExpression>()
            kspRequire(psiTargetCallable != null, targetParameter)
            mixinMethodParameters.add(
                buildJavaParameter(
                    Operation::class.asClassName().asKJClassName()
                        .parameterizedBy(targetFunctionReturnType.boxed)
                        .javaVersion,
                    "_operation"
                )
            )
            val genericsCount = targetGenericType.genericTypes().size
            val arity = genericsCount - 1
            val targetRawType = targetType.asKJTypeName().javaVersion

            hookArgumentsArray[function.parameters.indexOf(targetParameter)] = NamedHookArgument(
                targetRawType,
                "_target",
                if (arity > 22) {
                    JPCodeBlock.of(
                        "\$T.of(_operation, \$L)",
                        targetRawType,
                        arity
                    )
                } else {
                    val operationCallArgumentNames = signatureParameters.map { "_" + it.requireName() }
                    val lambdaParameterNames = operationCallArgumentNames.toMutableList().apply {
                        if (hookAnnotation.wrap == LapisWrapKind.Method) {
                            addFirst("__instance")
                        }
                    }
                    JPCodeBlock.of(
                        "\$T.of(_operation, (\$T) (${lambdaParameterNames.joinToString()}) -> " +
                            "_operation.call(${operationCallArgumentNames.joinToString()}))",
                        targetRawType,
                        JPClassName.get("kotlin.jvm.functions", "Function$arity"),
                    )
                }
            )

            when (hookAnnotation.wrap) {
                LapisWrapKind.Method -> {
                    buildJavaAnnotation<WrapMethod> {
                        addStringMember(
                            "method",
                            Descriptors.forMethod(
                                psiHandleCallable.callableReference.text,
                                null,
                                handleFunctionGenericTypes.drop(1).dropLast(1),
                                handleFunctionReturnType
                            )
                        )
                    }
                }

                LapisWrapKind.Operation -> {
                    buildJavaAnnotation<WrapOperation> {
                        addStringMember(
                            "method",
                            Descriptors.forMethod(
                                psiHandleCallable.callableReference.text,
                                null,
                                handleFunctionGenericTypes.drop(1).dropLast(1),
                                handleFunctionReturnType
                            )
                        )
                        addAnnotationMember<At>("at") {
                            addStringMember(DEFAULT_ANNOTATION_ELEMENT_NAME, "INVOKE")
                            addStringMember(
                                "target",
                                Descriptors.forMethod(
                                    psiTargetCallable.callableReference.text,
                                    targetFunctionGenericTypes.first(),
                                    targetFunctionGenericTypes.drop(1).dropLast(1),
                                    targetFunctionReturnType
                                )
                            )
                            addIntMember("ordinal", 0)
                        }
                    }
                }

                else -> TODO()
            } to targetFunctionReturnType
        }

        val returnParameter = function.parameters.singleOrNull { it.hasAnnotation<LaReturn>() }
        if (returnParameter != null) {
            val isReturnable = handleFunctionReturnType.kotlinVersion != KPUnit
            if (isReturnable) {
                kspRequire(
                    !returnParameter.type.resolve().isMarkedNullable &&
                        returnParameter.type.asKJTypeName().kotlinVersion ==
                        LapisReturner::class.asClassName().asKJClassName()
                            .parameterizedBy(handleFunctionReturnType).kotlinVersion,
                    returnParameter
                )
            } else {
                kspRequire(
                    !returnParameter.type.resolve().isMarkedNullable &&
                        returnParameter.type.asKJTypeName() == LapisCanceler::class.asClassName().asKJTypeName(),
                    returnParameter
                )
            }
            mixinMethodParameters.add(
                buildJavaParameter(
                    if (isReturnable) {
                        CallbackInfoReturnable::class.asClassName().asKJClassName()
                            .parameterizedBy(handleFunctionReturnType.boxed)
                            .javaVersion
                    } else {
                        CallbackInfo::class.asClassName().asKJClassName().javaVersion
                    },
                    "_callback"
                ) {
                    addAnnotation(Cancellable::class.asClassName().asKJClassName().javaVersion)
                }
            )
            hookArgumentsArray[function.parameters.indexOf(returnParameter)] = if (isReturnable) {
                val returnerType = LapisReturner::class.asClassName().asKJClassName().javaVersion
                NamedHookArgument(returnerType, "_returner", JPCodeBlock.of("\$T.of(_callback)", returnerType))
            } else {
                val cancelerType = LapisCanceler::class.asClassName().asKJClassName().javaVersion
                NamedHookArgument(cancelerType, "_canceler", JPCodeBlock.of("\$T.of(_callback)", cancelerType))
            }
        }
        return buildJavaMethod(function.name) {
            addAnnotation(annotation)
            addModifiers(Modifier.PRIVATE)
            setReturnType(methodReturnType.javaVersion)
            addParameters(mixinMethodParameters)
            hookArgumentsArray.filterIsInstance<NamedHookArgument>().forEach {
                addStatement(
                    "\$T \$L = \$L",
                    it.type,
                    it.name,
                    it.value,
                )
            }
            addStatement(
                buildString {
                    if (methodReturnType.javaVersion != JPVoid) {
                        append("return ")
                    }
                    append("\$L.\$L(\$L)")
                },
                lazyPatchGetterCall,
                function.name,
                hookArgumentsArray.joinToString { it?.statement.toString() },
            )
        }
    }

    private fun buildAccessorMethod(
        methodType: AccessorMethodType, target: String, isStatic: Boolean, type: KJTypeName, propertyName: String,
    ): JPMethod =
        buildJavaMethod(methodType.buildMethodName(propertyName)) {
            val isSetter = methodType == AccessorMethodType.SETTER
            addAnnotation<Accessor> {
                addStringMember(DEFAULT_ANNOTATION_ELEMENT_NAME, target)
            }
            if (isSetter) {
                addAnnotation<Mutable>()
            }
            addModifiers(
                Modifier.PUBLIC,
                if (isStatic) Modifier.STATIC else Modifier.ABSTRACT
            )
            if (isSetter) {
                setParameters(type to propertyName)
            }
            if (isStatic && !isSetter) {
                addStubStatement()
            }
            setReturnType(type.takeUnless { isSetter })
        }

    private fun buildInvokerMethod(
        target: String, isStatic: Boolean, returnType: KJTypeName?, name: String, parameters: List<JPParameter>,
    ): JPMethod =
        buildJavaMethod(name.capitalizeWithPrefix("invoke")) {
            addAnnotation<Invoker> {
                addStringMember(DEFAULT_ANNOTATION_ELEMENT_NAME, target)
            }
            addModifiers(
                Modifier.PUBLIC,
                if (isStatic) Modifier.STATIC else Modifier.ABSTRACT
            )
            setParameters(parameters)
            if (isStatic) {
                addStubStatement()
            }
            setReturnType(returnType)
        }

    private fun accumulateExtension(targetClassName: KJClassName, block: GeneratedExtension.() -> Unit) {
        extensions
            .getOrPut(targetClassName) { GeneratedExtension() }
            .apply(block)
    }

    private fun accumulateFactory(targetClassName: KJClassName, block: GeneratedFactory.() -> Unit) {
        factories
            .getOrPut(targetClassName) { GeneratedFactory() }
            .apply(block)
    }

    private fun accumulateMixin(
        side: LapisPatchSide,
        className: KJClassName,
        accessorClassName: KJClassName,
        targetTypeName: KJTypeName,
        patchTypeName: KJTypeName,
        implClassName: KJClassName,
        bridgeClassName: KJClassName,
        block: GeneratedMixin.() -> Unit,
    ) {
        mixins
            .getOrPut(className) {
                GeneratedMixin(
                    side = side,
                    targetTypeName = targetTypeName,
                    patchTypeName = patchTypeName,
                    implClassName = implClassName,
                    bridgeClassName = bridgeClassName,
                    accessorClassName = accessorClassName,
                )
            }
            .apply(block)
    }

    private fun restrictMixinAnnotations(declaration: KspDeclaration) {
        mixinAnnotations.forEach { annotation ->
            kspRequire(!declaration.hasAnnotation(annotation), declaration) {
                """
                Direct use of Mixin or MixinExtras annotations is restricted.
                The ${annotation.atName} annotation is managed internally by Lapis Hooks.
                Please use the corresponding @Hook* annotation instead.
                """.trimIndent()
            }
        }
    }

    private fun reset() {
        mixins.clear()
        extensions.clear()
        factories.clear()

        wideners.clear()
    }

    private fun String.withModId(): String =
        "${modId}_$this"

    private enum class AccessorMethodType(val prefix: String) {

        GETTER("get"),
        SETTER("set");

        fun buildMethodName(originalName: String): String =
            originalName.capitalizeWithPrefix(prefix)
    }

    sealed interface HookArgument {
        val statement: String
    }

    class NamedHookArgument(
        val type: JPTypeName,
        val name: String,
        val value: JPCodeBlock,
    ) : HookArgument {
        override val statement: String = name
    }

    class InlineHookArgument(
        val value: JPCodeBlock,
    ) : HookArgument {
        override val statement: String
            get() = value.toString()
    }

    class GeneratedExtension(
        val symbols: MutableSet<KspAnnotated> = mutableSetOf(),
        val typeAliases: MutableList<KPTypeAlias> = mutableListOf(),
        val topLevelFunctions: MutableList<KPFunction> = mutableListOf(),
        val properties: MutableList<KPProperty> = mutableListOf(),
        val functions: MutableList<KPFunction> = mutableListOf(),
    ) {
        fun isEmpty(): Boolean =
            typeAliases.isEmpty() && topLevelFunctions.isEmpty() && properties.isEmpty() && functions.isEmpty()
    }

    class GeneratedFactory(
        val symbols: MutableSet<KspAnnotated> = mutableSetOf(),
        val properties: MutableList<KPProperty> = mutableListOf(),
        val functions: MutableList<KPFunction> = mutableListOf(),
    ) {
        fun isEmpty(): Boolean =
            properties.isEmpty() && functions.isEmpty()
    }

    class GeneratedMixin(
        val side: LapisPatchSide,
        val targetTypeName: KJTypeName,
        val patchTypeName: KJTypeName,
        val implClassName: KJClassName,
        val bridgeClassName: KJClassName,
        val accessorClassName: KJClassName,
        val symbols: MutableSet<KspAnnotated> = mutableSetOf(),
        val mixinMethods: MutableList<JPMethod> = mutableListOf(),
        val accessorMethods: MutableList<JPMethod> = mutableListOf(),
        val bridgeFunctions: MutableList<KPFunction> = mutableListOf(),
        val implProperties: MutableList<KPProperty> = mutableListOf(),
        val implFunctions: MutableList<KPFunction> = mutableListOf(),
    )

    companion object {
        private const val DEFAULT_ANNOTATION_ELEMENT_NAME: String = "value"
        private const val SETTER_ARGUMENT_NAME: String = "newValue"

        private val mixinAnnotations: List<KClass<out Annotation>> = listOf(
            Overwrite::class,
            Debug::class,
            Dynamic::class,
            Final::class,
            Intrinsic::class,
            Mutable::class,
            Shadow::class,
            SoftOverride::class,
            Unique::class,
            Accessor::class,
            Invoker::class,
            Inject::class,
            ModifyArg::class,
            ModifyArgs::class,
            ModifyConstant::class,
            ModifyVariable::class,
            Redirect::class,
            Surrogate::class,

            Expression::class,
            Definition::class,
            Definitions::class,
            Expressions::class,
            ModifyExpressionValue::class,
            ModifyReceiver::class,
            ModifyReturnValue::class,
            @Suppress("DEPRECATION") com.llamalad7.mixinextras.injector.WrapWithCondition::class,
            WrapWithCondition::class,
            WrapMethod::class,
            WrapOperation::class,
        )
    }
}
