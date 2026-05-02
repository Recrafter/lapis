package io.github.recrafter.lapis.phases.parser

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.impl.symbol.kotlin.KSClassDeclarationImpl
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import io.github.recrafter.lapis.LapisLogger
import io.github.recrafter.lapis.annotations.*
import io.github.recrafter.lapis.annotations.Origin
import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.ks.*
import io.github.recrafter.lapis.extensions.ksp.getSymbolsAnnotatedWith
import io.github.recrafter.lapis.phases.common.JvmClassName
import ksp.org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import ksp.org.jetbrains.kotlin.psi.KtClassOrObject
import ksp.org.jetbrains.kotlin.psi.KtFunctionType
import ksp.org.jetbrains.kotlin.psi.KtUserType
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class SymbolParser(
    private val resolver: Resolver,
    private val types: KSTypes,
    @Suppress("unused") private val logger: LapisLogger,
) {
    fun prepare(): ParserPrepareResult =
        ParserPrepareResult(
            resolver
                .getSymbolsAnnotatedWith<Schema>()
                .filterIsInstance<KSClassDeclaration>()
                .filterNot { it.parentDeclaration != null }
                .toList(),
            resolver
                .getSymbolsAnnotatedWith<Patch>()
                .filterIsInstance<KSClassDeclaration>()
                .toList(),
        )

    fun parse(): ParserResult =
        prepare().run {
            ParserResult(
                schemas = schemaClassDeclarations.map(::parseSchema),
                patches = patchClassDeclarations.map(::parsePatch),
            )
        }

    private fun parseSchema(symbol: KSClassDeclaration, parentJvmClassName: JvmClassName? = null): ParsedSchema {
        val schemaAnnotation = symbol.findAnnotation<Schema>()
        val innerSchemaAnnotation = symbol.findAnnotation<InnerSchema>()
        val localSchemaAnnotation = symbol.findAnnotation<LocalSchema>()
        val anonymousSchemaAnnotation = symbol.findAnnotation<AnonymousSchema>()
        val isResolvable = symbol.parentDeclarations(includeSelf = true).none {
            it.hasAnnotation<LocalSchema>() || it.hasAnnotation<AnonymousSchema>()
        }
        val (currentJvmClassName, originClassDeclaration) = when {
            parentJvmClassName == null -> {
                val qualifiedName = schemaAnnotation?.getArgumentValue(Schema::qualifiedName)
                val rootJvmClassName = qualifiedName?.let { JvmClassName.of(it) }
                rootJvmClassName to qualifiedName?.let(resolver::getClassDeclarationByName)
            }

            innerSchemaAnnotation != null -> {
                val innerJvmClassName = innerSchemaAnnotation.getArgumentValue(InnerSchema::name)
                    ?.let(parentJvmClassName::inner)
                val classDeclaration = if (isResolvable) {
                    innerJvmClassName?.qualifiedName?.let(resolver::getClassDeclarationByName)
                } else {
                    innerSchemaAnnotation.getArgumentValue(InnerSchema::delegate)?.toClassDeclaration()
                }
                innerJvmClassName to classDeclaration
            }

            localSchemaAnnotation != null -> {
                val index = localSchemaAnnotation.getArgumentValue(LocalSchema::index)
                val name = localSchemaAnnotation.getArgumentValue(LocalSchema::name)
                val localJvmClassName = if (index != null && name != null) {
                    parentJvmClassName.local(index, name)
                } else null
                localJvmClassName to localSchemaAnnotation.getArgumentValue(LocalSchema::delegate)?.toClassDeclaration()
            }

            anonymousSchemaAnnotation != null -> {
                val index = anonymousSchemaAnnotation.getArgumentValue(AnonymousSchema::index)
                val anonymousJvmClassName = index?.let { parentJvmClassName.anonymous(it) }
                anonymousJvmClassName to anonymousSchemaAnnotation.getArgumentValue(AnonymousSchema::delegate)
                    ?.toClassDeclaration()
            }

            else -> null to null
        }
        val accessAnnotation = symbol.findAnnotation<Access>()
        val (nestedSchemas, descriptors) = symbol.classDeclarations.partition { it.getSuperTypeOrNull() == null }
        return ParsedSchema(
            symbol = symbol,
            classDeclaration = symbol,
            originClassDeclaration = originClassDeclaration,
            originJvmClassName = currentJvmClassName,
            hasSchemaAnnotation = schemaAnnotation != null,
            hasInnerSchemaAnnotation = innerSchemaAnnotation != null,
            hasLocalSchemaAnnotation = localSchemaAnnotation != null,
            hasAnonymousSchemaAnnotation = anonymousSchemaAnnotation != null,
            hasAccessAnnotation = accessAnnotation != null,
            isAccessible = isResolvable,
            isAccessUnfinal = accessAnnotation?.getArgumentValue(Access::unfinal) == true,
            accessor = accessAnnotation?.getArgumentValue(Access::accessor),
            descriptors = descriptors.map(::parseDescriptor),
            nestedSchemas = nestedSchemas.map { parseSchema(it, currentJvmClassName) },
        )
    }

    private fun parseDescriptor(classDeclaration: KSClassDeclaration): ParsedDescriptor {
        val accessAnnotation = classDeclaration.findAnnotation<Access>()
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
        return ParsedDescriptor(
            symbol = classDeclaration,

            name = classDeclaration.name,
            classDeclaration = classDeclaration,
            hasStaticAnnotation = classDeclaration.hasAnnotation<Static>(),
            hasAccessAnnotation = accessAnnotation != null,
            hasMappingNameAnnotation = mappingNameAnnotation != null,
            mappingName = mappingNameAnnotation?.getArgumentValue(MappingName::name),
            isAccessUnfinal = accessAnnotation?.getArgumentValue(Access::unfinal) == true,
            accessor = accessAnnotation?.getArgumentValue(Access::accessor),

            genericType = parseDescriptorGenericType(superClassType?.genericTypes?.firstOrNull(), ktFunctionType),
            superClassDeclaration = superClassType?.toClassDeclaration(),
        )
    }

    private fun parseDescriptorGenericType(
        type: KSType?,
        ktFunctionType: KtFunctionType?,
    ): ParsedDescriptorGenericType =
        if (type?.isFunctionType == true && ktFunctionType != null) {
            val genericTypes = type.genericTypes
            val receiverType = if (ktFunctionType.receiver != null) genericTypes.firstOrNull() else null
            ParsedFunctionTypeDescriptorGenericType(
                receiverType = receiverType,
                parameters = genericTypes
                    .drop(
                        if (receiverType != null) 1
                        else 0
                    )
                    .dropLast(1)
                    .mapIndexed { index, type ->
                        ParsedParameter(
                            type = type,
                            name = ktFunctionType.parameters.getOrNull(index)?.name,
                        )
                    },
                returnType = genericTypes.lastOrNull()?.takeIf { !it.isUnit(types) }
            )
        } else {
            ParsedTypeDescriptorGenericType(
                type = type,
                arrayComponentType = type?.findArrayComponentType(types)
            )
        }

    private fun parsePatch(symbol: KSAnnotated): ParsedPatch {
        val patchAnnotation = symbol.findAnnotation<Patch>()
        val classDeclaration = symbol.castOrNull<KSClassDeclaration>()
        return ParsedPatch(
            symbol = symbol,

            name = classDeclaration?.name,
            side = patchAnnotation?.getArgumentValue(Patch::side),
            isClass = classDeclaration?.isClass == true,
            isObject = classDeclaration?.isObject == true,
            isOpen = classDeclaration?.isExplicitlyOpen == true,
            isAbstract = classDeclaration?.isExplicitlyAbstract == true,
            isSealed = classDeclaration?.isSealed == true,
            isTopLevel = classDeclaration?.parentDeclaration == null,
            isPublic = classDeclaration?.isPublic() == true,
            initStrategy = patchAnnotation?.getArgumentValue(Patch::initStrategy),
            classDeclaration = classDeclaration,

            schemaClassDeclaration = patchAnnotation?.getArgumentValue(Patch::schema)?.toClassDeclaration(),

            companionObjects = classDeclaration?.companionObjectClassDeclarations
                ?.map(::parsePatchCompanionObject).orEmpty().toList(),
            constructors = classDeclaration?.constructorDeclarations?.map(::parsePatchConstructor).orEmpty().toList(),
            properties = classDeclaration?.bodyPropertyDeclarations?.map(::parsePatchProperty).orEmpty().toList(),
            functions = classDeclaration?.functionDeclarations?.map(::parsePatchFunction).orEmpty().toList(),
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
    private fun parsePatchProperty(propertyDeclaration: KSPropertyDeclaration): ParsedPatchProperty =
        ParsedPatchProperty(
            symbol = propertyDeclaration,

            name = propertyDeclaration.name,
            getterJvmName = propertyDeclaration.getter?.let(resolver::getJvmName),
            setterJvmName = propertyDeclaration.setter?.let(resolver::getJvmName),
            type = propertyDeclaration.type.resolve(),

            isPublic = propertyDeclaration.isPublic(),
            isOpen = propertyDeclaration.isExplicitlyOpen,
            isAbstract = propertyDeclaration.isExplicitlyAbstract,
            isExtension = propertyDeclaration.isExtension,
            isMutable = propertyDeclaration.isMutable && propertyDeclaration.setter?.isPublic == true,

            hasExtensionAnnotation = propertyDeclaration.hasAnnotation<Extension>(),
        )

    @OptIn(KspExperimental::class)
    private fun parsePatchFunction(functionDeclaration: KSFunctionDeclaration): ParsedPatchFunction {
        val hookAnnotation = functionDeclaration.findAnnotation<Hook>()

        val atConstructorHeadAnnotation = functionDeclaration.findAnnotation<AtConstructorHead>()

        val atLocalAnnotation = functionDeclaration.findAnnotation<AtLocal>()
        val (atLocalExplicitName, atLocalExplicitOrdinal) = atLocalAnnotation?.getArgumentValue(AtLocal::local).let {
            it?.getArgumentValue(Local::name, explicit = true) to it?.getArgumentValue(Local::ordinal, explicit = true)
        }

        val atInstanceofAnnotation = functionDeclaration.findAnnotation<AtInstanceof>()
        val atReturnAnnotation = functionDeclaration.findAnnotation<AtReturn>()

        val atLiteralAnnotation = functionDeclaration.findAnnotation<AtLiteral>()
        val atLiteralExplicitZeroAnnotation = atLiteralAnnotation?.getArgumentValue(AtLiteral::zero, explicit = true)
        val atLiteralExplicitClassType = atLiteralAnnotation?.getArgumentValue(AtLiteral::`class`, explicit = true)
        val atLiteralExplicitNullAnnotation = atLiteralAnnotation?.getArgumentValue(AtLiteral::`null`, explicit = true)

        val atFieldAnnotation = functionDeclaration.findAnnotation<AtField>()
        val atArrayAnnotation = functionDeclaration.findAnnotation<AtArray>()
        val atCallAnnotation = functionDeclaration.findAnnotation<AtCall>()
        return ParsedPatchFunction(
            symbol = functionDeclaration,

            name = functionDeclaration.name,
            jvmName = resolver.getJvmName(functionDeclaration) ?: lapisError("Function jvm name cannot be null"),
            parameters = functionDeclaration.parameters.map(::parsePatchFunctionParameter),
            returnType = functionDeclaration.getReturnTypeOrNull(),
            hasTypeParameters = functionDeclaration.typeParameters.isNotEmpty(),

            isPublic = functionDeclaration.isPublic(),
            isOpen = functionDeclaration.isExplicitlyOpen,
            isAbstract = functionDeclaration.isAbstract,
            isExtension = functionDeclaration.isExtension,

            hasExtensionAnnotation = functionDeclaration.hasAnnotation<Extension>(),

            hasHookAnnotation = hookAnnotation != null,
            hookDescriptorClassDeclaration = hookAnnotation?.getArgumentValue(Hook::desc)?.toClassDeclaration(),
            hookAt = functionDeclaration.findAnnotation<Hook>()?.getArgumentValue(Hook::at),

            hasAtConstructorHeadAnnotation = atConstructorHeadAnnotation != null,
            atConstructorHeadPhase = atConstructorHeadAnnotation?.getArgumentValue(AtConstructorHead::phase),

            hasAtLocalAnnotation = atLocalAnnotation != null,
            atLocalOp = atLocalAnnotation?.getArgumentValue(AtLocal::op),
            atLocalType = functionDeclaration.findAnnotation<AtLocal>()?.getArgumentValue(AtLocal::type),
            atLocalExplicitName = atLocalExplicitName,
            atLocalExplicitOrdinal = atLocalExplicitOrdinal,
            atLocalOpOrdinals = atLocalAnnotation?.getArgumentValue(AtLocal::ordinal).orEmpty(),

            hasAtInstanceofAnnotation = atInstanceofAnnotation != null,
            atInstanceofTypeClassDeclaration = functionDeclaration
                .findAnnotation<AtInstanceof>()
                ?.getArgumentValue(AtInstanceof::type)
                ?.toClassDeclaration(),
            atInstanceofOrdinals = atInstanceofAnnotation
                ?.getArgumentValue(AtInstanceof::ordinal).orEmpty(),

            hasAtReturnAnnotation = atReturnAnnotation != null,
            atReturnOrdinals = atReturnAnnotation?.getArgumentValue(AtReturn::ordinal).orEmpty(),

            hasAtLiteralAnnotation = atLiteralAnnotation != null,
            atLiteralExplicitZero = atLiteralExplicitZeroAnnotation,
            atLiteralZeroConditions = atLiteralExplicitZeroAnnotation?.getArgumentValue(Zero::conditions).orEmpty(),
            atLiteralExplicitInt = atLiteralAnnotation?.getArgumentValue(AtLiteral::int, explicit = true),
            atLiteralExplicitFloat = atLiteralAnnotation?.getArgumentValue(AtLiteral::float, explicit = true),
            atLiteralExplicitLong = atLiteralAnnotation?.getArgumentValue(AtLiteral::long, explicit = true),
            atLiteralExplicitDouble = atLiteralAnnotation?.getArgumentValue(AtLiteral::double, explicit = true),
            atLiteralExplicitString = atLiteralAnnotation?.getArgumentValue(AtLiteral::string, explicit = true),
            atLiteralExplicitClassType = atLiteralExplicitClassType,
            atLiteralExplicitClassDeclaration = atLiteralExplicitClassType?.toClassDeclaration(),
            atLiteralExplicitNull = atLiteralExplicitNullAnnotation,
            atLiteralOrdinals = atLiteralAnnotation?.getArgumentValue(AtLiteral::ordinal).orEmpty(),

            hasAtFieldAnnotation = atFieldAnnotation != null,
            atFieldOp = atFieldAnnotation?.getArgumentValue(AtField::op),
            atFieldDescriptorClassDeclaration = functionDeclaration
                .findAnnotation<AtField>()
                ?.getArgumentValue(AtField::desc)
                ?.toClassDeclaration(),
            atFieldOrdinals = atFieldAnnotation?.getArgumentValue(AtField::ordinal).orEmpty(),

            hasAtArrayAnnotation = atArrayAnnotation != null,
            atArrayOp = atArrayAnnotation?.getArgumentValue(AtArray::op),
            atArrayDescriptorClassDeclaration = functionDeclaration
                .findAnnotation<AtArray>()
                ?.getArgumentValue(AtArray::desc)
                ?.toClassDeclaration(),
            atArrayOrdinals = atArrayAnnotation?.getArgumentValue(AtArray::ordinal).orEmpty(),

            hasAtCallAnnotation = atCallAnnotation != null,
            atCallDescriptorClassDeclaration = functionDeclaration
                .findAnnotation<AtCall>()
                ?.getArgumentValue(AtCall::desc)
                ?.toClassDeclaration(),
            atCallOrdinals = atCallAnnotation?.getArgumentValue(AtCall::ordinal).orEmpty(),
        )
    }

    private fun parsePatchFunctionParameter(parameter: KSValueParameter): ParsedPatchFunctionParameter {
        val name = parameter.name?.asString()
        val type = parameter.type.resolve()
        val originAnnotation = parameter.findAnnotation<Origin>()
        val cancelAnnotation = parameter.findAnnotation<Cancel>()
        val paramAnnotation = parameter.findAnnotation<Param>()
        val localAnnotation = parameter.findAnnotation<Local>()
        val shareAnnotation = parameter.findAnnotation<Share>()
        return ParsedPatchFunctionParameter(
            symbol = parameter,

            name = name,
            type = type,
            hasDefaultArgument = parameter.hasDefault,

            hasOriginAnnotation = originAnnotation != null,
            originGenericTypeClassDeclaration = if (originAnnotation != null) {
                type.findGenericType()?.toClassDeclaration()
            } else null,

            hasCancelAnnotation = cancelAnnotation != null,
            cancelGenericTypeClassDeclaration = if (cancelAnnotation != null) {
                type.findGenericType()?.toClassDeclaration()
            } else null,

            hasOrdinalAnnotation = parameter.hasAnnotation<Ordinal>(),

            hasParamAnnotation = paramAnnotation != null,
            explicitParamName = paramAnnotation?.getArgumentValue(Param::name, explicit = true),

            hasLocalAnnotation = localAnnotation != null,
            explicitLocalName = localAnnotation?.getArgumentValue(Local::name, explicit = true),
            explicitLocalOrdinal = localAnnotation?.getArgumentValue(Local::ordinal, explicit = true),

            hasShareAnnotation = shareAnnotation != null,
            explicitShareKey = shareAnnotation?.getArgumentValue(Share::key, explicit = true),
            isShareExported = shareAnnotation?.getArgumentValue(Share::exported) == true,
        )
    }

    private fun KSClassDeclaration.getSuperTypeOrNull(): KSType? =
        superTypes.map { it.resolve() }.find { !it.isAny(types) }

    private fun KSFunctionDeclaration.getReturnTypeOrNull(): KSType? =
        returnType?.resolve()?.takeIf { !it.isUnit(types) }

    private inline fun <reified A : Annotation> KSAnnotation.findArgumentValue(
        property: KProperty1<A, *>,
        explicit: Boolean = false,
    ): KSAnnotationArgumentValue? =
        (if (explicit) explicitArguments else arguments)
            .find { it.name?.asString() == property.name }
            ?.value
            ?.let { KSAnnotationArgumentValue(it, keepDefault = explicit) }

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
        findArgumentValue(property, explicit)?.asKClass(types)

    private inline fun <reified A : Annotation, reified E : Enum<E>> KSAnnotation.getArgumentValue(
        property: KProperty1<A, E>,
        explicit: Boolean = false,
        default: E? = null,
    ): E? =
        findArgumentValue(property, explicit)?.asEnum(default)

    private inline fun <reified A : Annotation, reified Embedded : Annotation> KSAnnotation.getArgumentValue(
        property: KProperty1<A, Embedded>,
        explicit: Boolean = false,
    ): KSAnnotation? =
        findArgumentValue(property, explicit)?.asAnnotation()

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

    private inline fun <reified A : Annotation> KSAnnotation.getArrayArgumentValue(
        property: KProperty1<A, *>,
        explicit: Boolean = false,
    ): Iterable<KSAnnotationArgumentValue>? =
        findArgumentValue(property, explicit)?.asArray()
}
