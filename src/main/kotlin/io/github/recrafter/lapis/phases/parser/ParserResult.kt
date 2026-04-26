package io.github.recrafter.lapis.phases.parser

import com.google.devtools.ksp.symbol.*
import io.github.recrafter.lapis.annotations.*

class ParserPrepareResult(
    val schemaClassDeclarations: List<KSClassDeclaration>,
    val patchClassDeclarations: List<KSClassDeclaration>,
)

class ParserResult(
    val schemas: List<ParsedSchema>,
    val patches: List<ParsedPatch>,
)

class ParsedSchema(
    override val symbol: KSAnnotated,

    val classDeclaration: KSClassDeclaration?,
    val originClassDeclaration: KSClassDeclaration?,
    val originBinaryName: String?,
    val hasSchemaAnnotation: Boolean,
    val hasLocalSchemaAnnotation: Boolean,
    val hasAnonymousSchemaAnnotation: Boolean,
    val hasAccessAnnotation: Boolean,
    val unfinal: Boolean,
    val descriptors: List<ParsedDescriptor>,
    val nestedSchemas: List<ParsedSchema>,
) : SymbolSource(symbol)

class ParsedDescriptor(
    symbol: KSNode,

    val name: String,
    val classDeclaration: KSClassDeclaration,
    val hasStaticAnnotation: Boolean,
    val hasAccessAnnotation: Boolean,
    val hasBytecodeNameAnnotation: Boolean,
    val bytecodeName: String?,
    val unfinal: Boolean,
    val superClassDeclaration: KSClassDeclaration?,
    val genericType: ParsedDescriptorGenericType?,
    val callableReference: ParsedDescriptorCallableReference?,
) : SymbolSource(symbol)

sealed interface ParsedDescriptorGenericType
class ParsedFunctionTypeDescriptorGenericType(
    val receiverType: KSType?,
    val parameters: List<ParsedParameter>,
    val returnType: KSType?,
) : ParsedDescriptorGenericType

class ParsedTypeDescriptorGenericType(
    val type: KSType?,
    val arrayComponentType: KSType?,
) : ParsedDescriptorGenericType

sealed class ParsedDescriptorCallableReference(val name: String?)
class ParsedFieldDescriptorCallableReference(
    val receiverClassDeclaration: KSClassDeclaration?,
    name: String?,
) : ParsedDescriptorCallableReference(name)

class ParsedMethodDescriptorCallableReference(
    val receiverClassDeclaration: KSClassDeclaration?,
    name: String?,
) : ParsedDescriptorCallableReference(name)

class InvisibleCallableReference(name: String?) : ParsedDescriptorCallableReference(name)

class ParsedPatch(
    override val symbol: KSAnnotated,

    val name: String?,
    val side: Side?,
    val classDeclaration: KSClassDeclaration?,
    val schemaClassDeclaration: KSClassDeclaration?,

    val constructors: List<ParsedPatchConstructor>,
    val properties: List<ParsedPatchProperty>,
    val functions: List<ParsedPatchFunction>,
) : SymbolSource(symbol)

class ParsedPatchConstructor(
    symbol: KSNode,

    val parameters: List<ParsedPatchConstructorParameter>,
) : SymbolSource(symbol)

class ParsedPatchConstructorParameter(
    symbol: KSNode,

    val type: KSType,
    val hasOriginAnnotation: Boolean,
) : SymbolSource(symbol)

class ParsedPatchProperty(
    symbol: KSNode,

    val name: String,
    val type: KSType,
    isPublic: Boolean,
    isAbstract: Boolean,
    isExtension: Boolean,
    val isMutable: Boolean,
) : SymbolSource(symbol) {
    val isShared: Boolean = isPublic && !isAbstract && !isExtension
}

class ParsedPatchFunction(
    symbol: KSNode,

    val name: String,
    val parameters: List<ParsedPatchFunctionParameter>,
    val returnType: KSType?,
    val hasTypeParameters: Boolean,

    isPublic: Boolean,
    isAbstract: Boolean,
    isExtension: Boolean,

    val fromCompanionObject: Boolean,

    val hasHookAnnotation: Boolean,
    val hookDescriptorClassDeclaration: KSClassDeclaration?,
    val hookAt: At?,

    val hasAtConstructorHeadAnnotation: Boolean,
    val atConstructorHeadPhase: ConstructorHeadPhase?,

    val hasAtLocalAnnotation: Boolean,
    val atLocalOp: Op?,
    val atLocalType: KSType?,
    val atLocalExplicitName: String?,
    val atLocalExplicitOrdinal: Int?,
    val atLocalOpOrdinals: List<Int>,

    val hasAtInstanceofAnnotation: Boolean,
    val atInstanceofTypeClassDeclaration: KSClassDeclaration?,
    val atInstanceofOrdinals: List<Int>,

    val hasAtReturnAnnotation: Boolean,
    val atReturnOrdinals: List<Int>,

    val hasAtLiteralAnnotation: Boolean,
    val atLiteralExplicitZero: KSAnnotation?,
    val atLiteralZeroConditions: List<ZeroCondition>,
    val atLiteralExplicitInt: Int?,
    val atLiteralExplicitFloat: Float?,
    val atLiteralExplicitLong: Long?,
    val atLiteralExplicitDouble: Double?,
    val atLiteralExplicitString: String?,
    val atLiteralExplicitClassType: KSType?,
    val atLiteralExplicitClassDeclaration: KSClassDeclaration?,
    val atLiteralExplicitNull: KSAnnotation?,
    val atLiteralOrdinals: List<Int>,

    val hasAtFieldAnnotation: Boolean,
    val atFieldOp: Op?,
    val atFieldDescriptorClassDeclaration: KSClassDeclaration?,
    val atFieldOrdinals: List<Int>,

    val hasAtArrayAnnotation: Boolean,
    val atArrayOp: Op?,
    val atArrayDescriptorClassDeclaration: KSClassDeclaration?,
    val atArrayOrdinals: List<Int>,

    val hasAtCallAnnotation: Boolean,
    val atCallDescriptorClassDeclaration: KSClassDeclaration?,
    val atCallOrdinals: List<Int>,
) : SymbolSource(symbol) {

    val isShared: Boolean = isPublic && !isAbstract && !isExtension && !fromCompanionObject

    fun hasOrdinals(): Boolean {
        val allOrdinals = atLocalOpOrdinals + atInstanceofOrdinals + atReturnOrdinals + atLiteralOrdinals +
            atFieldOrdinals + atArrayOrdinals + atCallOrdinals
        return allOrdinals.isNotEmpty()
    }
}

class ParsedPatchFunctionParameter(
    symbol: KSNode,

    val name: String?,
    val type: KSType?,
    val hasDefaultArgument: Boolean,

    val hasOriginAnnotation: Boolean,
    val originGenericTypeClassDeclaration: KSClassDeclaration?,

    val hasCancelAnnotation: Boolean,
    val cancelGenericTypeClassDeclaration: KSClassDeclaration?,

    val hasOrdinalAnnotation: Boolean,

    val hasParamAnnotation: Boolean,
    val explicitParamName: String?,

    val hasLocalAnnotation: Boolean,
    val explicitLocalName: String?,
    val explicitLocalOrdinal: Int?,

    val hasShareAnnotation: Boolean,
    val explicitShareKey: String?,
    val isShareExported: Boolean,
) : SymbolSource(symbol)

class ParsedParameter(
    val type: KSType,
    val name: String?,
)
