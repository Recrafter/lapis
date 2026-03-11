package io.github.recrafter.lapis

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import io.github.recrafter.lapis.extensions.ksp.KSPAnnotated
import io.github.recrafter.lapis.extensions.ksp.KSPLogger
import io.github.recrafter.lapis.layers.Builtins
import io.github.recrafter.lapis.layers.generator.MixinGenerator
import io.github.recrafter.lapis.layers.lowering.IrDescriptor
import io.github.recrafter.lapis.layers.lowering.IrMixin
import io.github.recrafter.lapis.layers.lowering.MixinLowering
import io.github.recrafter.lapis.layers.parser.SymbolParser
import io.github.recrafter.lapis.layers.validator.BackendValidator
import io.github.recrafter.lapis.layers.validator.FrontendValidator

class LapisProcessor(
    private val options: Options,
    private val logger: KSPLogger,
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {

    private val builtins: Builtins = Builtins(options.generatedPackageName, codeGenerator)
    private val frontendValidator: FrontendValidator = FrontendValidator(logger, builtins)
    private val mixinLowering: MixinLowering = MixinLowering(options, builtins, logger)

    private val descriptors: MutableMap<String, IrDescriptor> = mutableMapOf()
    private val mixins: MutableMap<String, IrMixin> = mutableMapOf()

    override fun process(resolver: Resolver): List<KSPAnnotated> {
        val parserResult = SymbolParser.parse(resolver)
        if (!builtins.isGenerated) {
            builtins.generate()
            return parserResult.resolvedSymbols
        }
        val validatorResult = frontendValidator.validate(parserResult)
        val irResult = mixinLowering.lower(validatorResult)
        irResult.descriptors.forEach { descriptors[it.classType.qualifiedName] = it }
        irResult.mixins.forEach { mixins[it.patchClassType.qualifiedName] = it }
        return emptyList()
    }

    override fun finish() {
        generate()
    }

    override fun onError() {
        generate()
    }

    private fun generate() {
        val sortedDescriptors = descriptors.values.sortedBy { it.classType.qualifiedName }
        val sortedMixins = mixins.values.sortedBy { it.patchClassType.qualifiedName }
        if (sortedDescriptors.isEmpty() && sortedMixins.isEmpty()) {
            return
        }
        BackendValidator(options.jarFiles, logger).validate(sortedDescriptors, sortedMixins)
        MixinGenerator(options, builtins, codeGenerator).generate(sortedDescriptors, sortedMixins)
    }
}
