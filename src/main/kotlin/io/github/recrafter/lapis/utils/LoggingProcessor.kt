package io.github.recrafter.lapis.utils

import com.google.devtools.ksp.processing.SymbolProcessor
import io.github.recrafter.lapis.extensions.ksp.KspAnnotated
import io.github.recrafter.lapis.extensions.ksp.KspLogger
import io.github.recrafter.lapis.extensions.ksp.KspNode
import io.github.recrafter.lapis.extensions.ksp.KspResolver
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

abstract class LoggingProcessor(val logger: KspLogger) : SymbolProcessor {

    abstract fun run(resolver: KspResolver)

    fun resolvingError(node: KspNode): Nothing =
        kspError(node)

    @OptIn(ExperimentalContracts::class)
    inline fun kspRequire(
        condition: Boolean,
        symbol: KspNode,
        crossinline message: () -> String = { "TODO" },
    ) {
        contract {
            returns() implies condition
        }
        if (!condition) {
            logger.error(message(), symbol)
        }
    }

    inline fun kspError(
        symbol: KspNode,
        crossinline message: () -> String = { "TODO" },
    ): Nothing {
        val message = message()
        logger.error(message, symbol)
        throw IllegalStateException(message)
    }

    final override fun process(resolver: KspResolver): List<KspAnnotated> {
        run(resolver)
        return emptyList()
    }
}
