package io.github.recrafter.lapis.layers.parser

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.impl.symbol.kotlin.KSClassDeclarationImpl
import com.google.devtools.ksp.impl.symbol.kotlin.KSFunctionDeclarationImpl
import com.google.devtools.ksp.impl.symbol.kotlin.KSPropertyDeclarationJavaImpl
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPublic
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.recrafter.lapis.LapisLogger
import io.github.recrafter.lapis.annotations.*
import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.ka.KAClassSymbol
import io.github.recrafter.lapis.extensions.ks.*
import io.github.recrafter.lapis.extensions.ksp.KSPResolver
import io.github.recrafter.lapis.extensions.ksp.getSymbolsAnnotatedWith
import io.github.recrafter.lapis.extensions.ksp.isExtension
import io.github.recrafter.lapis.extensions.kt.*
import io.github.recrafter.lapis.layers.lowering.asIr
import ksp.com.intellij.psi.PsiElement
import ksp.com.intellij.psi.util.PsiTreeUtil
import ksp.org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import ksp.org.jetbrains.kotlin.analysis.api.KaSession
import ksp.org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import ksp.org.jetbrains.kotlin.analysis.api.resolution.KaErrorCallInfo
import ksp.org.jetbrains.kotlin.analysis.api.resolution.KaSuccessCallInfo
import ksp.org.jetbrains.kotlin.analysis.api.session.KaSessionProvider
import ksp.org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import ksp.org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import ksp.org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import ksp.org.jetbrains.kotlin.analysis.api.symbols.KaJavaFieldSymbol
import ksp.org.jetbrains.kotlin.analysis.api.types.KaClassType
import ksp.org.jetbrains.kotlin.psi.KtElement

