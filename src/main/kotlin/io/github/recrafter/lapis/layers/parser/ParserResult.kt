package io.github.recrafter.lapis.layers.parser

import io.github.recrafter.lapis.annotations.*
import io.github.recrafter.lapis.extensions.ksp.*
import io.github.recrafter.lapis.layers.JavaMemberKind
import io.github.recrafter.lapis.layers.validator.KSPSource
import kotlin.reflect.KClass

class ParserResult(
    val schemas: List<ParsedSchema>,
    val patches: List<ParsedPatch>,
)

class ParsedSchema(
    override val source: KSPAnnotated,

    val classDecl: KSPClassDecl?,
    val targetClassDecl: KSPClassDecl?,
    val access: String?,
    val hasAccess: Boolean,
    val isMarkedAsFinal: Boolean,
    val descriptors: List<ParsedDesc>,
    val nestedSchemas: List<ParsedSchema>,
) : KSPSource(source)

class ParsedDesc(
    source: KSPSymbol,

    val name: String?,
    val classDecl: KSPClassDecl?,
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

    val callableReference: ParsedCallableReference?,

    val superClassDecl: KSPClassDecl?,
) : KSPSource(source)

class ParsedCallableReference(
    val left: String?,
    val right: String?,
)

class ParsedPatch(
    override val source: KSPAnnotated,

    val name: String?,
    val side: Side?,
    val classDecl: KSPClassDecl?,

    val superClassDecl: KSPClassDecl?,
    val superGenericClassDecl: KSPClassDecl?,

    val schemaClassDecl: KSPClassDecl?,

    val properties: List<ParsedPatchProperty>,
    val functions: List<ParsedPatchFunction>,
) : KSPSource(source)

class ParsedPatchProperty(
    source: KSPSymbol,

    val name: String,
    val type: KSPType,
    isPublic: Boolean,
    isAbstract: Boolean,
    isExtension: Boolean,
    val isMutable: Boolean,
) : KSPSource(source) {
    val isShared: Boolean = isPublic && !isAbstract && !isExtension
}

class ParsedPatchFunction(
    source: KSPSymbol,

    val name: String,
    val parameters: List<ParsedPatchFunctionParameter>,
    val returnType: KSPType?,
    val hasTypeParameters: Boolean,

    isPublic: Boolean,
    isAbstract: Boolean,
    isExtension: Boolean,

    val hasHookAnnotation: Boolean,
    val hookDescClassDecl: KSPClassDecl?,
    val hookAt: Hook.At?,

    val hasAtConstructorHeadAnnotation: Boolean,
    val atConstructorHeadPhase: AtConstructorHead.Phase?,

    val hasAtLiteralAnnotation: Boolean,
    val atLiteralArguments: List<ParsedAnnotationArgumentVariant>,
    val atLiteralZeroConditions: List<Zero.Condition>,
    val atLiteralInt: Int?,
    val atLiteralFloat: Float?,
    val atLiteralLong: Long?,
    val atLiteralDouble: Double?,
    val atLiteralString: String?,
    val atLiteralClass: KClass<*>?,
    val atLiteralOrdinals: List<Int>,

    val hasAtLocalAnnotation: Boolean,
    val atLocalOp: AtLocal.Op?,
    val atLocalType: KSPClassDecl?,
    val atLocalOrdinal: Int?,

    val hasAtInstanceofAnnotation: Boolean,
    val atInstanceofType: KSPClassDecl?,
    val atInstanceofOrdinals: List<Int>,

    val hasAtReturnAnnotation: Boolean,
    val atReturnLast: Boolean,
    val atReturnOrdinals: List<Int>,

    val hasAtFieldAnnotation: Boolean,
    val atFieldOp: AtField.Op?,
    val atFieldDescClassDecl: KSPClassDecl?,
    val atFieldOrdinals: List<Int>,

    val hasAtArrayAnnotation: Boolean,
    val atArrayOp: AtArray.Op?,
    val atArrayDescClassDecl: KSPClassDecl?,
    val atArrayOrdinals: List<Int>,

    val hasAtCallAnnotation: Boolean,
    val atCallDescClassDecl: KSPClassDecl?,
    val atCallOrdinals: List<Int>,
) : KSPSource(source) {
    val isShared: Boolean = isPublic && !isAbstract && !isExtension
}

data class ParsedAnnotationArgumentVariant(
    val name: String?,
    val type: KSPType?,
    val value: Any?,
)

class ParsedPatchFunctionParameter(
    source: KSPSymbol,

    val name: String?,
    val type: KSPType?,

    val hasOriginAnnotation: Boolean,
    val originGenericClassDecl: KSPClassDecl?,

    val hasCancelAnnotation: Boolean,
    val cancelGenericClassDecl: KSPClassDecl?,

    val hasParamAnnotation: Boolean,
    val paramName: String?,

    val hasLocalAnnotation: Boolean,
    val localOrdinal: Int?,

    val hasShareAnnotation: Boolean,
    val shareKey: String?,
    val isShareExported: Boolean,
) : KSPSource(source)

class ParsedFunctionTypeParameter(
    val type: KSPType,
    val name: String?,
)
