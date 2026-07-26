package io.github.recrafter.lapis.phases.parser

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import io.github.recrafter.lapis.annotations.*
import io.github.recrafter.lapis.annotations.Origin
import io.github.recrafter.lapis.common.JvmClassName
import io.github.recrafter.lapis.common.KSBaseTypes
import io.github.recrafter.lapis.common.isUnit
import io.github.recrafter.lapis.common.toFunctionTypeOrNull
import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.ks.*
import io.github.recrafter.lapis.extensions.ksp.KSPOrigin
import io.github.recrafter.lapis.extensions.ksp.getSymbolsAnnotatedWith
import io.github.recrafter.lapis.logging.Logger
import io.github.recrafter.lapis.phases.parser.models.ParserPrepareResult
import io.github.recrafter.lapis.phases.parser.models.ParserResult
import io.github.recrafter.lapis.phases.parser.models.common.*
import io.github.recrafter.lapis.phases.parser.models.patches.*
import io.github.recrafter.lapis.phases.parser.models.schemas.*
import java.lang.annotation.RetentionPolicy
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class SymbolParser(
    private val resolver: Resolver,
    private val baseTypes: KSBaseTypes,
    @Suppress("unused") private val logger: Logger,
) {
    fun prepare(): ParserPrepareResult =
        ParserPrepareResult(
            resolver.getSymbolsAnnotatedWith<Class>().filterIsInstance<KSClassDeclaration>().toList(),
            resolver.getSymbolsAnnotatedWith<Patch>().filterIsInstance<KSClassDeclaration>().toList(),
        )

    fun parse(): ParserResult =
        prepare().run {
            ParserResult(
                schemas = schemaClassDeclarations.map(::parseSchema),
                patches = patchClassDeclarations.map(::parsePatch),
            )
        }

    private fun parseSchema(
        classDeclaration: KSClassDeclaration,
        parentJvmClassName: JvmClassName? = null,
    ): ParsedSchema {
        val classAnnotation = classDeclaration.findAnnotation<Class>()
        val innerClassAnnotation = classDeclaration.findAnnotation<InnerClass>()
        val localClassAnnotation = classDeclaration.findAnnotation<LocalClass>()
        val anonymousClassAnnotation = classDeclaration.findAnnotation<AnonymousClass>()
        val isAccessible = classDeclaration.parentDeclarations(includeSelf = true).none {
            it.hasAnnotation<LocalClass>() || it.hasAnnotation<AnonymousClass>()
        }
        val (currentJvmClassName, originClassDeclaration, side) = when {
            parentJvmClassName == null -> {
                val type = classAnnotation?.getArgumentValue(Class::type)?.toClassDeclaration()
                val qualifiedName = classAnnotation?.getArgumentValue(Class::name) ?: type?.qualifiedName?.asString()
                val rootJvmClassName = qualifiedName?.let { JvmClassName.of(it) }
                Triple(
                    rootJvmClassName,
                    type ?: qualifiedName?.let {
                        resolver.getClassDeclarationByName(resolver.getKSNameFromString(it))
                    },
                    classAnnotation?.getArgumentValue(Class::side),
                )
            }

            innerClassAnnotation != null -> {
                val type = innerClassAnnotation.getArgumentValue(InnerClass::type)?.toClassDeclaration()
                val name = innerClassAnnotation.getArgumentValue(InnerClass::name) ?: type?.simpleName?.asString()
                val innerJvmClassName = name?.let(parentJvmClassName::inner)
                val classDeclaration = if (isAccessible) {
                    innerJvmClassName?.qualifiedName?.let {
                        resolver.getClassDeclarationByName(resolver.getKSNameFromString(it))
                    }
                } else {
                    innerClassAnnotation.getArgumentValue(InnerClass::delegate)?.toClassDeclaration()
                }
                Triple(
                    innerJvmClassName,
                    classDeclaration,
                    innerClassAnnotation.getArgumentValue(InnerClass::side),
                )
            }

            localClassAnnotation != null -> {
                val index = localClassAnnotation.getArgumentValue(LocalClass::index)
                val name = localClassAnnotation.getArgumentValue(LocalClass::name)
                val localJvmClassName = if (index != null && name != null) {
                    parentJvmClassName.local(index, name)
                } else null
                Triple(
                    localJvmClassName,
                    localClassAnnotation.getArgumentValue(LocalClass::delegate)?.toClassDeclaration(),
                    localClassAnnotation.getArgumentValue(LocalClass::side),
                )
            }

            anonymousClassAnnotation != null -> {
                val index = anonymousClassAnnotation.getArgumentValue(AnonymousClass::index)
                val anonymousJvmClassName = index?.let { parentJvmClassName.anonymous(it) }
                Triple(
                    anonymousJvmClassName,
                    anonymousClassAnnotation.getArgumentValue(AnonymousClass::delegate)?.toClassDeclaration(),
                    anonymousClassAnnotation.getArgumentValue(AnonymousClass::side),
                )
            }

            else -> Triple(null, null, null)
        }
        val accessAnnotation = classDeclaration.findAnnotation<Access>()
        val (schemaClassDeclarations, descriptorClassDeclarations) = classDeclaration.innerClassDeclarations.partition {
            it.hasAnnotation<Class>() || it.hasAnnotation<InnerClass>() ||
                it.hasAnnotation<LocalClass>() || it.hasAnnotation<AnonymousClass>()
        }
        return ParsedSchema(
            classDeclaration = classDeclaration,
            side = side ?: Side.Common,
            isTopLevel = classDeclaration.parentDeclaration == null,
            hasPackageName = classDeclaration.packageName.asString().isNotEmpty(),
            originClassDeclaration = originClassDeclaration,
            originJvmClassName = currentJvmClassName,
            hasClassAnnotation = classAnnotation != null,
            hasInnerClassAnnotation = innerClassAnnotation != null,
            hasLocalClassAnnotation = localClassAnnotation != null,
            hasAnonymousClassAnnotation = anonymousClassAnnotation != null,
            isAccessible = isAccessible,
            hasAccessAnnotation = accessAnnotation != null,
            isAccessUnfinal = accessAnnotation?.getArgumentValue(Access::unfinal) == true,
            accessStrategy = accessAnnotation?.getArgumentValue(Access::strategy),
            descriptors = descriptorClassDeclarations.map(::parseDescriptor),
            nestedSchemas = schemaClassDeclarations.map { parseSchema(it, currentJvmClassName) },
        )
    }

    private fun parseDescriptor(classDeclaration: KSClassDeclaration): ParsedDescriptor {
        val fieldAnnotation = classDeclaration.findAnnotation<Field<*>>()
        val methodAnnotation = classDeclaration.findAnnotation<Method<*>>()
        val constructorAnnotation = classDeclaration.findAnnotation<Constructor<*>>()
        val accessAnnotation = classDeclaration.findAnnotation<Access>()
        val mappingNameAnnotation = classDeclaration.findAnnotation<MappingName>()
        val annotationTypeArgument = fieldAnnotation?.findTypeArgument("T")
            ?: methodAnnotation?.findTypeArgument("F")
            ?: constructorAnnotation?.findTypeArgument("F")
        return ParsedDescriptor(
            name = classDeclaration.name,
            classDeclaration = classDeclaration,
            isObject = classDeclaration.isObject,
            hasFieldAnnotation = fieldAnnotation != null,
            hasMethodAnnotation = methodAnnotation != null,
            hasConstructorAnnotation = constructorAnnotation != null,
            isStatic = fieldAnnotation?.getArgumentValue(Field<*>::static) == true ||
                methodAnnotation?.getArgumentValue(Method<*>::static) == true,
            hasMappingNameAnnotation = mappingNameAnnotation != null,
            explicitMappingName = mappingNameAnnotation?.getArgumentValue(MappingName::name, explicit = true),
            hasAccessAnnotation = accessAnnotation != null,
            isAccessUnfinal = accessAnnotation?.getArgumentValue(Access::unfinal) == true,
            accessFieldOps = accessAnnotation?.getArgumentValue(Access::field).orEmpty(),
            accessStrategy = accessAnnotation?.getArgumentValue(Access::strategy),
            genericArgument = annotationTypeArgument?.let { parseDescriptorGenericArgument(it) },
        )
    }

    private fun parseDescriptorGenericArgument(type: KSType): ParsedDescriptorGenericArgument =
        type.toFunctionTypeOrNull()?.let { functionType ->
            ParsedDescriptorGenericArgumentFunctionType(
                receiverType = functionType.getReceiverTypeOrNull(),
                parameters = functionType.getParameters().map { parameter ->
                    ParsedFunctionTypeParameter(
                        type = parameter.argument.type?.resolve(),
                        name = parameter.name,
                    )
                },
                returnType = functionType.getReturnType().takeIf { !it.isUnit(baseTypes) }
            )
        } ?: ParsedDescriptorGenericArgumentSimpleType(
            type = type,
            typeArguments = type.arguments.map { it.type?.resolve() },
        )

    private fun parsePatch(classDeclaration: KSClassDeclaration): ParsedPatch = with(classDeclaration) {
        val patchAnnotation = findAnnotation<Patch>()
        ParsedPatch(
            name = name,
            side = patchAnnotation?.getArgumentValue(Patch::side) ?: Side.Common,
            isClass = isClass,
            isObject = isObject,
            isOpen = isExplicitlyOpen,
            isAbstract = isExplicitlyAbstract,
            isSealed = isSealed,
            isTopLevel = parentDeclaration == null,
            hasPackageName = packageName.asString().isNotEmpty(),
            isPublic = isPublic(),
            initStrategy = patchAnnotation?.getArgumentValue(Patch::initStrategy),
            classDeclaration = classDeclaration,

            targetClassDeclaration = patchAnnotation?.getArgumentValue(Patch::target)?.toClassDeclaration(),

            companionObjects = companionObjectClassDeclarations.map(::parsePatchCompanionObject).toList(),
            constructors = constructorDeclarations.map(::parsePatchConstructor).toList(),
            bodyProperties = bodyPropertyDeclarations.map(::parsePatchBodyProperty).toList(),
            functions = functionDeclarations.map(::parsePatchFunction).toList(),

            annotations = annotations.map(::parseAnnotation).toList(),
        )
    }

    private fun parsePatchConstructor(constructorDeclaration: KSFunctionDeclaration): ParsedPatchConstructor =
        ParsedPatchConstructor(
            symbol = constructorDeclaration,

            isPublic = constructorDeclaration.isPublic(),
            parameters = constructorDeclaration.parameters.map(::parsePatchConstructorParameter),
        )

    private fun parsePatchConstructorParameter(parameter: KSValueParameter): ParsedPatchConstructorParameter =
        ParsedPatchConstructorParameter(
            symbol = parameter,

            type = parameter.type.resolve(),
            hasOriginAnnotation = parameter.hasAnnotation<Origin>()
        )

    private fun parsePatchCompanionObject(classDeclaration: KSClassDeclaration): ParsedPatchCompanionObject =
        ParsedPatchCompanionObject(
            symbol = classDeclaration,
            isPublic = classDeclaration.isPublic(),
            functions = classDeclaration.functionDeclarations.map(::parsePatchFunction).toList(),
        )

    @OptIn(KspExperimental::class)
    private fun parsePatchBodyProperty(
        propertyDeclaration: KSPropertyDeclaration
    ): ParsedPatchProperty = with(propertyDeclaration) {
        val shadowAnnotation = findAnnotation<KShadow>()
        val mappingNameAnnotation = findAnnotation<MappingName>()
        val getter = getter?.let {
            ParsedPatchPropertyGetter(
                jvmName = resolver.getJvmName(it),
                annotations = it.annotations.map(::parseAnnotation).toList(),
            )
        }
        val setter = takeIf { it.isMutable }?.setter?.takeIf { it.isPublic }?.let {
            ParsedPatchPropertySetter(
                jvmName = resolver.getJvmName(it),
            )
        }
        ParsedPatchProperty(
            symbol = propertyDeclaration,

            name = name,
            type = type.resolve(),

            isPublic = isPublic(),
            isOpen = isExplicitlyOpen,
            isAbstract = isExplicitlyAbstract,
            hasExtensionReceiver = hasExtensionReceiver,

            hasExtensionAnnotation = hasAnnotation<Extension>(),
            hasShadowAnnotation = shadowAnnotation != null,
            explicitMappingName = mappingNameAnnotation?.getArgumentValue(MappingName::name, explicit = true),
            shadowModifiers = shadowAnnotation?.getArgumentValue(KShadow::modifiers).orEmpty(),

            getter = getter,
            setter = setter,
        )
    }

    @OptIn(KspExperimental::class)
    private fun parsePatchFunction(
        functionDeclaration: KSFunctionDeclaration
    ): ParsedPatchFunction = with(functionDeclaration) {
        val shadowAnnotation = findAnnotation<KShadow>()
        val hookAnnotation = findAnnotation<Hook<*>>()
        val mappingNameAnnotation = findAnnotation<MappingName>()

        val atConstructorHeadAnnotation = findAnnotation<AtConstructorHead>()

        val atLocalAnnotation = findAnnotation<AtLocal<*>>()
        val (explicitAtLocalName, explicitAtLocalOrdinal) = atLocalAnnotation?.getArgumentValue(AtLocal<*>::local).let {
            it?.getArgumentValue(KLocal::name, explicit = true) to
                it?.getArgumentValue(KLocal::ordinal, explicit = true)
        }

        val atInstanceofAnnotation = findAnnotation<AtInstanceof<*>>()
        val atReturnAnnotation = findAnnotation<AtReturn>()

        val atLiteralAnnotation = findAnnotation<AtLiteral>()
        val explicitAtLiteralZeroAnnotation = atLiteralAnnotation?.getArgumentValue(AtLiteral::zero, explicit = true)
        val explicitAtLiteralType = atLiteralAnnotation?.getArgumentValue(AtLiteral::type, explicit = true)
        val isExplicitAtLiteralNull = atLiteralAnnotation?.getArgumentValue(AtLiteral::isNull, explicit = true) == true

        val atFieldAnnotation = findAnnotation<AtField<*>>()
        val atArrayAnnotation = findAnnotation<AtArray<*>>()
        val atCallAnnotation = findAnnotation<AtCall<*>>()
        ParsedPatchFunction(
            symbol = functionDeclaration,

            name = name,
            jvmName = resolver.getJvmName(functionDeclaration),
            parameters = parameters.map(::parsePatchFunctionParameter),
            returnType = getReturnTypeOrNull(),
            hasTypeParameters = typeParameters.isNotEmpty(),

            isPublic = isPublic(),
            isOpen = isExplicitlyOpen,
            isAbstract = isAbstract,
            extensionReceiverClassDeclaration = extensionReceiver?.resolve()?.toClassDeclaration(),

            hasExtensionAnnotation = hasAnnotation<Extension>(),
            hasShadowAnnotation = shadowAnnotation != null,
            explicitMappingName = mappingNameAnnotation?.getArgumentValue(MappingName::name, explicit = true),
            shadowModifiers = shadowAnnotation?.getArgumentValue(KShadow::modifiers).orEmpty(),

            hasHookAnnotation = hookAnnotation != null,
            hookDescClassDeclaration = hookAnnotation?.findTypeArgument("D")?.toClassDeclaration(),
            hookAt = findAnnotation<Hook<*>>()?.getArgumentValue(Hook<*>::at),

            hasAtConstructorHeadAnnotation = atConstructorHeadAnnotation != null,
            atConstructorHeadPhase = atConstructorHeadAnnotation?.getArgumentValue(AtConstructorHead::phase),

            hasAtLocalAnnotation = atLocalAnnotation != null,
            atLocalOp = atLocalAnnotation?.getArgumentValue(AtLocal<*>::op),
            atLocalType = findAnnotation<AtLocal<*>>()?.findTypeArgument("T"),
            explicitAtLocalName = explicitAtLocalName,
            explicitAtLocalOrdinal = explicitAtLocalOrdinal,
            atLocalOpOrdinals = atLocalAnnotation?.getArgumentValue(AtLocal<*>::ordinal).orEmpty(),

            hasAtInstanceofAnnotation = atInstanceofAnnotation != null,
            atInstanceofTypeClassDeclaration = findAnnotation<AtInstanceof<*>>()?.findTypeArgument("T")
                ?.toClassDeclaration(),
            atInstanceofOrdinals = atInstanceofAnnotation?.getArgumentValue(AtInstanceof<*>::ordinal).orEmpty(),

            hasAtReturnAnnotation = atReturnAnnotation != null,
            atReturnOrdinals = atReturnAnnotation?.getArgumentValue(AtReturn::ordinal).orEmpty(),

            hasAtLiteralAnnotation = atLiteralAnnotation != null,
            explicitAtLiteralZero = explicitAtLiteralZeroAnnotation,
            atLiteralZeroConditions = explicitAtLiteralZeroAnnotation?.getArgumentValue(Zero::conditions).orEmpty(),
            explicitAtLiteralInt = atLiteralAnnotation?.getArgumentValue(AtLiteral::int, explicit = true),
            explicitAtLiteralLong = atLiteralAnnotation?.getArgumentValue(AtLiteral::long, explicit = true),
            explicitAtLiteralFloat = atLiteralAnnotation?.getArgumentValue(AtLiteral::float, explicit = true),
            explicitAtLiteralDouble = atLiteralAnnotation?.getArgumentValue(AtLiteral::double, explicit = true),
            explicitAtLiteralString = atLiteralAnnotation?.getArgumentValue(AtLiteral::string, explicit = true),
            explicitAtLiteralType = explicitAtLiteralType,
            explicitAtLiteralTypeClassDeclaration = explicitAtLiteralType?.toClassDeclaration(),
            isExplicitAtLiteralNull = isExplicitAtLiteralNull,
            atLiteralOrdinals = atLiteralAnnotation?.getArgumentValue(AtLiteral::ordinal).orEmpty(),

            hasAtFieldAnnotation = atFieldAnnotation != null,
            atFieldOp = atFieldAnnotation?.getArgumentValue(AtField<*>::op),
            atFieldDescClassDeclaration = findAnnotation<AtField<*>>()?.findTypeArgument("D")
                ?.toClassDeclaration(),
            atFieldOrdinals = atFieldAnnotation?.getArgumentValue(AtField<*>::ordinal).orEmpty(),

            hasAtArrayAnnotation = atArrayAnnotation != null,
            atArrayOp = atArrayAnnotation?.getArgumentValue(AtArray<*>::op),
            atArrayDescClassDeclaration = findAnnotation<AtArray<*>>()?.findTypeArgument("D")
                ?.toClassDeclaration(),
            atArrayOrdinals = atArrayAnnotation?.getArgumentValue(AtArray<*>::ordinal).orEmpty(),

            hasAtCallAnnotation = atCallAnnotation != null,
            atCallDescClassDeclaration = findAnnotation<AtCall<*>>()?.findTypeArgument("D")?.toClassDeclaration(),
            atCallOrdinals = atCallAnnotation?.getArgumentValue(AtCall<*>::ordinal).orEmpty(),

            annotations = annotations.map(::parseAnnotation).toList(),
        )
    }

    private fun parsePatchFunctionParameter(
        parameter: KSValueParameter
    ): ParsedPatchFunctionParameter = with(parameter) {
        val type = type.resolve()
        val originAnnotation = findAnnotation<Origin>()
        val cancelAnnotation = findAnnotation<Cancel>()
        val paramAnnotation = findAnnotation<Param>()
        val localAnnotation = findAnnotation<KLocal>()
        val shareAnnotation = findAnnotation<KShare>()
        return ParsedPatchFunctionParameter(
            symbol = parameter,

            name = name?.asString(),
            type = type,
            typeArguments = type.typeArguments,
            hasDefaultArgument = hasDefault,

            hasOriginAnnotation = originAnnotation != null,
            hasCancelAnnotation = cancelAnnotation != null,
            hasOrdinalAnnotation = hasAnnotation<Ordinal>(),

            hasParamAnnotation = paramAnnotation != null,
            explicitParamName = paramAnnotation?.getArgumentValue(Param::name, explicit = true),

            hasLocalAnnotation = localAnnotation != null,
            explicitLocalName = localAnnotation?.getArgumentValue(KLocal::name, explicit = true),
            explicitLocalOrdinal = localAnnotation?.getArgumentValue(KLocal::ordinal, explicit = true),

            hasShareAnnotation = shareAnnotation != null,
            explicitShareKey = shareAnnotation?.getArgumentValue(KShare::key, explicit = true),
            isShareExported = shareAnnotation?.getArgumentValue(KShare::exported) == true,

            annotations = annotations.map(::parseAnnotation).toList(),
        )
    }

    private typealias KRetention = Retention
    private typealias JRetention = java.lang.annotation.Retention

    private fun parseAnnotation(annotation: KSAnnotation): ParsedAnnotation = with(annotation) {
        val typeClassDeclaration = annotationType.resolve().toClassDeclaration()
        val isSourceRetention = typeClassDeclaration.let {
            when (origin) {
                KSPOrigin.KOTLIN, KSPOrigin.KOTLIN_LIB -> {
                    it?.findAnnotation<KRetention>()?.getArgumentValue(KRetention::value) == AnnotationRetention.SOURCE
                }

                KSPOrigin.JAVA, KSPOrigin.JAVA_LIB -> {
                    it?.findAnnotation<JRetention>()?.getArgumentValue(JRetention::value) == RetentionPolicy.SOURCE
                }

                KSPOrigin.SYNTHETIC -> false
            }
        }
        return ParsedAnnotation(
            typeClassDeclaration = typeClassDeclaration,
            isSourceRetention = isSourceRetention,
            arguments = arguments.mapNotNull(::parseAnnotationArgument),
        )
    }

    private fun parseAnnotationArgument(argument: KSValueArgument): ParsedAnnotationArgument? = with(argument) {
        val name = name?.asString() ?: return null

        fun parseValue(value: Any): ParsedAnnotationArgumentValue = when (value) {
            is Boolean -> ParsedAnnotationBooleanArgumentValue(value)
            is Byte -> ParsedAnnotationByteArgumentValue(value)
            is Short -> ParsedAnnotationShortArgumentValue(value)
            is Int -> ParsedAnnotationIntArgumentValue(value)
            is Long -> ParsedAnnotationLongArgumentValue(value)
            is Char -> ParsedAnnotationCharArgumentValue(value)
            is Float -> ParsedAnnotationFloatArgumentValue(value)
            is Double -> ParsedAnnotationDoubleArgumentValue(value)
            is String -> ParsedAnnotationStringArgumentValue(value)
            is KSType -> ParsedAnnotationClassTypeArgumentValue(value)
            is KSClassDeclaration -> ParsedAnnotationEnumArgumentValue(value)
            is KSAnnotation -> ParsedAnnotationEmbeddedAnnotationArgumentValue(parseAnnotation(value))
            else -> lapisError("Unknown annotation argument value type: $value")
        }
        return value?.castOrNull<Iterable<Any>>()?.let { array ->
            ParsedAnnotationArrayArgument(name, isExplicit, array.map { parseValue(it) })
        } ?: value?.let { ParsedAnnotationSingleArgument(name, isExplicit, parseValue(it)) }
    }

    private inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
        property: KProperty1<A, KClass<*>>,
        explicit: Boolean = false,
    ): KSType? =
        getArgumentValue(property, baseTypes, explicit)

    private fun KSFunctionDeclaration.getReturnTypeOrNull(): KSType? =
        returnType?.resolve()?.takeIf { !it.isUnit(baseTypes) }
}
