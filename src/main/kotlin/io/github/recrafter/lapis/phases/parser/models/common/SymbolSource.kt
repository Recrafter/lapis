package io.github.recrafter.lapis.phases.parser.models.common

import com.google.devtools.ksp.symbol.KSNode

interface SymbolSource {
    val symbol: KSNode
}
