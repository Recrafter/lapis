package io.github.recrafter.lapis.layers.parser

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.Modifier
import io.github.recrafter.lapis.annotations.*
import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.ksp.*
import io.github.recrafter.lapis.extensions.psi.PSICallableReference
import io.github.recrafter.lapis.extensions.psi.PSIFunctionType
import io.github.recrafter.lapis.extensions.psi.PSISuperTypeCallEntry
import io.github.recrafter.lapis.extensions.psi.PSIValueArgumentList
import io.github.recrafter.lapis.layers.JavaMemberKind
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

object SymbolParser {

    fun parse(resolver: Resolver): ParserResult =
        ParserResult(
            schemas = resolver.getSymbolsAnnotatedWith<Schema>()
                .filterIsInstance<KSPClassDecl>()
                .filter { symbol ->
                    val parent = symbol.parentDeclaration
                    parent == null || !parent.hasAnnotation<Schema>()
                }
                .map { parseSchema(resolver, it) },
            patches = resolver.getSymbolsAnnotatedWith<Patch>().map { parsePatch(it) },
        )

    private fun parseSchema(resolver: Resolver, symbol: KSPClassDecl, parentAccess: String? = null): ParsedSchema {
        val (nestedSchemas, descriptors) = symbol.declarations
            .filterIsInstance<KSPClassDecl>()
            .partition { it.hasAnnotation<Schema>() }
        val schemaAnnotation = symbol.getAnnotationOrNull<Schema>()
        val access = schemaAnnotation?.access?.ifEmpty { null }
        val explicitTarget = symbol.annotations.firstOrNull { it.isInstance<Schema>() }
            ?.getClassDeclValue(Schema::target)
            ?.takeNotNothing()
        val resultAccess = when {
            parentAccess != null && access != null -> parentAccess + "." + access.removePrefix(".")
            access != null -> access
            explicitTarget != null -> explicitTarget.qualifiedName?.asString()
            else -> null
        }
        return ParsedSchema(
            source = symbol,
            classDecl = symbol,
            targetClassDecl = explicitTarget ?: resultAccess?.let { resolver.getClassDeclarationByName(it) },
            access = resultAccess,
            hasAccess = access != null,
            isMarkedAsFinal = schemaAnnotation?.final == true,
            descriptors = descriptors.map { parseDesc(it) },
            nestedSchemas = nestedSchemas.map { parseSchema(resolver, it, resultAccess) },
        )
    }

    private fun parseDesc(decl: KSPDecl): ParsedDesc {
        val classDecl = decl.castOrNull<KSPClassDecl>()
        val superClass = classDecl?.getSuperClassTypeOrNull()
        val superClassDecl = superClass?.toClassDeclOrNull()
        val functionType = superClass?.genericTypes?.firstOrNull()
        val functionGenericTypes = functionType?.genericTypes
        val (psiFunctionType, psiCallableReference) = parsePSI(classDecl)
        val callableReference = psiCallableReference?.let {
            ParsedCallableReference(it.receiverExpression?.text, it.callableReference.text)
        }
        val functionTypeReceiverName = psiFunctionType?.getReceiverTypeReference()?.text
        val receiverType = if (functionTypeReceiverName != null) {
            functionGenericTypes?.firstOrNull()
        } else null
        val hasReceiver = receiverType != null

        val accessAnnotation = classDecl?.getAnnotationOrNull<Access>()
        val isMarkedAsFinal = accessAnnotation?.final == true
        return ParsedDesc(
            source = decl,

            name = classDecl?.name,
            classDecl = classDecl,
            memberKinds = JavaMemberKind.entries.filter {
                classDecl?.hasAnnotation(it.annotationClass) == true
            },
            hasStaticAnnotation = classDecl?.hasAnnotation<Static>() == true,
            hasAccessAnnotation = classDecl?.hasAnnotation<Access>() == true,
            isMarkedAsFinal = isMarkedAsFinal,

            isFunctionType = psiFunctionType != null && functionType?.isFunctionType == true,
            hasReceiver = hasReceiver,
            functionTypeReceiverName = functionTypeReceiverName,
            receiverType = receiverType,
            parameters = functionGenericTypes
                ?.drop(
                    if (hasReceiver) 1
                    else 0
                )
                ?.dropLast(1)
                ?.mapIndexed { index, type ->
                    ParsedFunctionTypeParameter(
                        type = type,
                        name = psiFunctionType?.parameters?.getOrNull(index)?.name,
                    )
                }
                .orEmpty(),
            returnType = functionGenericTypes?.lastOrNull()?.takeNotUnit(),
            callableReference = callableReference,
            superClassDecl = superClassDecl,
        )
    }

