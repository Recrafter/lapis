package io.github.recrafter.lapis

import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.ks.KSSymbol
import io.github.recrafter.lapis.extensions.ksp.KSPFileLocation
import io.github.recrafter.lapis.extensions.ksp.KSPLogger

class LapisLogger(private val logger: KSPLogger) {

    private var currentPhase: LapisPhase = LapisPhase.entries.first()

    fun info(message: String, symbol: KSSymbol? = null) {
        println(buildFullMessage(message, symbol))
    }

    fun warn(message: String, symbol: KSSymbol? = null) {
        logger.warn(buildFullMessage(message, symbol))
    }

    fun error(message: String, symbol: KSSymbol? = null) {
        logger.error(buildFullMessage(message, symbol))
    }

    fun fatal(
        message: String,
        symbol: KSSymbol? = null,
    ): Nothing {
        error(message, symbol)
        throw LapisException(message)
    }

    fun setPhase(phase: LapisPhase) {
        currentPhase = phase
    }

    private fun buildFullMessage(message: String, symbol: KSSymbol?): String = buildString {
        append("[${LapisMeta.NAME}] [Phase: $currentPhase]")
        symbol?.location?.castOrNull<KSPFileLocation>()?.let { location ->
            appendLine()
            append(location.ideaLink)
        }
        appendLine()
        append(message.trimEnd())
        appendLine()
    }
}

private val KSPFileLocation.ideaLink: String
    get() = "file://$filePath:$lineNumber"
