package io.github.recrafter.lapis.phases.parser.models.schemas

import com.google.devtools.ksp.symbol.KSType

sealed interface ParsedDescriptorGenericArgument

class ParsedDescriptorGenericArgumentSimpleType(
    val type: KSType?,
    val typeArguments: List<KSType?>,
) : ParsedDescriptorGenericArgument

class ParsedDescriptorGenericArgumentFunctionType(
    val receiverType: KSType?,
    val parameters: List<ParsedFunctionTypeParameter>,
    val returnType: KSType?,
) : ParsedDescriptorGenericArgument

class ParsedFunctionTypeParameter(
    val type: KSType?,
    val name: String?,
)