class SymbolParser(
    private val resolver: KSPResolver,
    @Suppress("unused") private val logger: LapisLogger,
) {
    fun prepare(): ParserPrepareResult =
        ParserPrepareResult(
            resolver.getSymbolsAnnotatedWith<Schema>()
                .filterIsInstance<KSClassDecl>()
                .filter { symbol ->
                    val parent = symbol.parentDeclaration
                    parent == null || !parent.hasAnnotation<Schema>()
                },
            resolver.getSymbolsAnnotatedWith<Patch>()
                .filterIsInstance<KSClassDecl>(),
        )

    fun parse(): ParserResult =
        prepare().run {
            ParserResult(
                schemas = schemaClassDecls.map { parseSchema(it) },
                patches = patchClassDecls.map { parsePatch(it) },
            )
        }

    private fun parseSchema(symbol: KSClassDecl, parentBinaryName: String? = null): ParsedSchema {
        val (nestedSchemas, descriptors) = symbol.declarations
            .filterIsInstance<KSClassDecl>()
            .partition { it.getSuperClassTypeOrNull() == null }
        val accessAnnotation = symbol.getAnnotationOrNull<Access>()
        val accessTarget = accessAnnotation?.target?.ifEmpty { null }
        val explicitTarget = symbol.annotations.firstOrNull { it.isInstance<Schema>() }
            ?.getMemberTypeClassDecl(Schema::target)
        val (targetBinaryName, targetQualifiedName) = when {
            parentBinaryName != null && accessTarget != null -> {
                val innerName = accessTarget.removePrefix(".")
                parentBinaryName + "$" + innerName to parentBinaryName + "." + innerName
            }

            accessTarget != null -> accessTarget to accessTarget
            explicitTarget?.isValid == true -> {
                explicitTarget.toClassName().asIr().run {
                    binaryName to qualifiedName
                }
            }

            else -> null to null
        }
        return ParsedSchema(
            symbol = symbol,
            classDecl = symbol,
            targetClassDecl = targetQualifiedName?.let { resolver.getClassDeclarationByName(targetQualifiedName) },
            targetBinaryName = targetBinaryName,
            hasAccess = accessTarget != null,
            unfinal = accessAnnotation?.unfinal == true,
            descriptors = descriptors.map { parseDesc(it) },
            nestedSchemas = nestedSchemas.map { parseSchema(it, targetBinaryName) },
        )
    }

    private fun parseDesc(classDecl: KSClassDecl): ParsedDesc {
        val accessAnnotation = classDecl.getAnnotationOrNull<Access>()
        val superClassType = classDecl.getSuperClassTypeOrNull()
        val ktSuperTypeCallEntry = classDecl
            .castOrNull<KSClassDeclImpl>()
            ?.ktDeclarationSymbol
            ?.castOrNull<KAClassSymbol>()
            ?.psi
            ?.castOrNull<KTClassOrObject>()
            ?.superTypeListEntries
            ?.filterIsInstance<KTSuperTypeCallEntry>()
            ?.firstOrNull()
        return ParsedDesc(
            symbol = classDecl,

            name = classDecl.name,
            classDecl = classDecl,
            hasStaticAnnotation = classDecl.hasAnnotation<Static>(),
            hasAccessAnnotation = accessAnnotation != null,
            unfinal = accessAnnotation?.unfinal == true,

            generic = parseDescGeneric(
                superClassType,
                ktSuperTypeCallEntry
                    ?.typeArguments
                    ?.firstOrNull()
                    ?.typeReference
                    ?.typeElement
                    ?.castOrNull<KTFunctionType>()
            ),
            callable = parseDescCallable(ktSuperTypeCallEntry),
            superClassDecl = superClassType?.getClassDecl(),
        )
    }

    private fun parseDescGeneric(superClassType: KSType?, ktFunctionType: KTFunctionType?): ParsedDescGeneric {
        val superClassGenericType = superClassType?.genericTypes?.firstOrNull()
        return if (superClassGenericType?.isFunctionType == true) {
            val functionGenericTypes = superClassGenericType.genericTypes
            val receiverType = if (ktFunctionType?.receiver != null) functionGenericTypes.firstOrNull() else null
            ParsedFunctionTypeDescGeneric(
                receiverType = receiverType,
                parameters = functionGenericTypes
                    .drop(
                        if (receiverType != null) 1
                        else 0
                    )
                    .dropLast(1)
                    .mapIndexed { index, type ->
                        ParsedParameter(
                            type = type,
                            name = ktFunctionType?.parameters?.getOrNull(index)?.name,
                        )
                    },
                returnType = functionGenericTypes.lastOrNull()?.takeNotUnit()
            )
        } else {
            ParsedTypeDescGeneric(
                type = superClassGenericType,
                arrayComponentType = superClassGenericType?.findArrayComponentType(resolver)
            )
        }
    }

    private fun parseDescCallable(ktSuperTypeCallEntry: KTSuperTypeCallEntry?): ParsedDescCallable? {
        ktSuperTypeCallEntry?.useAnalysis { entry ->
            entry
                .typeReference
                ?.type
                ?.castOrNull<KaClassType>()
                ?.typeArguments
                ?.firstOrNull()
                ?.type
        }
        return ktSuperTypeCallEntry
            ?.getChildOfType<KTValueArgumentList>()
            ?.arguments
            ?.firstOrNull()
            ?.getArgumentExpression()
            ?.castOrNull<KTCallableReferenceExpression>()
            ?.useAnalysis { callable ->
                val callInfo = callable.resolveToCall()
                if (callInfo is KaErrorCallInfo && callInfo.diagnostic.factoryName == "INVISIBLE_REFERENCE") {
                    return@useAnalysis PrivateCallable(callable.callableReference.text)
                }
                val successCallInfo = callInfo as? KaSuccessCallInfo ?: return@useAnalysis null
                val call = successCallInfo.call as? KaCallableMemberCall<*, *> ?: return@useAnalysis null
                val symbol = call.partiallyAppliedSymbol.signature.symbol
                val containingClassSymbol = symbol.containingSymbol as? KaClassSymbol
                val receiverClassDecl = containingClassSymbol?.let { KSClassDeclarationImpl.getCached(it) }
                when (symbol) {
                    is KaConstructorSymbol -> {
                        val decl = KSFunctionDeclarationImpl.getCached(symbol)
                        ParsedConstructorDescCallable(
                            receiverClassDecl = receiverClassDecl,
                            parameters = decl.parameters.map {
                                ParsedParameter(it.type.resolve(), it.name?.asString())
                            },
                            returnType = decl.getReturnTypeOrNull(),
                        )
                    }

                    is KaFunctionSymbol -> {
                        val decl = KSFunctionDeclarationImpl.getCached(symbol)
                        ParsedMethodDescCallable(
                            receiverClassDecl = receiverClassDecl,
                            name = decl.name,
                            parameters = decl.parameters.map {
                                ParsedParameter(it.type.resolve(), it.name?.asString())
                            },
                            returnType = decl.getReturnTypeOrNull(),
                        )
                    }

                    is KaJavaFieldSymbol -> {
                        val decl = KSPropertyDeclarationJavaImpl.getCached(symbol)
                        ParsedFieldDescCallable(
                            receiverClassDecl = receiverClassDecl,
                            name = decl.name,
                            type = decl.type.resolve(),
                        )
                    }

                    else -> null
                }
            }
    }

    private fun parsePatch(symbol: KSAnnotated): ParsedPatch {
        val patchAnnotation = symbol.getAnnotationOrNull<Patch>()
        val classDecl = symbol.castOrNull<KSClassDecl>()
        val superClassType = classDecl?.getSuperClassTypeOrNull()
        val classFunctions = classDecl?.functionDeclarations
            ?.filter { !it.isConstructor() }
            ?.map { parsePatchFunction(it, fromCompanionObject = false) }
            .orEmpty()
        val companionObjectFunctions = classDecl?.declarations
            ?.filterIsInstance<KSClassDecl>()
            ?.find { it.isCompanionObject }
            ?.functionDeclarations
            ?.filter { !it.isConstructor() }
            ?.map { parsePatchFunction(it, fromCompanionObject = true) }
            .orEmpty()
        return ParsedPatch(
            symbol = symbol,

            name = classDecl?.name,
            side = patchAnnotation?.side,
            classDecl = classDecl,

            superClassDecl = superClassType?.getClassDecl(),
            superGenericClassDecl = superClassType?.getGenericTypeOrNull()?.getClassDecl(),

            schemaClassDecl = symbol.annotations
                .firstOrNull { it.isInstance<Patch>() }
                ?.getMemberTypeClassDecl(Patch::schema),

            properties = classDecl?.propertyDeclarations?.map { parsePatchProperty(it) }.orEmpty(),
            functions = classFunctions + companionObjectFunctions,
        )
    }

    private fun parsePatchProperty(propertyDecl: KSPropertyDecl): ParsedPatchProperty =
        ParsedPatchProperty(
            symbol = propertyDecl,

            name = propertyDecl.name,
            type = propertyDecl.type.resolve(),
            isPublic = propertyDecl.isPublic(),
            isAbstract = propertyDecl.isAbstract(),
            isExtension = propertyDecl.isExtension,
            isMutable = propertyDecl.isMutable && propertyDecl.setter?.modifiers?.contains(KSPModifier.PUBLIC) == true,
        )

    private fun parsePatchFunction(functionDecl: KSFunctionDecl, fromCompanionObject: Boolean): ParsedPatchFunction {
        val atConstructorHeadAnnotation = functionDecl.getAnnotationOrNull<AtConstructorHead>()
        val atLocalAnnotation = functionDecl.getAnnotationOrNull<AtLocal>()
        val atInstanceofAnnotation = functionDecl.getAnnotationOrNull<AtInstanceof>()
        val atReturnAnnotation = functionDecl.getAnnotationOrNull<AtReturn>()
        val atLiteralKspAnnotation = functionDecl.annotations.find { it.isInstance<AtLiteral>() }
        val atLiteralAnnotation = functionDecl.getAnnotationOrNull<AtLiteral>()
        val atFieldAnnotation = functionDecl.getAnnotationOrNull<AtField>()
        val atArrayAnnotation = functionDecl.getAnnotationOrNull<AtArray>()
        val atCallAnnotation = functionDecl.getAnnotationOrNull<AtCall>()
        return ParsedPatchFunction(
            symbol = functionDecl,

            name = functionDecl.name,
            parameters = functionDecl.parameters.map { parsePatchFunctionParameter(it) },
            returnType = functionDecl.getReturnTypeOrNull(),
            hasTypeParameters = functionDecl.typeParameters.isNotEmpty(),

            isPublic = functionDecl.isPublic(),
            isAbstract = functionDecl.isAbstract,
            isExtension = functionDecl.isExtension,

            fromCompanionObject = fromCompanionObject,

            hasHookAnnotation = functionDecl.hasAnnotation<Hook>(),
            hookDescClassDecl = functionDecl
                .annotations
                .firstOrNull { it.isInstance<Hook>() }
                ?.getMemberTypeClassDecl(Hook::desc),
            hookAt = functionDecl.getAnnotationOrNull<Hook>()?.at,

            hasAtConstructorHeadAnnotation = atConstructorHeadAnnotation != null,
            atConstructorHeadPhase = atConstructorHeadAnnotation?.phase,

            hasAtLocalAnnotation = atLocalAnnotation != null,
            atLocalOp = atLocalAnnotation?.op,
            atLocalType = functionDecl
                .annotations
                .firstOrNull { it.isInstance<AtLocal>() }
                ?.findArgument(AtLocal::type)
                ?.getKClassType(),
            atLocalName = atLocalAnnotation?.local?.name?.ifEmpty { null },
            atLocalOrdinal = atLocalAnnotation?.local?.ordinal?.takeIf { it != -1 },
            atLocalOpOrdinals = atLocalAnnotation?.ordinal?.toList().orEmpty(),

            hasAtInstanceofAnnotation = atInstanceofAnnotation != null,
            atInstanceofType = functionDecl
                .annotations
                .firstOrNull { it.isInstance<AtInstanceof>() }
                ?.getMemberTypeClassDecl(AtInstanceof::type),
            atInstanceofOrdinals = atInstanceofAnnotation?.ordinal?.toList().orEmpty(),

            hasAtReturnAnnotation = atReturnAnnotation != null,
            atReturnOrdinals = atReturnAnnotation?.ordinal?.toList().orEmpty(),

            hasAtLiteralAnnotation = atLiteralAnnotation != null,
            atLiteralArguments = atLiteralKspAnnotation
                ?.explicitArguments?.filterNot { it.name?.asString() == AtLiteral::ordinal.name }
                ?.map { argument ->
                    val name = argument.name?.asString()
                    ParsedAnnotationArgumentVariant(
                        name = name,
                        type = name?.let {
                            atLiteralKspAnnotation.getArgumentType(
                                if (AtLiteral::zero.name == name) AtLiteral::int.name
                                else it
                            )
                        },
                        value = argument.value,
                    )
                }
                .orEmpty(),
            atLiteralOrdinals = atLiteralAnnotation?.ordinal?.toList().orEmpty(),
            atLiteralZeroConditions = atLiteralAnnotation?.zero?.conditions?.toList().orEmpty(),
            atLiteralInt = atLiteralAnnotation?.int,
            atLiteralFloat = atLiteralAnnotation?.float,
            atLiteralLong = atLiteralAnnotation?.long,
            atLiteralDouble = atLiteralAnnotation?.double,
            atLiteralString = atLiteralAnnotation?.string,

            hasAtFieldAnnotation = atFieldAnnotation != null,
            atFieldOp = atFieldAnnotation?.op,
            atFieldDescClassDecl = functionDecl.annotations.firstOrNull { it.isInstance<AtField>() }
                ?.getMemberTypeClassDecl(AtField::desc),
            atFieldOrdinals = atFieldAnnotation?.ordinal?.toList().orEmpty(),

            hasAtArrayAnnotation = atArrayAnnotation != null,
            atArrayOp = atArrayAnnotation?.op,
            atArrayDescClassDecl = functionDecl.annotations.firstOrNull { it.isInstance<AtArray>() }
                ?.getMemberTypeClassDecl(AtArray::desc),
            atArrayOrdinals = atArrayAnnotation?.ordinal?.toList().orEmpty(),

            hasAtCallAnnotation = atCallAnnotation != null,
            atCallDescClassDecl = functionDecl.annotations.firstOrNull { it.isInstance<AtCall>() }
                ?.getMemberTypeClassDecl(AtCall::desc),
            atCallOrdinals = atCallAnnotation?.ordinal?.toList().orEmpty(),
        )
    }

    private fun parsePatchFunctionParameter(parameter: KSValueParameter): ParsedPatchFunctionParameter {
        val name = parameter.name?.asString()
        val type = parameter.type.resolve()
        val originAnnotation = parameter.getAnnotationOrNull<Origin>()
        val cancelAnnotation = parameter.getAnnotationOrNull<Cancel>()
        val ordinalAnnotation = parameter.getAnnotationOrNull<Ordinal>()
        val paramAnnotation = parameter.getAnnotationOrNull<Param>()
        val localAnnotation = parameter.getAnnotationOrNull<Local>()
        val shareAnnotation = parameter.getAnnotationOrNull<Share>()
        return ParsedPatchFunctionParameter(
            symbol = parameter,

            name = name,
            type = type,
            hasDefaultArgument = parameter.hasDefault,

            hasOriginAnnotation = originAnnotation != null,
            originGenericClassDecl = if (originAnnotation != null) {
                type.getGenericTypeOrNull()?.getClassDecl()
            } else null,

            hasCancelAnnotation = cancelAnnotation != null,
            cancelGenericClassDecl = if (cancelAnnotation != null) {
                type.getGenericTypeOrNull()?.getClassDecl()
            } else null,

            hasOrdinalAnnotation = ordinalAnnotation != null,

            hasParamAnnotation = paramAnnotation != null,
            paramName = paramAnnotation?.name?.ifEmpty { name },

            hasLocalAnnotation = localAnnotation != null,
            localName = localAnnotation?.name?.ifEmpty { null },
            localOrdinal = localAnnotation?.ordinal?.takeIf { it != -1 },

            hasShareAnnotation = shareAnnotation != null,
            shareKey = shareAnnotation?.key,
            isShareExported = shareAnnotation?.exported == true,
        )
    }
}

inline fun <reified T : PsiElement> PsiElement.getChildOfType(): T? =
    PsiTreeUtil.getChildOfType(this, T::class.java)

@OptIn(KaImplementationDetail::class)
inline fun <T : KtElement, R> T.useAnalysis(crossinline action: KaSession.(T) -> R): R =
    KaSessionProvider.getInstance(project).analyze(this) {
        action(this@useAnalysis)
    }
