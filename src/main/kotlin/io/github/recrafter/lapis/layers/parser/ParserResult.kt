package io.github.recrafter.lapis.layers.parser

import io.github.recrafter.lapis.annotations.enums.LapisHookKind
import io.github.recrafter.lapis.annotations.enums.LapisPatchSide
import io.github.recrafter.lapis.extensions.ksp.KSPAnnotated
import io.github.recrafter.lapis.extensions.ksp.KSPClass
import io.github.recrafter.lapis.extensions.ksp.KSPSymbol
import io.github.recrafter.lapis.extensions.ksp.KSPType
import io.github.recrafter.lapis.layers.JavaMemberKind
import io.github.recrafter.lapis.layers.validator.KSPSource

class ParserResult(
    val descriptorContainers: List<ParsedDescriptorContainer>,
    val patches: List<ParsedPatch>,
) {
    val resolvedSymbols: List<KSPAnnotated>
        get() = descriptorContainers.map { it.symbol } + patches.map { it.symbol }
}

class ParsedDescriptorContainer(
    override val symbol: KSPAnnotated,

    val classType: KSPClass?,
    val targetClassType: KSPClass?,
    val descriptors: List<ParsedDescriptor>,
) : KSPSource(symbol)

class ParsedFunctionTypeParameter(
    val type: KSPType,
    val name: String?,
)

class ParsedDescriptor(
    symbol: KSPSymbol,

    val classType: KSPClass?,
    val targetClassType: KSPClass?,
    val memberKinds: List<JavaMemberKind>,
    val hasStaticAnnotation: Boolean,

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
    val side: LapisPatchSide?,
    val widener: String?,
    val classType: KSPClass?,

    val hasOuter: Boolean,
    val hasOuterAnnotation: Boolean,
    val outerWidener: String?,
    val outerClassType: KSPClass?,

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
    val isPublic: Boolean,
    val isAbstract: Boolean,
    val isExtension: Boolean,

    val hasAccessAnnotation: Boolean,
    val accessName: String?,
    val hasFieldAnnotation: Boolean,

    val hasStaticAnnotation: Boolean,
    val isMutable: Boolean,
    val isSetterPublic: Boolean,
) : KSPSource(symbol)

class ParsedPatchFunction(
    symbol: KSPSymbol,

    val name: String,
    val parameters: List<ParsedPatchFunctionParameter>,
    val returnType: KSPType?,

    val isPublic: Boolean,
    val isAbstract: Boolean,
    val isExtension: Boolean,

    val hasAccessAnnotation: Boolean,
    val accessName: String?,
    val accessMemberKinds: List<JavaMemberKind>,

    val hasStaticAnnotation: Boolean,

    val hasHookAnnotation: Boolean,
    val hookDescriptorClassType: KSPClass?,
    val hookKind: LapisHookKind?,
) : KSPSource(symbol)

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
