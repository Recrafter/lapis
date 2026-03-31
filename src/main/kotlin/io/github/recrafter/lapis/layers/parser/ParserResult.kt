package io.github.recrafter.lapis.layers.parser

import io.github.recrafter.lapis.annotations.*
import io.github.recrafter.lapis.extensions.ks.KSAnnotated
import io.github.recrafter.lapis.extensions.ks.KSClassDecl
import io.github.recrafter.lapis.extensions.ks.KSSymbol
import io.github.recrafter.lapis.extensions.ks.KSType

class ParserPrepareResult(
    val schemaClassDecls: List<KSClassDecl>,
    val patchClassDecls: List<KSClassDecl>,
)

class ParserResult(
    val schemas: List<ParsedSchema>,
    val patches: List<ParsedPatch>,
)

class ParsedSchema(
    override val symbol: KSAnnotated,

    val classDecl: KSClassDecl?,
    val targetClassDecl: KSClassDecl?,
    val targetBinaryName: String?,
    val hasAccess: Boolean,
    val isMarkedAsFinal: Boolean,
    val descriptors: List<ParsedDesc>,
    val nestedSchemas: List<ParsedSchema>,
) : SymbolSource(symbol)

class ParsedDesc(
    symbol: KSSymbol,

    val name: String?,
    val classDecl: KSClassDecl,
    val hasStaticAnnotation: Boolean,
    val hasAccessAnnotation: Boolean,
    val isMarkedAsFinal: Boolean,
    val superClassDecl: KSClassDecl?,
    val generic: ParsedDescGeneric?,
    val callable: ParsedDescCallable?,
) : SymbolSource(symbol)

sealed interface ParsedDescGeneric

class ParsedFunctionTypeDescGeneric(
    val receiverType: KSType?,
    val parameters: List<ParsedParameter>,
    val returnType: KSType?,
) : ParsedDescGeneric

class ParsedTypeDescGeneric(
    val type: KSType?,
    val arrayComponentType: KSType?,
) : ParsedDescGeneric

sealed class ParsedDescCallable(
    val receiverClassDecl: KSClassDecl?,
)

class ParsedFieldDescCallable(
    receiverClassDecl: KSClassDecl?,
    val name: String?,
    val type: KSType?,
) : ParsedDescCallable(receiverClassDecl)

class ParsedMethodDescCallable(
    receiverClassDecl: KSClassDecl?,
    val name: String?,
    val parameters: List<ParsedParameter>,
    val returnType: KSType?,
) : ParsedDescCallable(receiverClassDecl)

class ParsedConstructorDescCallable(
    receiverClassDecl: KSClassDecl?,
    val parameters: List<ParsedParameter>,
    val returnType: KSType?,
) : ParsedDescCallable(receiverClassDecl)

class ParsedPatch(
    override val symbol: KSAnnotated,

    val name: String?,
    val side: Side?,
    val classDecl: KSClassDecl?,

    val superClassDecl: KSClassDecl?,
    val superGenericClassDecl: KSClassDecl?,

    val schemaClassDecl: KSClassDecl?,

    val properties: List<ParsedPatchProperty>,
    val functions: List<ParsedPatchFunction>,
) : SymbolSource(symbol)

class ParsedPatchProperty(
    symbol: KSSymbol,

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
    symbol: KSSymbol,

    val name: String,
    val parameters: List<ParsedPatchFunctionParameter>,
    val returnType: KSType?,
    val hasTypeParameters: Boolean,

    isPublic: Boolean,
    isAbstract: Boolean,
    isExtension: Boolean,

    val fromCompanionObject: Boolean,

    val hasHookAnnotation: Boolean,
    val hookDescClassDecl: KSClassDecl?,
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
    val atInstanceofType: KSClassDecl?,
    val atInstanceofOrdinals: List<Int>,

    val hasAtReturnAnnotation: Boolean,
    val atReturnOrdinals: List<Int>,

    val hasAtLiteralAnnotation: Boolean,
    val atLiteralArguments: List<ParsedAnnotationArgumentVariant>,
    val atLiteralZeroConditions: List<ZeroCondition>,
    val atLiteralInt: Int?,
    val atLiteralFloat: Float?,
    val atLiteralLong: Long?,
    val atLiteralDouble: Double?,
    val atLiteralString: String?,
    val atLiteralOrdinals: List<Int>,

    val hasAtFieldAnnotation: Boolean,
    val atFieldOp: Op?,
    val atFieldDescClassDecl: KSClassDecl?,
    val atFieldOrdinals: List<Int>,

    val hasAtArrayAnnotation: Boolean,
    val atArrayOp: Op?,
    val atArrayDescClassDecl: KSClassDecl?,
    val atArrayOrdinals: List<Int>,

    val hasAtCallAnnotation: Boolean,
    val atCallDescClassDecl: KSClassDecl?,
    val atCallOrdinals: List<Int>,
) : SymbolSource(symbol) {

    val isShared: Boolean = isPublic && !isAbstract && !isExtension

    fun hasOrdinals(): Boolean {
        val allOrdinals = atLocalOpOrdinals + atInstanceofOrdinals + atReturnOrdinals + atLiteralOrdinals +
            atFieldOrdinals + atArrayOrdinals + atCallOrdinals
        return allOrdinals.isNotEmpty()
    }
}

data class ParsedAnnotationArgumentVariant(
    val name: String?,
    val type: KSType?,
    val value: Any?,
)

class ParsedPatchFunctionParameter(
    symbol: KSSymbol,

    val name: String?,
    val type: KSType?,
    val hasDefaultArgument: Boolean,

    val hasOriginAnnotation: Boolean,
    val originGenericClassDecl: KSClassDecl?,

    val hasCancelAnnotation: Boolean,
    val cancelGenericClassDecl: KSClassDecl?,

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
