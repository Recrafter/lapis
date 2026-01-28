package io.github.recrafter.lapis.layers.parser

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Resolver
import io.github.recrafter.lapis.annotations.*
import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.ksp.*
import io.github.recrafter.lapis.extensions.psi.PsiCallableReferenceExpression
import io.github.recrafter.lapis.extensions.psi.PsiFunctionType
import io.github.recrafter.lapis.extensions.psi.PsiSuperTypeCallEntry
import io.github.recrafter.lapis.extensions.psi.PsiValueArgumentList
import io.github.recrafter.lapis.utils.MemberKind
import io.github.recrafter.lapis.utils.PsiCompanion
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

class SymbolParser(
    private val resolver: Resolver,
    private val psiCompanion: PsiCompanion,
    private val logger: KspLogger,
) {
    fun parse(): ParserResult =
        ParserResult(
            descriptorContainers = resolver.getSymbolsAnnotatedWith<LaDescriptors>().map { symbol ->
                val classDeclaration = symbol.castOrNull<KspClassDeclaration>()
                val targetClassDeclaration = symbol.annotations
                    .firstOrNull { it.isInstance<LaDescriptors>() }
                    ?.findClassArgument(LaDescriptors::target)
                ParsedDescriptorContainer(
                    source = symbol,
                    classDeclaration = classDeclaration,
                    targetClassDeclaration = targetClassDeclaration,
                    descriptors = symbol.castOrNull<KspClassDeclaration>()
                        ?.declarations
                        ?.mapNotNull { parseDescriptor(it, targetClassDeclaration) }
                        ?.toList()
                        .orEmpty()
                )
            },
            patches = resolver.getSymbolsAnnotatedWith<LaPatch>().map { parsePatch(it) },
        )

    private fun parseDescriptor(
        declaration: KspDeclaration,
        targetClassDeclaration: KspClassDeclaration?,
    ): ParsedDescriptor? {
        if (declaration is KspFunctionDeclaration && declaration.isConstructor()) {
            return null
        }
        val classDeclaration = declaration.castOrNull<KspClassDeclaration>()
        val superClass = classDeclaration?.getSuperClassOrNull()
        val superClassDeclaration = superClass?.declaration?.castOrNull<KspClassDeclaration>()
        val functionType = superClass?.genericTypes?.firstOrNull()
        val functionGenericTypes = functionType?.genericTypes
        val psiDescriptorSuperType = classDeclaration?.findPsi(psiCompanion)
            ?.superTypeListEntries
            ?.filterIsInstance<PsiSuperTypeCallEntry>()
            ?.firstOrNull()
        val psiFunctionType = psiDescriptorSuperType
            ?.typeArguments
            ?.firstOrNull()
            ?.typeReference
            ?.typeElement
            ?.castOrNull<PsiFunctionType>()
        val psiCallableReference = psiDescriptorSuperType
            ?.getChildOfType<PsiValueArgumentList>()
            ?.arguments
            ?.firstOrNull()
            ?.getArgumentExpression()
            ?.castOrNull<PsiCallableReferenceExpression>()
        val functionTypeReceiverName = psiFunctionType?.getReceiverTypeReference()?.text
        val receiverType = if (functionTypeReceiverName != null) {
            functionGenericTypes?.firstOrNull()
        } else null
        val hasReceiver = receiverType != null
        return ParsedDescriptor(
            source = declaration,

            classDeclaration = classDeclaration,
            targetClassDeclaration = targetClassDeclaration,
            memberKinds = MemberKind.entries.filter {
                classDeclaration?.hasAnnotation(it.annotationClass) == true
            },
            hasStaticAnnotation = classDeclaration?.hasAnnotation<LaStatic>() == true,

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

            isCallable = psiCallableReference != null,
            callableReceiverName = psiCallableReference?.receiverExpression?.text,
            callableName = psiCallableReference?.callableReference?.text,

            superClassDeclaration = superClassDeclaration,
        )
    }

    private fun parsePatch(symbol: KspAnnotated): ParsedPatch {
        val patchAnnotation = symbol.getAnnotationOrNull<LaPatch>()
        val classDeclaration = symbol.castOrNull<KspClassDeclaration>()

        val outerClass = classDeclaration?.parentDeclaration?.castOrNull<KspClassDeclaration>()
        val outerAnnotation = outerClass?.getAnnotationOrNull<LaPatch>()

        val superClass = classDeclaration?.getSuperClassOrNull()

        return ParsedPatch(
            source = symbol,

            name = classDeclaration?.name,
            side = patchAnnotation?.side,
            widener = patchAnnotation?.widener?.ifEmpty { null },
            classDeclaration = classDeclaration,

            hasOuter = classDeclaration?.hasParent() == true,
            hasOuterAnnotation = outerAnnotation != null,
            outerWidener = outerAnnotation?.widener?.ifEmpty { null },
            outerClassDeclaration = outerClass,

            superClassDeclaration = superClass?.declaration?.castOrNull<KspClassDeclaration>(),
            superClassGenericDeclaration = superClass
                ?.getGenericTypeOrNull()
                ?.declaration
                ?.castOrNull<KspClassDeclaration>(),

            targetClassDeclaration = symbol.annotations
                .firstOrNull { it.isInstance<LaPatch>() }
                ?.findClassArgument(LaPatch::target),

            properties = classDeclaration?.getProperties()?.map { parsePatchProperty(it) }.orEmpty(),
            functions = classDeclaration?.getFunctions()
                ?.filter { !it.isConstructor() }
                ?.map { parsePatchFunction(it) }
                .orEmpty(),
        )
    }

    private fun parsePatchProperty(propertyDeclaration: KspPropertyDeclaration): ParsedPatchProperty {
        val accessAnnotation = propertyDeclaration.getAnnotationOrNull<LaAccess>()
        return ParsedPatchProperty(
            source = propertyDeclaration,

            name = propertyDeclaration.name,
            type = propertyDeclaration.type.resolve(),
            isPublic = propertyDeclaration.isPublic(),
            isAbstract = propertyDeclaration.isAbstract(),
            isExtension = propertyDeclaration.isExtension(),

            hasAccessAnnotation = accessAnnotation != null,
            accessVanillaName = accessAnnotation?.vanillaName?.ifEmpty { propertyDeclaration.name },
            hasFieldAnnotation = propertyDeclaration.hasAnnotation<LaField>(),

            hasStaticAnnotation = propertyDeclaration.hasAnnotation<LaStatic>(),
            isMutable = propertyDeclaration.isMutable,
        )
    }

    private fun parsePatchFunction(functionDeclaration: KspFunctionDeclaration): ParsedPatchFunction {
        val accessAnnotation = functionDeclaration.getAnnotationOrNull<LaAccess>()
        return ParsedPatchFunction(
            source = functionDeclaration,

            name = functionDeclaration.name,
            parameters = functionDeclaration.parameters.map { parsePatchFunctionParameter(functionDeclaration, it) },
            returnType = functionDeclaration.getReturnTypeOrNull(),
            isPublic = functionDeclaration.isPublic(),
            isAbstract = functionDeclaration.isAbstract,
            isExtension = functionDeclaration.isExtension(),

            hasAccessAnnotation = accessAnnotation != null,
            accessVanillaName = accessAnnotation?.vanillaName?.ifEmpty { functionDeclaration.name },
            accessMemberKinds = MemberKind.entries.filter {
                functionDeclaration.hasAnnotation(it.annotationClass)
            },

            hasStaticAnnotation = functionDeclaration.hasAnnotation<LaStatic>(),

            hasHookAnnotation = functionDeclaration.hasAnnotation<LaHook>(),
            hookMethodDescriptorClassDeclaration = functionDeclaration.annotations
                .firstOrNull { it.isInstance<LaHook>() }
                ?.findClassArgument(LaHook::method),
            hookKind = functionDeclaration.getAnnotationOrNull<LaHook>()?.kind,
        )
    }

    private fun parsePatchFunctionParameter(
        functionDeclaration: KspFunctionDeclaration,
        parameter: KspValueParameter,
    ): ParsedPatchFunctionParameter {
        val name = parameter.name?.asString()
        val type = parameter.type.resolve()
        val targetDescriptorClass = type.declaration.castOrNull<KspClassDeclaration>()

        val literalAnnotation = parameter.getAnnotationOrNull<LaLiteral>()
        val literalType = if (literalAnnotation != null) {
            type
        } else null
        val literalTypeName = if (literalAnnotation != null) {
            functionDeclaration.findPsi(psiCompanion)
                ?.valueParameters
                ?.firstOrNull { it.name == name }
                ?.annotationEntries
                ?.firstOrNull()
                ?.valueArguments
                ?.singleOrNull()
                ?.getArgumentName()
                ?.asName
                ?.asString()
        } else null
        val literalValue = if (literalAnnotation != null) {
            parameter.annotations
                .firstOrNull { it.isInstance<LaLiteral>() }
                ?.arguments
                ?.find { it.name?.asString() == literalTypeName }
                ?.value
                ?.toString()
        } else null

        val ordinalAnnotation = parameter.getAnnotationOrNull<LaOrdinal>()
        val parameterAnnotation = parameter.getAnnotationOrNull<LaParameter>()
        val localAnnotation = parameter.getAnnotationOrNull<LaLocal>()

        return ParsedPatchFunctionParameter(
            source = parameter,

            name = name,
            type = type,

            hasTargetAnnotation = parameter.hasAnnotation<LaTarget>(),
            targetDescriptorClassDeclaration = targetDescriptorClass,

            hasLiteralAnnotation = literalAnnotation != null,
            literalType = literalType,
            literalTypeName = literalTypeName,
            literalValue = literalValue,

            hasOrdinalAnnotation = ordinalAnnotation != null,
            ordinals = ordinalAnnotation?.indices?.toList().orEmpty(),

            hasReturnAnnotation = parameter.hasAnnotation<LaReturn>(),
            returnKind = ParsedReturnKind.entries.firstOrNull {
                type.declaration.isInstance(it.typeClass)
            },

            hasParameterAnnotation = parameterAnnotation != null,
            parameterName = parameterAnnotation?.name?.ifEmpty { name },

            hasLocalAnnotation = localAnnotation != null,
            localName = localAnnotation?.name?.ifEmpty { null },
            localIndex = localAnnotation?.index.takeIf { it != null && it >= 0 },
        )
    }
}
