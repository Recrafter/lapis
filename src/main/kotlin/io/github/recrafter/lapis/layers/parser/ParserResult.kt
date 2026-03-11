package io.github.recrafter.lapis.layers.parser

import io.github.recrafter.lapis.Hook
import io.github.recrafter.lapis.Side
import io.github.recrafter.lapis.annotations.LaHook
import io.github.recrafter.lapis.annotations.LaPatch
import io.github.recrafter.lapis.extensions.ksp.KSPAnnotated
import io.github.recrafter.lapis.extensions.ksp.KSPClass
import io.github.recrafter.lapis.extensions.ksp.KSPSymbol
import io.github.recrafter.lapis.extensions.ksp.KSPType
import io.github.recrafter.lapis.layers.JavaMemberKind
import io.github.recrafter.lapis.layers.validator.KSPSource

class ParserResult(
    val schemas: List<ParsedSchema>,
    val patches: List<ParsedPatch>,
) {
    val symbols: List<KSPAnnotated>
        get() = schemas.map { it.symbol } + patches.map { it.symbol }
}

class ParsedSchema(
    override val symbol: KSPAnnotated,

    val classType: KSPClass?,
    val targetClassType: KSPClass?,
    val widener: String?,
    val isMarkedAsFinal: Boolean,
    val descriptors: List<ParsedDescriptor>,
    val nestedSchemas: List<ParsedSchema>,
) : KSPSource(symbol)

class ParsedFunctionTypeParameter(
    val type: KSPType,
    val name: String?,
)

class ParsedDescriptor(
    symbol: KSPSymbol,

    val name: String?,
    val classType: KSPClass?,
    val memberKinds: List<JavaMemberKind>,
    val hasStaticAnnotation: Boolean,
    val hasAccessAnnotation: Boolean,
    val isMarkedAsFinal: Boolean,

    val isFunctionType: Boolean,
    val hasReceiver: Boolean,
    val functionTypeReceiverName: String?,
    val receiverType: KSPType?,
    val parameters: List<ParsedFunctionTypeParameter>,
    val returnType: KSPType?,

    val isCallable: Boolean,
    val callableReceiverName: String?,
    val callableName: String?,

    val superClassType: KSPClass?,
) : KSPSource(symbol)

class ParsedPatch(
    override val symbol: KSPAnnotated,

    val name: String?,
    val side: Side?,
    val classType: KSPClass?,

    val superClassType: KSPClass?,
    val superGenericClassType: KSPClass?,

    val targetClassType: KSPClass?,

    val properties: List<ParsedPatchProperty>,
    val functions: List<ParsedPatchFunction>,
) : KSPSource(symbol)

class ParsedPatchProperty(
    symbol: KSPSymbol,

    val name: String,
    val type: KSPType,
    isPublic: Boolean,
    isAbstract: Boolean,
    isExtension: Boolean,
    val isMutable: Boolean,
) : KSPSource(symbol) {
    val isShared: Boolean = isPublic && !isAbstract && !isExtension
}

class ParsedPatchFunction(
    symbol: KSPSymbol,

    val name: String,
    val parameters: List<ParsedPatchFunctionParameter>,
    val returnType: KSPType?,
    val hasTypeParameters: Boolean,

    isPublic: Boolean,
    isAbstract: Boolean,
    isExtension: Boolean,

    val hasHookAnnotation: Boolean,
    val hookDescriptorClassType: KSPClass?,
    val hookKind: Hook?,
) : KSPSource(symbol) {
    val isShared: Boolean = isPublic && !isAbstract && !isExtension
}

class ParsedPatchFunctionParameter(
    symbol: KSPSymbol,

    val name: String?,
    val type: KSPType?,

    val hasTargetAnnotation: Boolean,
    val targetDescriptorClassType: KSPClass?,
    val targetDescriptorGenericClassType: KSPClass?,

    val hasContextAnnotation: Boolean,
    val contextDescriptorClassType: KSPClass?,
    val contextDescriptorGenericClassType: KSPClass?,

    val hasLiteralAnnotation: Boolean,
    val literalType: KSPType?,
    val literalTypeName: String?,
    val literalValue: String?,

    val hasOrdinalAnnotation: Boolean,
    val ordinalIndices: List<Int>,

    val hasLocalAnnotation: Boolean,
    val localOrdinal: Int?,
) : KSPSource(symbol)
