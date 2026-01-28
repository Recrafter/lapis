package io.github.recrafter.lapis

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import io.github.recrafter.lapis.extensions.elements
import io.github.recrafter.lapis.extensions.singleQuoted
import io.github.recrafter.lapis.options.Options
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@OptIn(ExperimentalSerializationApi::class)
@AutoService(SymbolProcessorProvider::class)
class LapisProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        LapisProcessor(
            parseOptions(environment.options),
            environment.logger,
            environment.codeGenerator,
        )

    private fun parseOptions(options: Map<String, String>): Options {
        val generatorOptions = options
            .filterKeys { it.startsWith(LAPIS_ARGUMENT_PREFIX) }
            .mapKeys { it.key.removeLapisArgumentPrefix() }

        val descriptorElements = serialDescriptor<Options>().elements
        val existingOptions = descriptorElements.map { it.name }.toSet()

        val unknownOptions = generatorOptions.keys - existingOptions
        require(unknownOptions.isEmpty()) {
            "Unknown Lapis options: ${unknownOptions.joinToString { it.withLapisArgumentPrefix().singleQuoted() }}. " +
                "Existing options: ${existingOptions.joinToString { it.withLapisArgumentPrefix().singleQuoted() }}."
        }

        val requiredOptions = descriptorElements.filter { !it.isOptional }.map { it.name }.toSet()
        val missingOptions = requiredOptions - generatorOptions.keys
        require(missingOptions.isEmpty()) {
            "Missing Lapis options: ${missingOptions.joinToString { it.withLapisArgumentPrefix().singleQuoted() }}. " +
                "Required options: ${requiredOptions.joinToString { it.withLapisArgumentPrefix().singleQuoted() }}."
        }

        return Json.decodeFromJsonElement(
            buildJsonObject {
                generatorOptions.forEach { (key, value) ->
                    put(key, JsonPrimitive(value))
                }
            }
        )
    }

    private fun String.withLapisArgumentPrefix(): String =
        LAPIS_ARGUMENT_PREFIX + this

    private fun String.removeLapisArgumentPrefix(): String =
        removePrefix(LAPIS_ARGUMENT_PREFIX)

    companion object {
        private const val LAPIS_ARGUMENT_PREFIX: String = "lapis."
    }
}
