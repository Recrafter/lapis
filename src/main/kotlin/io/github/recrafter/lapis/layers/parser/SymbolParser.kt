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
import io.github.recrafter.lapis.extensions.psi.PSICallableReferenceExpression
import io.github.recrafter.lapis.extensions.psi.PSIFunctionType
import io.github.recrafter.lapis.extensions.psi.PSISuperTypeCallEntry
import io.github.recrafter.lapis.extensions.psi.PSIValueArgumentList
import io.github.recrafter.lapis.layers.JavaMemberKind
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

object SymbolParser {

    fun parse(resolver: Resolver): ParserResult =
        ParserResult(
            schemas = resolver.getSymbolsAnnotatedWith<LaSchema>()
                .filterIsInstance<KSPClass>()
                .filter { symbol ->
                    val parent = symbol.parentDeclaration
                    parent == null || !parent.hasAnnotation<LaSchema>()
                }
                .map { parseSchema(resolver, it) },
            patches = resolver.getSymbolsAnnotatedWith<LaPatch>().map { parsePatch(it) },
        )

    private fun parseSchema(resolver: Resolver, symbol: KSPClass, parentWidener: String? = null): ParsedSchema {
        val (nestedSchemas, descriptors) = symbol.declarations
            .filterIsInstance<KSPClass>()
            .partition { it.hasAnnotation<LaSchema>() }
        val schemaAnnotation = symbol.getAnnotationOrNull<LaSchema>()
        val widener = schemaAnnotation?.widener?.ifEmpty { null }
        val explicitTarget = symbol.annotations
            .firstOrNull { it.isInstance<LaSchema>() }
            ?.findClassArgument(LaSchema::target)
            ?.takeNotNothing()
        val currentWidener = when {
            parentWidener != null && widener != null -> parentWidener + "." + widener.removePrefix(".")
            widener != null -> widener
            explicitTarget != null -> explicitTarget.qualifiedName?.asString()
            else -> null
        }
        return ParsedSchema(
            symbol = symbol,
            classType = symbol,
            targetClassType = explicitTarget ?: currentWidener?.let { resolver.getClassDeclarationByName(it) },
            widener = currentWidener,
            hasWidener = widener != null,
            isMarkedAsFinal = schemaAnnotation?.final == true,
            descriptors = descriptors.map { parseDescriptor(it) },
            nestedSchemas = nestedSchemas.map { parseSchema(resolver, it, currentWidener) },
        )
    }

    private fun parseDescriptor(declaration: KSPDeclaration): ParsedDescriptor {
        val classDeclaration = declaration.castOrNull<KSPClass>()
        val superClass = classDeclaration?.getSuperClassOrNull()
        val superClassDeclaration = superClass?.declaration?.castOrNull<KSPClass>()
        val functionType = superClass?.genericTypes?.firstOrNull()
        val functionGenericTypes = functionType?.genericTypes
        val psiDescriptorSuperType = classDeclaration?.findPsi()
            ?.superTypeListEntries
            ?.filterIsInstance<PSISuperTypeCallEntry>()
            ?.firstOrNull()
        val psiFunctionType = psiDescriptorSuperType
            ?.typeArguments
            ?.firstOrNull()
            ?.typeReference
            ?.typeElement
            ?.castOrNull<PSIFunctionType>()
        val psiCallableReference = psiDescriptorSuperType
            ?.getChildOfType<PSIValueArgumentList>()
            ?.arguments
            ?.firstOrNull()
            ?.getArgumentExpression()
            ?.castOrNull<PSICallableReferenceExpression>()
        val functionTypeReceiverName = psiFunctionType?.getReceiverTypeReference()?.text
        val receiverType = if (functionTypeReceiverName != null) {
            functionGenericTypes?.firstOrNull()
        } else null
        val hasReceiver = receiverType != null

        val accessAnnotation = classDeclaration?.getAnnotationOrNull<LaAccess>()
        val isMarkedAsFinal = accessAnnotation?.final == true
        return ParsedDescriptor(
            symbol = declaration,

            name = classDeclaration?.name,
            classType = classDeclaration,
            memberKinds = JavaMemberKind.entries.filter {
                classDeclaration?.hasAnnotation(it.annotationClass) == true
            },
            hasStaticAnnotation = classDeclaration?.hasAnnotation<LaStatic>() == true,
            hasAccessAnnotation = classDeclaration?.hasAnnotation<LaAccess>() == true,
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

            isCallable = psiCallableReference != null,
            callableReceiverName = psiCallableReference?.receiverExpression?.text,
            callableName = psiCallableReference?.callableReference?.text,

            superClassType = superClassDeclaration,
        )
    }

