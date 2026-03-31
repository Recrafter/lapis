package io.github.recrafter.lapis

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import io.github.recrafter.lapis.extensions.elements
import io.github.recrafter.lapis.extensions.quoted
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@AutoService(SymbolProcessorProvider::class)
class LapisProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val logger = LapisLogger(environment.logger)
        return LapisProcessor(
            parseOptions(environment.options, logger),
            environment.codeGenerator,
            logger,
        )
    }

    private fun parseOptions(options: Map<String, String>, logger: LapisLogger): Options {
        val processorOptions = options
            .filterKeys { it.startsWith(ARGUMENT_PREFIX) }
            .mapKeys { it.key.removeArgumentPrefix() }

        val descriptorElements = serialDescriptor<Options>().elements
        val existingOptions = descriptorElements.map { it.name }.toSet()

        val unknownKeys = processorOptions.keys - existingOptions
        if (unknownKeys.isNotEmpty()) {
            logger.fatal(
                buildString {
                    append("Unknown options: ${unknownKeys.joinToString { it.withArgumentPrefix() }}.")
                    appendLine()
                    append("Existing options: ${existingOptions.joinToString { it.withArgumentPrefix() }}.")
                }
            )
        }

        val requiredKeys = descriptorElements.filter { !it.isOptional }.map { it.name }.toSet()
        val missingKeys = requiredKeys - processorOptions.keys
        if (missingKeys.isNotEmpty()) {
            logger.fatal(
                buildString {
                    append("Missing options: ${missingKeys.joinToString { it.withArgumentPrefix() }}.")
                    appendLine()
                    append("Required options: ${requiredKeys.joinToString { it.withArgumentPrefix() }}.")
                }
            )
        }

        return Json.decodeFromJsonElement(
            buildJsonObject {
                processorOptions.forEach { (key, value) ->
                    put(key, JsonPrimitive(value))
                }
            }
        )
    }

    private fun String.withArgumentPrefix(): String =
        (ARGUMENT_PREFIX + this).quoted()

    private fun String.removeArgumentPrefix(): String =
        removePrefix(ARGUMENT_PREFIX)

    companion object {
        private val ARGUMENT_PREFIX: String = LapisMeta.NAME.lowercase() + "."
    }
}
