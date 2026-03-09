package io.github.recrafter.lapis.layers.parser

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
            descriptorContainers = resolver
                .getSymbolsAnnotatedWith<LaDescriptors>()
                .map { parseDescriptorContainer(it) },
            patches = resolver
                .getSymbolsAnnotatedWith<LaPatch>()
                .map { parsePatch(it) },
        )

    private fun parseDescriptorContainer(symbol: KSPAnnotated): ParsedDescriptorContainer {
        val targetClassType = symbol.annotations
            .firstOrNull { it.isInstance<LaDescriptors>() }
            ?.findClassArgument(LaDescriptors::target)
        return ParsedDescriptorContainer(
            symbol = symbol,
            classType = symbol.castOrNull<KSPClass>(),
            targetClassType = targetClassType,
            descriptors = symbol.castOrNull<KSPClass>()
                ?.declarations
                ?.mapNotNull { parseDescriptor(it, targetClassType) }
                ?.toList()
                .orEmpty()
        )
    }

    private fun parseDescriptor(declaration: KSPDeclaration, targetClassType: KSPClass?): ParsedDescriptor? {
        if (declaration is KSPFunction && declaration.isConstructor()) {
            return null
        }
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
        val receiverType = when {
            functionTypeReceiverName != null -> functionGenericTypes?.firstOrNull()
            else -> null
        }
        val hasReceiver = receiverType != null
        return ParsedDescriptor(
            symbol = declaration,

            classType = classDeclaration,
            targetClassType = targetClassType,
            memberKinds = JavaMemberKind.entries.filter {
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

            superClassType = superClassDeclaration,
        )
    }

    private fun parsePatch(symbol: KSPAnnotated): ParsedPatch {
        val patchAnnotation = symbol.getAnnotationOrNull<LaPatch>()
        val classType = symbol.castOrNull<KSPClass>()

        val outerClassType = classType?.parentDeclaration?.castOrNull<KSPClass>()
        val outerAnnotation = outerClassType?.getAnnotationOrNull<LaPatch>()

        val superClass = classType?.getSuperClassOrNull()

        return ParsedPatch(
            symbol = symbol,

            name = classType?.name,
            side = patchAnnotation?.side,
            widener = patchAnnotation?.widener?.ifEmpty { null },
            classType = classType,

            hasOuter = classType?.hasParent() == true,
            hasOuterAnnotation = outerAnnotation != null,
            outerWidener = outerAnnotation?.widener?.ifEmpty { null },
            outerClassType = outerClassType,

            superClassType = superClass?.declaration?.castOrNull<KSPClass>(),
            superGenericClassType = superClass
                ?.getGenericTypeOrNull()
                ?.declaration
                ?.castOrNull<KSPClass>(),

            targetClassType = symbol.annotations
                .firstOrNull { it.isInstance<LaPatch>() }
                ?.findClassArgument(LaPatch::target),

            properties = classType?.getProperties()?.map { parsePatchProperty(it) }.orEmpty(),
            functions = classType?.getFunctions()
                ?.filter { !it.isConstructor() }
                ?.map { parsePatchFunction(it) }
                .orEmpty(),
        )
    }

    private fun parsePatchProperty(property: KSPProperty): ParsedPatchProperty {
        val accessAnnotation = property.getAnnotationOrNull<LaAccess>()
        return ParsedPatchProperty(
            symbol = property,

            name = property.name,
            type = property.type.resolve(),
            isPublic = property.isPublic(),
            isAbstract = property.isAbstract(),
            isExtension = property.isExtension(),

            hasAccessAnnotation = accessAnnotation != null,
            accessName = accessAnnotation?.name?.ifEmpty { property.name },
            hasFieldAnnotation = property.hasAnnotation<LaField>(),

            hasStaticAnnotation = property.hasAnnotation<LaStatic>(),
            isMutable = property.isMutable,
            isSetterPublic = property.setter?.modifiers?.contains(Modifier.PUBLIC) == true,
        )
    }

    private fun parsePatchFunction(function: KSPFunction): ParsedPatchFunction {
        val accessAnnotation = function.getAnnotationOrNull<LaAccess>()
        return ParsedPatchFunction(
            symbol = function,

            name = function.name,
            parameters = function.parameters.map { parsePatchFunctionParameter(function, it) },
            returnType = function.getReturnTypeOrNull(),
            isPublic = function.isPublic(),
            isAbstract = function.isAbstract,
            isExtension = function.isExtension(),

            hasAccessAnnotation = accessAnnotation != null,
            accessName = accessAnnotation?.name?.ifEmpty { function.name },
            accessMemberKinds = JavaMemberKind.entries.filter {
                function.hasAnnotation(it.annotationClass)
            },

            hasStaticAnnotation = function.hasAnnotation<LaStatic>(),

            hasHookAnnotation = function.hasAnnotation<LaHook>(),
            hookDescriptorClassType = function.annotations
                .firstOrNull { it.isInstance<LaHook>() }
                ?.findClassArgument(LaHook::descriptor),
            hookKind = function.getAnnotationOrNull<LaHook>()?.kind,
        )
    }

    private fun parsePatchFunctionParameter(
        function: KSPFunction,
        parameter: KSPValueParameter,
    ): ParsedPatchFunctionParameter {
        val name = parameter.name?.asString()
        val type = parameter.type.resolve()

        val targetAnnotation = parameter.getAnnotationOrNull<LaTarget>()
        val targetDescriptorClassType = when {
            targetAnnotation != null -> type.declaration.castOrNull<KSPClass>()
            else -> null
        }
        val targetDescriptorGenericClassType = when {
            targetAnnotation != null -> type.getGenericTypeOrNull()?.declaration?.castOrNull<KSPClass>()
            else -> null
        }

        val contextAnnotation = parameter.getAnnotationOrNull<LaContext>()
        val contextDescriptorClassType = when {
            contextAnnotation != null -> type.declaration.castOrNull<KSPClass>()
            else -> null
        }
        val contextDescriptorGenericClassType = when {
            contextAnnotation != null -> type.getGenericTypeOrNull()?.declaration?.castOrNull<KSPClass>()
            else -> null
        }

        val literalAnnotation = parameter.getAnnotationOrNull<LaLiteral>()
        val literalType = when {
            literalAnnotation != null -> type
            else -> null
        }
        val literalTypeName = when {
            literalAnnotation != null -> {
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
            }

            else -> null
        }
        val literalValue = when {
            literalAnnotation != null -> {
                parameter.annotations
                    .firstOrNull { it.isInstance<LaLiteral>() }
                    ?.arguments
                    ?.find { it.name?.asString() == literalTypeName }
                    ?.value
                    ?.toString()
            }

            else -> null
        }

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
