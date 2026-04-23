package io.github.recrafter.lapis.phases.parser

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.impl.symbol.kotlin.KSClassDeclarationImpl
import com.google.devtools.ksp.impl.symbol.kotlin.KSFunctionDeclarationImpl
import com.google.devtools.ksp.impl.symbol.kotlin.KSPropertyDeclarationJavaImpl
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.recrafter.lapis.LapisLogger
import io.github.recrafter.lapis.annotations.*
import io.github.recrafter.lapis.annotations.Origin
import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ks.*
import io.github.recrafter.lapis.extensions.ksp.getSymbolsAnnotatedWith
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import ksp.com.intellij.psi.PsiElement
import ksp.com.intellij.psi.util.PsiTreeUtil
import ksp.org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import ksp.org.jetbrains.kotlin.analysis.api.KaSession
import ksp.org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import ksp.org.jetbrains.kotlin.analysis.api.resolution.KaErrorCallInfo
import ksp.org.jetbrains.kotlin.analysis.api.resolution.KaSuccessCallInfo
import ksp.org.jetbrains.kotlin.analysis.api.session.KaSessionProvider
import ksp.org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import ksp.org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import ksp.org.jetbrains.kotlin.analysis.api.symbols.KaJavaFieldSymbol
import ksp.org.jetbrains.kotlin.analysis.api.types.KaClassType
import ksp.org.jetbrains.kotlin.psi.*
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
                .filter { symbol ->
                    val parent = symbol.parentDeclaration
                    parent == null || !parent.hasAnnotation<Schema>()
                },
            resolver
                .getSymbolsAnnotatedWith<Patch>()
                .filterIsInstance<KSClassDeclaration>(),
        )

    fun parse(): ParserResult =
        prepare().run {
            ParserResult(
                schemas = schemaClassDeclarations.map { parseSchema(it) },
                patches = patchClassDeclarations.map { parsePatch(it) },
            )
        }

    private fun parseSchema(symbol: KSClassDeclaration, parentBinaryName: String? = null): ParsedSchema {
        val (nestedSchemas, descriptors) = symbol.classDeclarations.partition { it.getSuperTypeOrNull() == null }
        val accessAnnotation = symbol.findAnnotation<Access>()
        val accessTarget = accessAnnotation?.getArgumentValue(Access::target)
        val explicitTarget = symbol.findAnnotation<Schema>()?.getArgumentValue(Schema::target)?.toClassDeclaration()
        val (targetBinaryName, targetQualifiedName) = when {
            parentBinaryName != null && accessTarget != null -> {
                val innerName = accessTarget.removePrefix(".")
                parentBinaryName + "$" + innerName to parentBinaryName + "." + innerName
            }

            accessTarget != null -> accessTarget to accessTarget
            explicitTarget?.isValid == true -> {
                explicitTarget.toClassName().asIrClassName().run {
                    binaryName to qualifiedName
                }
            }

            else -> null to null
        }
        return ParsedSchema(
            symbol = symbol,
            classDeclaration = symbol,
            targetClassDeclaration = targetQualifiedName?.let {
                resolver.getClassDeclarationByName(targetQualifiedName)
            },
            targetBinaryName = targetBinaryName,
            hasAccess = accessTarget != null,
            unfinal = accessAnnotation?.getArgumentValue(Access::unfinal) == true,
            descriptors = descriptors.map { parseDescriptor(it) },
            nestedSchemas = nestedSchemas.map { parseSchema(it, targetBinaryName) },
        )
    }

    private fun parseDescriptor(classDeclaration: KSClassDeclaration): ParsedDescriptor {
        val accessAnnotation = classDeclaration.findAnnotation<Access>()
        val superClassType = classDeclaration.getSuperTypeOrNull()
        val ktSuperTypeListEntry = classDeclaration
            .castOrNull<KSClassDeclarationImpl>()
            ?.ktDeclarationSymbol
            ?.castOrNull<KaClassSymbol>()
            ?.psi
            ?.castOrNull<KtClassOrObject>()
            ?.superTypeListEntries
            ?.firstOrNull()
        return ParsedDescriptor(
            symbol = classDeclaration,

            name = classDeclaration.name,
            classDeclaration = classDeclaration,
            hasStaticAnnotation = classDeclaration.hasAnnotation<Static>(),
            hasAccessAnnotation = accessAnnotation != null,
            unfinal = accessAnnotation?.getArgumentValue(Access::unfinal) == true,

            genericType = parseDescriptorGenericType(
                superClassType?.genericTypes?.firstOrNull(),
                ktSuperTypeListEntry
                    ?.typeReference
                    ?.typeElement
                    ?.castOrNull<KtUserType>()
                    ?.typeArguments
                    ?.firstOrNull()
                    ?.typeReference
                    ?.typeElement
                    ?.castOrNull<KtFunctionType>()
            ),
            callableReference = parseDescriptorCallableReference(ktSuperTypeListEntry as? KtSuperTypeCallEntry),
            superClassDeclaration = superClassType?.toClassDeclaration(),
        )
    }

    private fun parseDescriptorGenericType(
        type: KSType?,
        ktFunctionType: KtFunctionType?
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
                returnType = genericTypes.lastOrNull()?.takeNotUnit()
            )
        } else {
            ParsedTypeDescriptorGenericType(
                type = type,
                arrayComponentType = type?.findArrayComponentType()
            )
        }

    private fun parseDescriptorCallableReference(
        ktSuperTypeCallEntry: KtSuperTypeCallEntry?
    ): ParsedDescriptorCallableReference? =
        ktSuperTypeCallEntry
            ?.useAnalysis {
                it.typeReference?.type?.castOrNull<KaClassType>()?.typeArguments?.firstOrNull()?.type
                return@useAnalysis it
            }
            ?.getChildOfType<KtValueArgumentList>()
            ?.arguments
            ?.firstOrNull()
            ?.getArgumentExpression()
            ?.castOrNull<KtCallableReferenceExpression>()
            ?.useAnalysis { callable ->
                val callInfo = callable.resolveToCall()
                if (callInfo is KaErrorCallInfo && callInfo.diagnostic.factoryName == "INVISIBLE_REFERENCE") {
                    return@useAnalysis InvisibleCallableReference(callable.callableReference.text)
                }
                val symbol = callInfo
                    ?.castOrNull<KaSuccessCallInfo>()
                    ?.call
                    ?.castOrNull<KaCallableMemberCall<*, *>>()
                    ?.partiallyAppliedSymbol
                    ?.signature
                    ?.symbol
                    ?: return@useAnalysis null
                val receiverClassDeclaration = symbol
                    .containingSymbol
                    ?.castOrNull<KaClassSymbol>()
                    ?.let { KSClassDeclarationImpl.getCached(it) }
                when (symbol) {
                    is KaFunctionSymbol -> ParsedMethodDescriptorCallableReference(
                        receiverClassDeclaration = receiverClassDeclaration,
                        name = KSFunctionDeclarationImpl.getCached(symbol).name,
                    )

                    is KaJavaFieldSymbol -> ParsedFieldDescriptorCallableReference(
                        receiverClassDeclaration = receiverClassDeclaration,
                        name = KSPropertyDeclarationJavaImpl.getCached(symbol).name,
                    )

                    else -> null
                }
            }

    private fun parsePatch(symbol: KSAnnotated): ParsedPatch {
        val patchAnnotation = symbol.findAnnotation<Patch>()
        val classDeclaration = symbol.castOrNull<KSClassDeclaration>()
        val superClassType = classDeclaration?.getSuperTypeOrNull()
        return ParsedPatch(
            symbol = symbol,

            name = classDeclaration?.name,
            side = patchAnnotation?.getArgumentValue(Patch::side),
            classDeclaration = classDeclaration,

            superClassDeclaration = superClassType?.toClassDeclaration(),
            superGenericClassDeclaration = superClassType?.findGenericType()?.toClassDeclaration(),

            schemaClassDeclaration = patchAnnotation?.getArgumentValue(Patch::schema)?.toClassDeclaration(),

            properties = classDeclaration?.propertyDeclarations?.map { parsePatchProperty(it) }.orEmpty(),
            functions = listOfNotNull(
                classDeclaration,
                classDeclaration?.findCompanionObject(),
            ).flatMap { classDeclaration ->
                classDeclaration.functionDeclarations
                    .filter { !it.isConstructor() }
                    .map { parsePatchFunction(it) }
            },
        )
    }

    private fun parsePatchProperty(propertyDeclaration: KSPropertyDeclaration): ParsedPatchProperty =
        ParsedPatchProperty(
            symbol = propertyDeclaration,

            name = propertyDeclaration.name,
            type = propertyDeclaration.type.resolve(),
            isPublic = propertyDeclaration.isPublic(),
            isAbstract = propertyDeclaration.isAbstract(),
            isExtension = propertyDeclaration.isExtension,
            isMutable = propertyDeclaration.isMutable &&
                propertyDeclaration.setter?.modifiers?.contains(Modifier.PUBLIC) == true,
        )

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
        val atLiteralZeroAnnotation = atLiteralAnnotation?.getArgumentValue(AtLiteral::zero, explicit = true)
        val atLiteralClassType = atLiteralAnnotation?.getArgumentValue(AtLiteral::`class`, explicit = true)
        val atLiteralNullAnnotation = atLiteralAnnotation?.getArgumentValue(AtLiteral::`null`, explicit = true)

        val atFieldAnnotation = functionDeclaration.findAnnotation<AtField>()
        val atArrayAnnotation = functionDeclaration.findAnnotation<AtArray>()
        val atCallAnnotation = functionDeclaration.findAnnotation<AtCall>()
        return ParsedPatchFunction(
            symbol = functionDeclaration,

            name = functionDeclaration.name,
            parameters = functionDeclaration.parameters.map { parsePatchFunctionParameter(it) },
            returnType = functionDeclaration.getReturnTypeOrNull(),
            hasTypeParameters = functionDeclaration.typeParameters.isNotEmpty(),

            isPublic = functionDeclaration.isPublic(),
            isAbstract = functionDeclaration.isAbstract,
            isExtension = functionDeclaration.isExtension,

            fromCompanionObject = functionDeclaration.parentDeclaration.let {
                it is KSClassDeclaration && it.isCompanionObject
            },

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
            atLiteralExplicitZero = atLiteralZeroAnnotation,
            atLiteralZeroConditions = atLiteralZeroAnnotation?.getArgumentValue(Zero::conditions).orEmpty(),
            atLiteralExplicitInt = atLiteralAnnotation?.getArgumentValue(AtLiteral::int, explicit = true),
            atLiteralExplicitFloat = atLiteralAnnotation?.getArgumentValue(AtLiteral::float, explicit = true),
            atLiteralExplicitLong = atLiteralAnnotation?.getArgumentValue(AtLiteral::long, explicit = true),
            atLiteralExplicitDouble = atLiteralAnnotation?.getArgumentValue(AtLiteral::double, explicit = true),
            atLiteralExplicitString = atLiteralAnnotation?.getArgumentValue(AtLiteral::string, explicit = true),
            atLiteralExplicitClassType = atLiteralClassType,
            atLiteralExplicitClassDeclaration = atLiteralClassType?.toClassDeclaration(),
            atLiteralExplicitNull = atLiteralNullAnnotation,
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
            originGenericClassDeclaration = if (originAnnotation != null) {
                type.findGenericType()?.toClassDeclaration()
            } else null,

            hasCancelAnnotation = cancelAnnotation != null,
            cancelGenericClassDeclaration = if (cancelAnnotation != null) {
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

    fun KSType.takeNotUnit(): KSType? =
        takeIf { it != types.unit }

    fun KSClassDeclaration.getSuperTypeOrNull(): KSType? =
        superTypes.map { it.resolve() }.find { it != types.any }

    fun KSFunctionDeclaration.getReturnTypeOrNull(): KSType? =
        returnType?.resolve()?.takeNotUnit()

    fun KSType.findArrayComponentType(): KSType? =
        when (declaration.qualifiedName?.asString()) {
            KPArray.qualifiedName -> arguments.firstOrNull()?.type?.resolve()
            KPBooleanArray.qualifiedName -> types.boolean
            KPByteArray.qualifiedName -> types.byte
            KPShortArray.qualifiedName -> types.short
            KPIntArray.qualifiedName -> types.int
            KPLongArray.qualifiedName -> types.long
            KPCharArray.qualifiedName -> types.char
            KPFloatArray.qualifiedName -> types.float
            KPDoubleArray.qualifiedName -> types.double
            else -> null
        }

    private inline fun <reified A : Annotation> KSAnnotation.findArgumentValue(
        property: KProperty1<A, *>,
        explicit: Boolean,
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

    private inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
        property: KProperty1<A, Annotation>,
        explicit: Boolean = false,
    ): KSAnnotation? =
        findArgumentValue(property, explicit)?.asAnnotation()

    @JvmName("getIntArrayArgumentValue")
    private inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
        property: KProperty1<A, IntArray>,
        explicit: Boolean = false,
    ): List<Int>? =
        findArgumentValue(property, explicit)?.asArray()?.mapNotNull { it.asInt() }

    @JvmName("getEnumArrayArgumentValue")
    private inline fun <reified A : Annotation, reified E : Enum<E>> KSAnnotation.getArgumentValue(
        property: KProperty1<A, Array<out E>>,
        explicit: Boolean = false,
    ): List<E>? =
        findArgumentValue(property, explicit)?.asArray()?.mapNotNull { it.asEnum() }
}

inline fun <reified T : PsiElement> PsiElement.getChildOfType(): T? =
    PsiTreeUtil.getChildOfType(this, T::class.java)

@OptIn(KaImplementationDetail::class)
inline fun <T : KtElement, R> T.useAnalysis(crossinline action: KaSession.(T) -> R): R =
    KaSessionProvider.getInstance(project).analyze(this) {
        action(this@useAnalysis)
    }
