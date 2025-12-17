package io.github.recrafter.nametag.extensions

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun KSPLogger.kspRequire(condition: Boolean, symbol: KSNode? = null, crossinline message: () -> String) {
    contract {
        returns() implies condition
    }
    if (!condition) {
        kspError(symbol, message)
    }
}

inline fun KSPLogger.kspError(symbol: KSNode? = null, crossinline message: () -> String): Nothing {
    val message = message()
    error(message, symbol)
    throw IllegalStateException(message)
}
