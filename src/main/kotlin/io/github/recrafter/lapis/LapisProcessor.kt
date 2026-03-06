package io.github.recrafter.lapis

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import io.github.recrafter.lapis.extensions.ksp.KspAnnotated
import io.github.recrafter.lapis.extensions.ksp.KspLogger
import io.github.recrafter.lapis.layers.RuntimeApi
import io.github.recrafter.lapis.layers.generator.MixinGenerator
import io.github.recrafter.lapis.layers.lowering.IrDescriptor
import io.github.recrafter.lapis.layers.lowering.IrMixin
import io.github.recrafter.lapis.layers.lowering.MixinLowering
import io.github.recrafter.lapis.layers.parser.SymbolParser
import io.github.recrafter.lapis.layers.validator.BackendValidator
import io.github.recrafter.lapis.layers.validator.FrontendValidator
import io.github.recrafter.lapis.options.Options

class LapisProcessor(
    private val options: Options,
    private val logger: KspLogger,
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {

    private val runtimeApi: RuntimeApi = RuntimeApi(options.generatedPackageName, codeGenerator)
    private val symbolParser: SymbolParser = SymbolParser(logger)
    private val frontendValidator: FrontendValidator = FrontendValidator(logger, runtimeApi)
    private val mixinLowering: MixinLowering = MixinLowering(options, runtimeApi)

    private val descriptors = mutableMapOf<String, IrDescriptor>()
    private val mixins = mutableMapOf<String, IrMixin>()

    override fun process(resolver: Resolver): List<KspAnnotated> {
        val parsedData = symbolParser.parse(resolver)
        val validatedData = frontendValidator.validate(parsedData)

        val irResult = mixinLowering.lower(validatedData)
        irResult.descriptors.forEach { descriptors[it.targetImpl.type.qualifiedName] = it }
        irResult.mixins.forEach { mixins[it.type.qualifiedName] = it }
        if (validatedData.unresolvedSymbols.isNotEmpty()) {
            runtimeApi.generate()
        }
        return validatedData.unresolvedSymbols
    }

    override fun finish() {
        generate()
    }

    override fun onError() {
        generate()
    }

    private fun generate() {
        val finalDescriptors = descriptors.values.toList()
        val finalMixins = mixins.values.toList()
        if (finalDescriptors.isEmpty() && finalMixins.isEmpty()) {
            return
        }
        BackendValidator(options.minecraftJars, logger).validate(finalDescriptors, finalMixins)
        MixinGenerator(options, runtimeApi, codeGenerator).generate(finalDescriptors, finalMixins)
        runtimeApi.generate()
    }
}