    private fun parsePSI(descClassDecl: KSPClassDecl?): Pair<PSIFunctionType?, PSICallableReference?> {
        val psiDescSuperType = descClassDecl?.findPsi()
            ?.superTypeListEntries
            ?.filterIsInstance<PSISuperTypeCallEntry>()
            ?.firstOrNull()
        val psiFunctionType = psiDescSuperType
            ?.typeArguments
            ?.firstOrNull()
            ?.typeReference
            ?.typeElement
            ?.castOrNull<PSIFunctionType>()
        val psiCallableReference = psiDescSuperType
            ?.getChildOfType<PSIValueArgumentList>()
            ?.arguments
            ?.firstOrNull()
            ?.getArgumentExpression()
            ?.castOrNull<PSICallableReference>()
        return psiFunctionType to psiCallableReference
    }

    private fun parsePatch(symbol: KSPAnnotated): ParsedPatch {
        val patchAnnotation = symbol.getAnnotationOrNull<Patch>()
        val classDecl = symbol.castOrNull<KSPClassDecl>()
        val superClassType = classDecl?.getSuperClassTypeOrNull()
        return ParsedPatch(
            source = symbol,

            name = classDecl?.name,
            side = patchAnnotation?.side,
            classDecl = classDecl,

            superClassDecl = superClassType?.toClassDeclOrNull(),
            superGenericClassDecl = superClassType?.getGenericTypeOrNull()?.toClassDeclOrNull(),

            schemaClassDecl = symbol.annotations
                .firstOrNull { it.isInstance<Patch>() }
                ?.getClassDeclValue(Patch::schema),

            properties = classDecl?.propertyDeclarations?.map { parsePatchProperty(it) }.orEmpty(),
            functions = classDecl?.functionDeclarations
                ?.filter { !it.isConstructor() }
                ?.map { parsePatchFunction(it) }
                .orEmpty(),
        )
    }

    private fun parsePatchProperty(propertyDecl: KSPPropertyDecl): ParsedPatchProperty =
        ParsedPatchProperty(
            source = propertyDecl,

            name = propertyDecl.name,
            type = propertyDecl.type.resolve(),
            isPublic = propertyDecl.isPublic(),
            isAbstract = propertyDecl.isAbstract(),
            isExtension = propertyDecl.isExtension,
            isMutable = propertyDecl.isMutable && propertyDecl.setter?.modifiers?.contains(Modifier.PUBLIC) == true,
        )

