package io.github.recrafter.lapis

import com.google.devtools.ksp.processing.SymbolProcessor
import io.github.recrafter.lapis.extensions.ks.KSAnnotated
import io.github.recrafter.lapis.extensions.ksp.KSPCodeGenerator
import io.github.recrafter.lapis.extensions.ksp.KSPResolver
import io.github.recrafter.lapis.layers.generator.MixinGenerator
import io.github.recrafter.lapis.layers.builtins.Builtins
import io.github.recrafter.lapis.layers.lowering.MixinLowering
import io.github.recrafter.lapis.layers.lowering.models.IrMixin
import io.github.recrafter.lapis.layers.lowering.models.IrSchema
import io.github.recrafter.lapis.layers.parser.SymbolParser
import io.github.recrafter.lapis.layers.validator.FrontendValidator

class LapisProcessor(
    private val options: Options,
    private val codeGenerator: KSPCodeGenerator,
    private val logger: LapisLogger,
) : SymbolProcessor {

    private val builtins: Builtins = Builtins(options.generatedPackageName, codeGenerator)
    private val frontendValidator: FrontendValidator = FrontendValidator(logger, options, builtins)
    private val mixinLowering: MixinLowering = MixinLowering(options, builtins, logger)

    private val schemas: MutableMap<String, IrSchema> = mutableMapOf()
    private val mixins: MutableMap<String, IrMixin> = mutableMapOf()

    override fun process(resolver: KSPResolver): List<KSAnnotated> {
        val parser = SymbolParser(resolver, logger)
        if (!builtins.isExternalGenerated) {
            logger.setPhase(LapisPhase.BUILTINS)
            builtins.generateExternal()
            return parser.prepare().run { schemaClassDecls + patchClassDecls }
        }

        logger.setPhase(LapisPhase.PARSING)
        val parserResult = parser.parse()

        logger.setPhase(LapisPhase.VALIDATION)
        val validatorResult = frontendValidator.validate(parserResult)

        logger.setPhase(LapisPhase.TRANSFORMATION)
        val irResult = mixinLowering.lower(validatorResult)
        irResult.schemas.forEach { schemas[it.className.qualifiedName] = it }
        irResult.mixins.forEach { mixins[it.patchClassName.qualifiedName] = it }

        return emptyList()
    }

    override fun finish() {
        generate()
    }

    override fun onError() {
        generate()
    }

    private fun generate() {
        logger.setPhase(LapisPhase.GENERATION)
        val sortedSchemas = schemas.values.sortedBy { it.className.qualifiedName }
        val sortedMixins = mixins.values.sortedBy { it.patchClassName.qualifiedName }
        MixinGenerator(options, builtins, codeGenerator, logger).generate(sortedSchemas, sortedMixins)
        builtins.generateInternal()
    }
}
