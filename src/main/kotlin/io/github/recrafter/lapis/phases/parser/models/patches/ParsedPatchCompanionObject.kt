package io.github.recrafter.lapis.phases.parser.models.patches

import com.google.devtools.ksp.symbol.KSNode
import io.github.recrafter.lapis.phases.parser.models.common.SymbolSource

class ParsedPatchCompanionObject(
    override val symbol: KSNode,

    val isPublic: Boolean,
    val functions: List<ParsedPatchFunction>,
) : SymbolSource
