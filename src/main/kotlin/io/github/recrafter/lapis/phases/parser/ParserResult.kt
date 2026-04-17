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
    val targetClassDeclaration: KSClassDeclaration?,
    val targetBinaryName: String?,
    val hasAccess: Boolean,
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
    val unfinal: Boolean,
    val superClassDeclaration: KSClassDeclaration?,
    val generic: ParsedDescriptorGeneric?,
    val callable: ParsedDescriptorCallable?,
) : SymbolSource(symbol)

sealed interface ParsedDescriptorGeneric

class ParsedFunctionTypeDescriptorGeneric(
    val receiverType: KSType?,
    val parameters: List<ParsedParameter>,
    val returnType: KSType?,
) : ParsedDescriptorGeneric

class ParsedTypeDescriptorGeneric(
    val type: KSType?,
    val arrayComponentType: KSType?,
) : ParsedDescriptorGeneric

sealed class ParsedDescriptorCallable(val name: String?)

class InvisibleCallableReference(name: String?) : ParsedDescriptorCallable(name)

class ParsedFieldDescriptorCallable(
    val receiverClassDeclaration: KSClassDeclaration?,
    name: String?,
    val type: KSType?,
) : ParsedDescriptorCallable(name)

class ParsedMethodDescriptorCallable(
    val receiverClassDeclaration: KSClassDeclaration?,
    name: String?,
    val parameters: List<ParsedParameter>,
    val returnType: KSType?,
) : ParsedDescriptorCallable(name)

class ParsedConstructorDescriptorCallable(
    val receiverClassDeclaration: KSClassDeclaration?,
    val parameters: List<ParsedParameter>,
    val returnType: KSType?,
) : ParsedDescriptorCallable(null)

class ParsedPatch(
    override val symbol: KSAnnotated,

    val name: String?,
    val side: Side?,
    val classDeclaration: KSClassDeclaration?,

    val superClassDeclaration: KSClassDeclaration?,
    val superGenericClassDeclaration: KSClassDeclaration?,

    val schemaClassDeclaration: KSClassDeclaration?,

    val properties: List<ParsedPatchProperty>,
    val functions: List<ParsedPatchFunction>,
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
    val atLocalName: String?,
    val atLocalOrdinal: Int?,
    val atLocalOpOrdinals: List<Int>,

    val hasAtInstanceofAnnotation: Boolean,
    val atInstanceofTypeClassDeclaration: KSClassDeclaration?,
    val atInstanceofOrdinals: List<Int>,

    val hasAtReturnAnnotation: Boolean,
    val atReturnOrdinals: List<Int>,

    val hasAtLiteralAnnotation: Boolean,
    val atLiteralZero: KSAnnotation?,
    val atLiteralZeroConditions: List<ZeroCondition>,
    val atLiteralInt: Int?,
    val atLiteralFloat: Float?,
    val atLiteralLong: Long?,
    val atLiteralDouble: Double?,
    val atLiteralString: String?,
    val atLiteralClass: KSType?,
    val atLiteralClassDeclaration: KSClassDeclaration?,
    val atLiteralNull: KSAnnotation?,
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
    val originGenericClassDeclaration: KSClassDeclaration?,

    val hasCancelAnnotation: Boolean,
    val cancelGenericClassDeclaration: KSClassDeclaration?,

    val hasOrdinalAnnotation: Boolean,

    val hasParamAnnotation: Boolean,
    val paramName: String?,

    val hasLocalAnnotation: Boolean,
    val localName: String?,
    val localOrdinal: Int?,

    val hasShareAnnotation: Boolean,
    val shareKey: String?,
    val isShareExported: Boolean,
) : SymbolSource(symbol)

class ParsedParameter(
    val type: KSType,
    val name: String?,
)