    private fun parsePatch(symbol: KSPAnnotated): ParsedPatch {
        val patchAnnotation = symbol.getAnnotationOrNull<LaPatch>()
        val classType = symbol.castOrNull<KSPClass>()
        val superClass = classType?.getSuperClassOrNull()

        return ParsedPatch(
            symbol = symbol,

            name = classType?.name,
            side = patchAnnotation?.side,
            classType = classType,

            superClassType = superClass?.declaration?.castOrNull<KSPClass>(),
            superGenericClassType = superClass
                ?.getGenericTypeOrNull()
                ?.declaration
                ?.castOrNull<KSPClass>(),

            targetClassType = symbol.annotations
                .firstOrNull { it.isInstance<LaPatch>() }
                ?.findClassArgument(LaPatch::target),

            properties = classType?.properties?.map {
                parsePatchProperty(it)
            }.orEmpty(),
            functions = classType?.functions
                ?.filter { !it.isConstructor() }
                ?.map { parsePatchFunction(it) }
                .orEmpty(),
        )
    }

    private fun parsePatchProperty(property: KSPProperty): ParsedPatchProperty =
        ParsedPatchProperty(
            symbol = property,

            name = property.name,
            type = property.type.resolve(),
            isPublic = property.isPublic(),
            isAbstract = property.isAbstract(),
            isExtension = property.isExtension,
            isMutable = property.isMutable && property.setter?.modifiers?.contains(Modifier.PUBLIC) == true,
        )

    private fun parsePatchFunction(function: KSPFunction): ParsedPatchFunction =
        ParsedPatchFunction(
            symbol = function,

            name = function.name,
            parameters = function.parameters.map { parsePatchFunctionParameter(function, it) },
            returnType = function.getReturnTypeOrNull(),
            hasTypeParameters = function.typeParameters.isNotEmpty(),

            isPublic = function.isPublic(),
            isAbstract = function.isAbstract,
            isExtension = function.isExtension,

            hasHookAnnotation = function.hasAnnotation<LaHook>(),
            hookDescriptorClassType = function.annotations
                .firstOrNull { it.isInstance<LaHook>() }
                ?.findClassArgument(LaHook::descriptor),
            hookKind = function.getAnnotationOrNull<LaHook>()?.kind,
        )

    private fun parsePatchFunctionParameter(
        function: KSPFunction,
        parameter: KSPValueParameter,
    ): ParsedPatchFunctionParameter {
        val name = parameter.name?.asString()
        val type = parameter.type.resolve()

        val targetAnnotation = parameter.getAnnotationOrNull<LaTarget>()
        val targetDescriptorClassType = if (targetAnnotation != null) {
            type.declaration.castOrNull<KSPClass>()
        } else null
        val targetDescriptorGenericClassType = if (targetAnnotation != null) {
            type.getGenericTypeOrNull()?.declaration?.castOrNull<KSPClass>()
        } else null

        val contextAnnotation = parameter.getAnnotationOrNull<LaContext>()
        val contextDescriptorClassType = if (contextAnnotation != null) {
            type.declaration.castOrNull<KSPClass>()
        } else null
        val contextDescriptorGenericClassType = if (contextAnnotation != null) {
            type.getGenericTypeOrNull()?.declaration?.castOrNull<KSPClass>()
        } else null

        val literalAnnotation = parameter.getAnnotationOrNull<LaLiteral>()
        val literalType = if (literalAnnotation != null) type else null
        val literalTypeName = if (literalAnnotation != null) {
            function.findPsi()
                .valueParameters
                .firstOrNull { it.name == name }
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
        val localAnnotation = parameter.getAnnotationOrNull<LaLocal>()

        return ParsedPatchFunctionParameter(
            symbol = parameter,

            name = name,
            type = type,

            hasTargetAnnotation = targetAnnotation != null,
            targetDescriptorClassType = targetDescriptorClassType,
            targetDescriptorGenericClassType = targetDescriptorGenericClassType,

            hasContextAnnotation = contextAnnotation != null,
            contextDescriptorClassType = contextDescriptorClassType,
            contextDescriptorGenericClassType = contextDescriptorGenericClassType,

            hasLiteralAnnotation = literalAnnotation != null,
            literalType = literalType,
            literalTypeName = literalTypeName,
            literalValue = literalValue,

            hasOrdinalAnnotation = ordinalAnnotation != null,
            ordinalIndices = ordinalAnnotation?.indices?.toList().orEmpty(),

            hasLocalAnnotation = localAnnotation != null,
            localOrdinal = localAnnotation?.ordinal,
        )
    }
}
