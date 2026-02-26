package io.github.recrafter.lapis

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import io.github.recrafter.lapis.extensions.ksp.KspAnnotated
import io.github.recrafter.lapis.extensions.ksp.KspLogger
import io.github.recrafter.lapis.layers.generator.MixinGenerator
import io.github.recrafter.lapis.layers.lowering.IrResult
import io.github.recrafter.lapis.layers.lowering.MixinLowering
import io.github.recrafter.lapis.layers.parser.SymbolParser
import io.github.recrafter.lapis.layers.validator.BackendValidator
import io.github.recrafter.lapis.layers.validator.FrontendValidator
import io.github.recrafter.lapis.options.Options
import io.github.recrafter.lapis.utils.PsiCompanion

class LapisProcessor(
    private val options: Options,
    private val logger: KspLogger,
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {

    private val psiCompanion: PsiCompanion = PsiCompanion()
    private val symbolParser: SymbolParser = SymbolParser(psiCompanion)
    private val frontendValidator: FrontendValidator = FrontendValidator(logger)
    private val mixinLowering: MixinLowering = MixinLowering(options)

    private val results: MutableList<IrResult> = mutableListOf()

    override fun process(resolver: Resolver): List<KspAnnotated> {
        val parsedData = symbolParser.parse(resolver)
        val validatedData = frontendValidator.validate(parsedData)
        results += mixinLowering.lower(validatedData)
        return emptyList()
    }

    override fun finish() {
        val descriptors = results.flatMap { it.descriptors }
        val mixins = results.flatMap { it.mixins }
        BackendValidator(options.minecraftJars, logger).validate(descriptors, mixins)
        MixinGenerator(options, codeGenerator).generate(descriptors, mixins)
        reset()
    }

    override fun onError() {
        reset()
    }

    private fun reset() {
        results.clear()
        psiCompanion.destroy()
    }
}
