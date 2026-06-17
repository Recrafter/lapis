package io.github.recrafter.lapis.phases.parser

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.impl.symbol.kotlin.KSClassDeclarationImpl
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import io.github.recrafter.lapis.annotations.*
import io.github.recrafter.lapis.annotations.Origin
import io.github.recrafter.lapis.common.JvmClassName
import io.github.recrafter.lapis.common.KSBaseTypes
import io.github.recrafter.lapis.common.isAny
import io.github.recrafter.lapis.common.isUnit
import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.ks.*
import io.github.recrafter.lapis.extensions.ksp.KSPOrigin
import io.github.recrafter.lapis.extensions.ksp.getSymbolsAnnotatedWith
import io.github.recrafter.lapis.logging.Logger
import io.github.recrafter.lapis.phases.parser.helpers.AnnotationArgumentValue
import io.github.recrafter.lapis.phases.parser.models.ParserPrepareResult
import io.github.recrafter.lapis.phases.parser.models.ParserResult
import io.github.recrafter.lapis.phases.parser.models.common.*
import io.github.recrafter.lapis.phases.parser.models.patches.*
import io.github.recrafter.lapis.phases.parser.models.schemas.*
import ksp.org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import ksp.org.jetbrains.kotlin.psi.KtClassOrObject
import ksp.org.jetbrains.kotlin.psi.KtFunctionType
import ksp.org.jetbrains.kotlin.psi.KtUserType
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
            resolver.getSymbolsAnnotatedWith<Schema>().filterIsInstance<KSClassDeclaration>().toList(),
            resolver.getSymbolsAnnotatedWith<KMixin>().filterIsInstance<KSClassDeclaration>().toList(),
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
        val schemaAnnotation = classDeclaration.findAnnotation<Schema>()
        val innerSchemaAnnotation = classDeclaration.findAnnotation<InnerSchema>()
        val localSchemaAnnotation = classDeclaration.findAnnotation<LocalSchema>()
        val anonymousSchemaAnnotation = classDeclaration.findAnnotation<AnonymousSchema>()
        val isAccessible = classDeclaration.parentDeclarations(includeSelf = true).none {
            it.hasAnnotation<LocalSchema>() || it.hasAnnotation<AnonymousSchema>()
        }
        val (currentJvmClassName, originClassDeclaration, side) = when {
            parentJvmClassName == null -> {
                val qualifiedName = schemaAnnotation?.getArgumentValue(Schema::qualifiedName)
                val rootJvmClassName = qualifiedName?.let { JvmClassName.of(it) }
                Triple(
                    rootJvmClassName,
                    qualifiedName?.let(resolver::getClassDeclarationByName),
                    schemaAnnotation?.getArgumentValue(Schema::side),
                )
            }

            innerSchemaAnnotation != null -> {
                val innerJvmClassName = innerSchemaAnnotation.getArgumentValue(InnerSchema::name)
                    ?.let(parentJvmClassName::inner)
                val classDeclaration = if (isAccessible) {
                    innerJvmClassName?.qualifiedName?.let(resolver::getClassDeclarationByName)
                } else {
                    innerSchemaAnnotation.getArgumentValue(InnerSchema::delegate)?.toClassDeclaration()
                }
                Triple(
                    innerJvmClassName,
                    classDeclaration,
                    innerSchemaAnnotation.getArgumentValue(InnerSchema::side),
                )
            }

            localSchemaAnnotation != null -> {
                val index = localSchemaAnnotation.getArgumentValue(LocalSchema::index)
                val name = localSchemaAnnotation.getArgumentValue(LocalSchema::name)
                val localJvmClassName = if (index != null && name != null) {
                    parentJvmClassName.local(index, name)
                } else null
                Triple(
                    localJvmClassName,
                    localSchemaAnnotation.getArgumentValue(LocalSchema::delegate)?.toClassDeclaration(),
                    localSchemaAnnotation.getArgumentValue(LocalSchema::side),
                )
            }

            anonymousSchemaAnnotation != null -> {
                val index = anonymousSchemaAnnotation.getArgumentValue(AnonymousSchema::index)
                val anonymousJvmClassName = index?.let { parentJvmClassName.anonymous(it) }
                Triple(
                    anonymousJvmClassName,
                    anonymousSchemaAnnotation.getArgumentValue(AnonymousSchema::delegate)?.toClassDeclaration(),
                    anonymousSchemaAnnotation.getArgumentValue(AnonymousSchema::side),
                )
            }

            else -> Triple(null, null, null)
        }
        val accessAnnotation = classDeclaration.findAnnotation<Access>()
        val (schemaClassDeclarations, descriptorClassDeclarations) = classDeclaration.innerClassDeclarations.partition {
            it.getSuperTypeOrNull() == null
        }
        return ParsedSchema(
            classDeclaration = classDeclaration,
            side = side ?: Side.Common,
            isTopLevel = classDeclaration.parentDeclaration == null,
            hasPackageName = classDeclaration.packageName.asString().isNotEmpty(),
            originClassDeclaration = originClassDeclaration,
            originJvmClassName = currentJvmClassName,
            hasSchemaAnnotation = schemaAnnotation != null,
            hasInnerSchemaAnnotation = innerSchemaAnnotation != null,
            hasLocalSchemaAnnotation = localSchemaAnnotation != null,
            hasAnonymousSchemaAnnotation = anonymousSchemaAnnotation != null,
            isAccessible = isAccessible,
            hasAccessAnnotation = accessAnnotation != null,
            isAccessUnfinal = accessAnnotation?.getArgumentValue(Access::unfinal) == true,
            accessStrategy = accessAnnotation?.getArgumentValue(Access::strategy),
            descriptors = descriptorClassDeclarations.map(::parseDescriptor),
            nestedSchemas = schemaClassDeclarations.map { parseSchema(it, currentJvmClassName) },
        )
    }

    private fun parseDescriptor(classDeclaration: KSClassDeclaration): ParsedDescriptor {
        val mappingNameAnnotation = classDeclaration.findAnnotation<MappingName>()
        val superClassType = classDeclaration.getSuperTypeOrNull()
        val ktFunctionType = classDeclaration
            .castOrNull<KSClassDeclarationImpl>()
            ?.ktDeclarationSymbol
            ?.castOrNull<KaClassSymbol>()
            ?.psi
            ?.castOrNull<KtClassOrObject>()
            ?.superTypeListEntries
            ?.firstOrNull()
            ?.typeReference
            ?.typeElement
            ?.castOrNull<KtUserType>()
            ?.typeArguments
            ?.firstOrNull()
            ?.typeReference
            ?.typeElement
            ?.castOrNull<KtFunctionType>()
        val accessAnnotation = classDeclaration.findAnnotation<Access>()
        return ParsedDescriptor(
            name = classDeclaration.name,
            classDeclaration = classDeclaration,
            isObject = classDeclaration.isObject,
            hasStaticAnnotation = classDeclaration.hasAnnotation<Static>(),
            hasMappingNameAnnotation = mappingNameAnnotation != null,
            explicitMappingName = mappingNameAnnotation?.getArgumentValue(MappingName::name, explicit = true),
            hasAccessAnnotation = accessAnnotation != null,
            isAccessUnfinal = accessAnnotation?.getArgumentValue(Access::unfinal) == true,
            accessFieldOps = accessAnnotation?.getArgumentValue(Access::field).orEmpty(),
            accessStrategy = accessAnnotation?.getArgumentValue(Access::strategy),

            genericArgument = parseDescriptorGenericArgument(
                superClassType?.typeArguments?.firstOrNull(),
                ktFunctionType
            ),
            superClassDeclaration = superClassType?.toClassDeclaration(),
        )
    }

    private fun parseDescriptorGenericArgument(
        type: KSType?,
        ktFunctionType: KtFunctionType?,
    ): ParsedDescriptorGenericArgument =
        if (type?.isFunctionType == true && ktFunctionType != null) {
            val typeArguments = type.typeArguments
            val receiverType = if (ktFunctionType.receiver != null) typeArguments.firstOrNull() else null
            ParsedDescriptorGenericArgumentFunctionType(
                receiverType = receiverType,
                parameters = typeArguments
                    .drop(
                        if (receiverType != null) 1
                        else 0
                    )
                    .dropLast(1)
                    .mapIndexed { index, type ->
                        ParsedFunctionTypeParameter(
                            type = type,
                            name = ktFunctionType.parameters.getOrNull(index)?.name,
                        )
                    },
                returnType = typeArguments.lastOrNull()?.takeIf { !it.isUnit(baseTypes) }
            )
        } else {
            ParsedDescriptorGenericArgumentSimpleType(
                type = type,
                typeArguments = type?.arguments?.map { it.type?.resolve() }.orEmpty()
            )
        }

    private fun parsePatch(classDeclaration: KSClassDeclaration): ParsedPatch = with(classDeclaration) {
        val patchAnnotation = findAnnotation<KMixin>()
        ParsedPatch(
            name = name,
            side = patchAnnotation?.getArgumentValue(KMixin::side) ?: Side.Common,
            isClass = isClass,
            isObject = isObject,
            isOpen = isExplicitlyOpen,
            isAbstract = isExplicitlyAbstract,
            isSealed = isSealed,
            isTopLevel = parentDeclaration == null,
            hasPackageName = packageName.asString().isNotEmpty(),
            isPublic = isPublic(),
            initStrategy = patchAnnotation?.getArgumentValue(KMixin::initStrategy),
            classDeclaration = classDeclaration,

            targetClassDeclaration = patchAnnotation?.getArgumentValue(KMixin::target)?.toClassDeclaration(),

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
        val hookAnnotation = findAnnotation<Hook>()
        val mappingNameAnnotation = findAnnotation<MappingName>()

        val atConstructorHeadAnnotation = findAnnotation<AtConstructorHead>()

        val atLocalAnnotation = findAnnotation<AtLocal>()
        val (explicitAtLocalName, explicitAtLocalOrdinal) = atLocalAnnotation?.getArgumentValue(AtLocal::local).let {
            it?.getArgumentValue(KLocal::name, explicit = true) to
                it?.getArgumentValue(KLocal::ordinal, explicit = true)
        }

        val atInstanceofAnnotation = findAnnotation<AtInstanceof>()
        val atReturnAnnotation = findAnnotation<AtReturn>()

        val atLiteralAnnotation = findAnnotation<AtLiteral>()
        val explicitAtLiteralZeroAnnotation = atLiteralAnnotation?.getArgumentValue(AtLiteral::zero, explicit = true)
        val explicitAtLiteralClassType = atLiteralAnnotation?.getArgumentValue(AtLiteral::`class`, explicit = true)
        val explicitAtLiteralNullAnnotation = atLiteralAnnotation?.getArgumentValue(AtLiteral::`null`, explicit = true)

        val atFieldAnnotation = findAnnotation<AtField>()
        val atArrayAnnotation = findAnnotation<AtArray>()
        val atCallAnnotation = findAnnotation<AtCall>()
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
            hasExtensionReceiver = hasExtensionReceiver,

            hasExtensionAnnotation = hasAnnotation<Extension>(),
            hasShadowAnnotation = shadowAnnotation != null,
            explicitMappingName = mappingNameAnnotation?.getArgumentValue(MappingName::name, explicit = true),
            shadowModifiers = shadowAnnotation?.getArgumentValue(KShadow::modifiers).orEmpty(),

            hasHookAnnotation = hookAnnotation != null,
            hookDescClassDeclaration = hookAnnotation?.getArgumentValue(Hook::desc)?.toClassDeclaration(),
            hookAt = findAnnotation<Hook>()?.getArgumentValue(Hook::at),

            hasAtConstructorHeadAnnotation = atConstructorHeadAnnotation != null,
            atConstructorHeadPhase = atConstructorHeadAnnotation?.getArgumentValue(AtConstructorHead::phase),

            hasAtLocalAnnotation = atLocalAnnotation != null,
            atLocalOp = atLocalAnnotation?.getArgumentValue(AtLocal::op),
            atLocalType = findAnnotation<AtLocal>()?.getArgumentValue(AtLocal::type),
            explicitAtLocalName = explicitAtLocalName,
            explicitAtLocalOrdinal = explicitAtLocalOrdinal,
            atLocalOpOrdinals = atLocalAnnotation?.getArgumentValue(AtLocal::ordinal).orEmpty(),

            hasAtInstanceofAnnotation = atInstanceofAnnotation != null,
            atInstanceofTypeClassDeclaration = findAnnotation<AtInstanceof>()?.getArgumentValue(AtInstanceof::type)
                ?.toClassDeclaration(),
            atInstanceofOrdinals = atInstanceofAnnotation?.getArgumentValue(AtInstanceof::ordinal).orEmpty(),

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
            explicitAtLiteralClassType = explicitAtLiteralClassType,
            explicitAtLiteralClassDeclaration = explicitAtLiteralClassType?.toClassDeclaration(),
            explicitAtLiteralNull = explicitAtLiteralNullAnnotation,
            atLiteralOrdinals = atLiteralAnnotation?.getArgumentValue(AtLiteral::ordinal).orEmpty(),

            hasAtFieldAnnotation = atFieldAnnotation != null,
            atFieldOp = atFieldAnnotation?.getArgumentValue(AtField::op),
            atFieldDescClassDeclaration = findAnnotation<AtField>()?.getArgumentValue(AtField::desc)
                ?.toClassDeclaration(),
            atFieldOrdinals = atFieldAnnotation?.getArgumentValue(AtField::ordinal).orEmpty(),

            hasAtArrayAnnotation = atArrayAnnotation != null,
            atArrayOp = atArrayAnnotation?.getArgumentValue(AtArray::op),
            atArrayDescClassDeclaration = findAnnotation<AtArray>()?.getArgumentValue(AtArray::desc)
                ?.toClassDeclaration(),
            atArrayOrdinals = atArrayAnnotation?.getArgumentValue(AtArray::ordinal).orEmpty(),

            hasAtCallAnnotation = atCallAnnotation != null,
            atCallDescClassDeclaration = findAnnotation<AtCall>()?.getArgumentValue(AtCall::desc)?.toClassDeclaration(),
            atCallOrdinals = atCallAnnotation?.getArgumentValue(AtCall::ordinal).orEmpty(),

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

    private inline fun <reified A : Annotation> KSAnnotation.findArgumentValue(
        property: KProperty1<A, *>,
        explicit: Boolean = false,
    ): AnnotationArgumentValue? =
        (if (explicit) arguments.filter { it.isExplicit } else arguments)
            .find { it.name?.asString() == property.name }
            ?.value
            ?.let { AnnotationArgumentValue(it, keepDefault = explicit) }

    private inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
        property: KProperty1<A, Boolean>,
        explicit: Boolean = false,
    ): Boolean? =
        findArgumentValue(property, explicit)?.asBoolean()

    private inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
        property: KProperty1<A, Int>,
        explicit: Boolean = false,
    ): Int? =
        findArgumentValue(property, explicit)?.asInt()

    private inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
        property: KProperty1<A, Long>,
        explicit: Boolean = false,
    ): Long? =
        findArgumentValue(property, explicit)?.asLong()

    private inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
        property: KProperty1<A, Float>,
        explicit: Boolean = false,
    ): Float? =
        findArgumentValue(property, explicit)?.asFloat()

    private inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
        property: KProperty1<A, Double>,
        explicit: Boolean = false,
    ): Double? =
        findArgumentValue(property, explicit)?.asDouble()

    private inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
        property: KProperty1<A, String>,
        explicit: Boolean = false,
    ): String? =
        findArgumentValue(property, explicit)?.asString()

    private inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
        property: KProperty1<A, KClass<*>>,
        explicit: Boolean = false,
    ): KSType? =
        findArgumentValue(property, explicit)?.asClassType(baseTypes)

    private inline fun <reified A : Annotation, reified E : Enum<E>> KSAnnotation.getArgumentValue(
        property: KProperty1<A, E>,
        explicit: Boolean = false,
    ): E? =
        findArgumentValue(property, explicit)?.asEnum()

    private inline fun <reified A : Annotation, reified EA : Annotation> KSAnnotation.getArgumentValue(
        property: KProperty1<A, EA>,
        explicit: Boolean = false,
    ): KSAnnotation? =
        findArgumentValue(property, explicit)?.asAnnotation()

    private inline fun <reified A : Annotation> KSAnnotation.getArrayArgumentValue(
        property: KProperty1<A, *>,
        explicit: Boolean = false,
    ): Iterable<AnnotationArgumentValue>? =
        findArgumentValue(property, explicit)?.asArray()

    @JvmName("getIntArrayArgumentValue")
    private inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
        property: KProperty1<A, IntArray>,
        explicit: Boolean = false,
    ): List<Int>? =
        getArrayArgumentValue(property, explicit)?.mapNotNull { it.asInt() }

    @JvmName("getEnumArrayArgumentValue")
    private inline fun <reified A : Annotation, reified E : Enum<E>> KSAnnotation.getArgumentValue(
        property: KProperty1<A, Array<out E>>,
        explicit: Boolean = false,
    ): List<E>? =
        getArrayArgumentValue(property, explicit)?.mapNotNull { it.asEnum() }

    private fun KSClassDeclaration.getSuperTypeOrNull(): KSType? =
        superTypes.map { it.resolve() }.find { !it.isAny(baseTypes) }

    private fun KSFunctionDeclaration.getReturnTypeOrNull(): KSType? =
        returnType?.resolve()?.takeIf { !it.isUnit(baseTypes) }
}
