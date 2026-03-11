package io.github.recrafter.lapis

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import io.github.recrafter.lapis.extensions.ksp.KSPAnnotated
import io.github.recrafter.lapis.extensions.ksp.KSPLogger
import io.github.recrafter.lapis.layers.Builtins
import io.github.recrafter.lapis.layers.generator.MixinGenerator
import io.github.recrafter.lapis.layers.lowering.IrMixin
import io.github.recrafter.lapis.layers.lowering.IrSchema
import io.github.recrafter.lapis.layers.lowering.MixinLowering
import io.github.recrafter.lapis.layers.parser.SymbolParser
import io.github.recrafter.lapis.layers.validator.FrontendValidator

class LapisProcessor(
    private val options: Options,
    private val codeGenerator: CodeGenerator,
    logger: KSPLogger,
) : SymbolProcessor {

    private val builtins: Builtins = Builtins(options.generatedPackageName, codeGenerator)
    private val frontendValidator: FrontendValidator = FrontendValidator(logger, options, builtins)
    private val mixinLowering: MixinLowering = MixinLowering(options, builtins)

    private val schemas: MutableMap<String, IrSchema> = mutableMapOf()
    private val mixins: MutableMap<String, IrMixin> = mutableMapOf()

    override fun process(resolver: Resolver): List<KSPAnnotated> {
        val parserResult = SymbolParser.parse(resolver)
        if (!builtins.isGenerated) {
            builtins.generate()
            return parserResult.symbols
        }
        val validatorResult = frontendValidator.validate(parserResult)
        val irResult = mixinLowering.lower(validatorResult)
        irResult.schemas.forEach { schemas[it.classType.qualifiedName] = it }
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
        val sortedSchemas = schemas.values.sortedBy { it.classType.qualifiedName }
        val sortedMixins = mixins.values.sortedBy { it.patchClassType.qualifiedName }
        if (sortedSchemas.isEmpty() && sortedMixins.isEmpty()) {
            return
        }
        MixinGenerator(options, builtins, codeGenerator).generate(sortedSchemas, sortedMixins)
    }
}