    private fun parsePatchFunction(functionDecl: KSPFunctionDecl): ParsedPatchFunction {
        val atLiteralKspAnnotation = functionDecl.annotations.find { it.isInstance<AtLiteral>() }
        val atLiteralAnnotation = functionDecl.getAnnotationOrNull<AtLiteral>()
        val atConstructorHeadAnnotation = functionDecl.getAnnotationOrNull<AtConstructorHead>()
        val atLocalAnnotation = functionDecl.getAnnotationOrNull<AtLocal>()
        val atInstanceofAnnotation = functionDecl.getAnnotationOrNull<AtInstanceof>()
        val atReturnAnnotation = functionDecl.getAnnotationOrNull<AtReturn>()
        val atFieldAnnotation = functionDecl.getAnnotationOrNull<AtField>()
        val atArrayAnnotation = functionDecl.getAnnotationOrNull<AtArray>()
        val atCallAnnotation = functionDecl.getAnnotationOrNull<AtCall>()
        return ParsedPatchFunction(
            source = functionDecl,

            name = functionDecl.name,
            parameters = functionDecl.parameters.map { parsePatchFunctionParameter(it) },
            returnType = functionDecl.getReturnTypeOrNull(),
            hasTypeParameters = functionDecl.typeParameters.isNotEmpty(),

            isPublic = functionDecl.isPublic(),
            isAbstract = functionDecl.isAbstract,
            isExtension = functionDecl.isExtension,

            hasHookAnnotation = functionDecl.hasAnnotation<Hook>(),
            hookDescClassDecl = functionDecl
                .annotations.firstOrNull { it.isInstance<Hook>() }
                ?.getClassDeclValue(Hook::desc),
            hookAt = functionDecl.getAnnotationOrNull<Hook>()?.at,

            hasAtConstructorHeadAnnotation = atConstructorHeadAnnotation != null,
            atConstructorHeadPhase = atConstructorHeadAnnotation?.phase,

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
            atLiteralClass = atLiteralAnnotation?.`class`,

            hasAtLocalAnnotation = atLocalAnnotation != null,
            atLocalOp = atLocalAnnotation?.op,
            atLocalType = functionDecl.annotations.firstOrNull { it.isInstance<AtLocal>() }
                ?.getClassDeclValue(AtLocal::type),
            atLocalOrdinal = atLocalAnnotation?.ordinal,

            hasAtInstanceofAnnotation = atInstanceofAnnotation != null,
            atInstanceofType = functionDecl.annotations.firstOrNull { it.isInstance<AtInstanceof>() }
                ?.getClassDeclValue(AtInstanceof::type),
            atInstanceofOrdinals = atInstanceofAnnotation?.ordinal?.toList().orEmpty(),

            hasAtReturnAnnotation = atReturnAnnotation != null,
            atReturnLast = atReturnAnnotation?.last == true,
            atReturnOrdinals = atReturnAnnotation?.ordinal?.toList().orEmpty(),

            hasAtFieldAnnotation = atFieldAnnotation != null,
            atFieldOp = atFieldAnnotation?.op,
            atFieldDescClassDecl = functionDecl.annotations.firstOrNull { it.isInstance<AtField>() }
                ?.getClassDeclValue(AtField::desc),
            atFieldOrdinals = atFieldAnnotation?.ordinal?.toList().orEmpty(),

            hasAtArrayAnnotation = atArrayAnnotation != null,
            atArrayOp = atArrayAnnotation?.op,
            atArrayDescClassDecl = functionDecl.annotations.firstOrNull { it.isInstance<AtArray>() }
                ?.getClassDeclValue(AtArray::desc),
            atArrayOrdinals = atArrayAnnotation?.ordinal?.toList().orEmpty(),

            hasAtCallAnnotation = atCallAnnotation != null,
            atCallDescClassDecl = functionDecl.annotations.firstOrNull { it.isInstance<AtCall>() }
                ?.getClassDeclValue(AtCall::desc),
            atCallOrdinals = atCallAnnotation?.ordinal?.toList().orEmpty(),
        )
    }

    private fun parsePatchFunctionParameter(parameter: KSPValueParameter): ParsedPatchFunctionParameter {
        val name = parameter.name?.asString()
        val type = parameter.type.resolve()
        val originAnnotation = parameter.getAnnotationOrNull<Origin>()
        val cancelAnnotation = parameter.getAnnotationOrNull<Cancel>()
        val paramAnnotation = parameter.getAnnotationOrNull<Param>()
        val localAnnotation = parameter.getAnnotationOrNull<Local>()
        val shareAnnotation = parameter.getAnnotationOrNull<Share>()
        return ParsedPatchFunctionParameter(
            source = parameter,

            name = name,
            type = type,

            hasOriginAnnotation = originAnnotation != null,
            originGenericClassDecl = if (originAnnotation != null) {
                type.getGenericTypeOrNull()?.toClassDeclOrNull()
            } else null,

            hasCancelAnnotation = cancelAnnotation != null,
            cancelGenericClassDecl = if (cancelAnnotation != null) {
                type.getGenericTypeOrNull()?.toClassDeclOrNull()
            } else null,

            hasParamAnnotation = paramAnnotation != null,
            paramName = paramAnnotation?.name?.ifEmpty { name },

            hasLocalAnnotation = localAnnotation != null,
            localOrdinal = localAnnotation?.ordinal,

            hasShareAnnotation = shareAnnotation != null,
            shareKey = shareAnnotation?.key,
            isShareExported = shareAnnotation?.exported == true,
        )
    }
}
