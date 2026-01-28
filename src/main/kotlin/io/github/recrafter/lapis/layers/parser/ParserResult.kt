package io.github.recrafter.lapis.layers.parser

import io.github.recrafter.lapis.annotations.enums.LapisHookKind
import io.github.recrafter.lapis.annotations.enums.LapisPatchSide
import io.github.recrafter.lapis.api.LapisCanceler
import io.github.recrafter.lapis.api.LapisReturner
import io.github.recrafter.lapis.extensions.ksp.KspClassDeclaration
import io.github.recrafter.lapis.extensions.ksp.KspSymbol
import io.github.recrafter.lapis.extensions.ksp.KspType
import io.github.recrafter.lapis.layers.validator.KspSourceHolder
import io.github.recrafter.lapis.utils.MemberKind
import kotlin.reflect.KClass

class ParserResult(
    val descriptorContainers: List<ParsedDescriptorContainer>,
    val patches: List<ParsedPatch>,
)

class ParsedDescriptorContainer(
    override val source: KspSymbol,

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
    override val source: KspSymbol,

    val name: String?,
    val side: LapisPatchSide?,
    val widener: String?,
    val classDeclaration: KspClassDeclaration?,

    val hasOuter: Boolean,
    val hasOuterAnnotation: Boolean,
    val outerWidener: String?,
    val outerClassDeclaration: KspClassDeclaration?,

    val superClassDeclaration: KspClassDeclaration?,
    val superClassGenericDeclaration: KspClassDeclaration?,

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
    val accessVanillaName: String?,
    val hasFieldAnnotation: Boolean,

    val hasStaticAnnotation: Boolean,
    val isMutable: Boolean,
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
    val accessVanillaName: String?,
    val accessMemberKinds: List<MemberKind>,

    val hasStaticAnnotation: Boolean,

    val hasHookAnnotation: Boolean,
    val hookMethodDescriptorClassDeclaration: KspClassDeclaration?,
    val hookKind: LapisHookKind?,
) : KspSourceHolder()

class ParsedPatchFunctionParameter(
    override val source: KspSymbol,

    val name: String?,
    val type: KspType?,

    val hasTargetAnnotation: Boolean,
    val targetDescriptorClassDeclaration: KspClassDeclaration?,

    val hasLiteralAnnotation: Boolean,
    val literalType: KspType?,
    val literalTypeName: String?,
    val literalValue: String?,

    val hasOrdinalAnnotation: Boolean,
    val ordinals: List<Int>,

    val hasReturnAnnotation: Boolean,
    val returnKind: ParsedReturnKind?,

    val hasParameterAnnotation: Boolean,
    val parameterName: String?,

    val hasLocalAnnotation: Boolean,
    val localName: String?,
    val localIndex: Int?,
) : KspSourceHolder()

enum class ParsedReturnKind(val typeClass: KClass<*>) {
    CANCELER(LapisCanceler::class),
    RETURNER(LapisReturner::class),
}
