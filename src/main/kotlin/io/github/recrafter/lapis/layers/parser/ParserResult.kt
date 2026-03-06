package io.github.recrafter.lapis.layers.parser

import io.github.recrafter.lapis.annotations.enums.LapisHookKind
import io.github.recrafter.lapis.annotations.enums.LapisPatchSide
import io.github.recrafter.lapis.extensions.ksp.KspAnnotated
import io.github.recrafter.lapis.extensions.ksp.KspClassDeclaration
import io.github.recrafter.lapis.extensions.ksp.KspSymbol
import io.github.recrafter.lapis.extensions.ksp.KspType
import io.github.recrafter.lapis.layers.MemberKind
import io.github.recrafter.lapis.layers.validator.KspSourceHolder

class ParserResult(
    val descriptorContainers: List<ParsedDescriptorContainer>,
    val patches: List<ParsedPatch>,
)

class ParsedDescriptorContainer(
    override val source: KspAnnotated,

    val classDeclaration: KspClassDeclaration?,
    val targetClassDeclaration: KspClassDeclaration?,
    val descriptors: List<ParsedDescriptor>,
) : KspSourceHolder()

class ParsedFunctionTypeParameter(
    val type: KspType,
    val name: String?,
)

class ParsedDescriptor(
    override val source: KspSymbol,

    val classDeclaration: KspClassDeclaration?,
    val targetClassDeclaration: KspClassDeclaration?,
    val memberKinds: List<MemberKind>,
    val hasStaticAnnotation: Boolean,

    val isFunctionType: Boolean,
    val hasReceiver: Boolean,
    val functionTypeReceiverName: String?,
    val receiverType: KspType?,
    val parameters: List<ParsedFunctionTypeParameter>,
    val returnType: KspType?,

    val isCallable: Boolean,
    val callableReceiverName: String?,
    val callableName: String?,

    val superClassDeclaration: KspClassDeclaration?,
) : KspSourceHolder()

class ParsedPatch(
    override val source: KspAnnotated,

    val name: String?,
    val side: LapisPatchSide?,
    val widener: String?,
    val classDeclaration: KspClassDeclaration?,

    val hasOuter: Boolean,
    val hasOuterAnnotation: Boolean,
    val outerWidener: String?,
    val outerClassDeclaration: KspClassDeclaration?,

    val superClassDeclaration: KspClassDeclaration?,
    val superGenericClassDeclaration: KspClassDeclaration?,

    val targetClassDeclaration: KspClassDeclaration?,

    val properties: List<ParsedPatchProperty>,
    val functions: List<ParsedPatchFunction>,
) : KspSourceHolder()

class ParsedPatchProperty(
    override val source: KspSymbol,

    val name: String,
    val type: KspType,
    val isPublic: Boolean,
    val isAbstract: Boolean,
    val isExtension: Boolean,

    val hasAccessAnnotation: Boolean,
    val accessName: String?,
    val hasFieldAnnotation: Boolean,

    val hasStaticAnnotation: Boolean,
    val isMutable: Boolean,
    val isSetterPublic: Boolean,
) : KspSourceHolder()

class ParsedPatchFunction(
    override val source: KspSymbol,

    val name: String,
    val parameters: List<ParsedPatchFunctionParameter>,
    val returnType: KspType?,

    val isPublic: Boolean,
    val isAbstract: Boolean,
    val isExtension: Boolean,

    val hasAccessAnnotation: Boolean,
    val accessName: String?,
    val accessMemberKinds: List<MemberKind>,

    val hasStaticAnnotation: Boolean,

    val hasHookAnnotation: Boolean,
    val hookDescriptorClassDeclaration: KspClassDeclaration?,
    val hookKind: LapisHookKind?,
) : KspSourceHolder()

class ParsedPatchFunctionParameter(
    override val source: KspSymbol,

    val name: String?,
    val type: KspType?,

    val hasContextAnnotation: Boolean,
    val contextDescriptorClassDeclaration: KspClassDeclaration?,
    val contextDescriptorGenericClassDeclaration: KspClassDeclaration?,

    val hasTargetAnnotation: Boolean,
    val targetDescriptorClassDeclaration: KspClassDeclaration?,

    val hasLiteralAnnotation: Boolean,
    val literalType: KspType?,
    val literalTypeName: String?,
    val literalValue: String?,

    val hasOrdinalAnnotation: Boolean,
    val ordinalIndices: List<Int>,

    val hasLocalAnnotation: Boolean,
    val localOrdinal: Int?,
) : KspSourceHolder()
