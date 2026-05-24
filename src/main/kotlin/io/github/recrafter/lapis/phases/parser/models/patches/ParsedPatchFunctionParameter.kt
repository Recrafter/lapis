package io.github.recrafter.lapis.phases.parser.models.patches

import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.phases.parser.models.common.ParsedAnnotation
import io.github.recrafter.lapis.phases.parser.models.common.SymbolSource

class ParsedPatchFunctionParameter(
    override val symbol: KSNode,

    val name: String?,
    val type: KSType?,
    val typeArguments: List<KSType?>,
    val hasDefaultArgument: Boolean,

    val hasOriginAnnotation: Boolean,
    val hasCancelAnnotation: Boolean,
    val hasOrdinalAnnotation: Boolean,

    val hasParamAnnotation: Boolean,
    val explicitParamName: String?,

    val hasLocalAnnotation: Boolean,
    val explicitLocalName: String?,
    val explicitLocalOrdinal: Int?,

    val hasShareAnnotation: Boolean,
    val explicitShareKey: String?,
    val isShareExported: Boolean,

    val annotations: List<ParsedAnnotation>,
) : SymbolSource
