package io.github.recrafter.lapis

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import io.github.recrafter.lapis.extensions.ksp.KspAnnotated
import io.github.recrafter.lapis.extensions.ksp.KspLogger
import io.github.recrafter.lapis.layers.generator.MixinGenerator
import io.github.recrafter.lapis.layers.lowering.IrLowering
import io.github.recrafter.lapis.layers.lowering.IrResult
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
    private val results: MutableList<IrResult> = mutableListOf()

    override fun process(resolver: Resolver): List<KspAnnotated> {
        val parsedData = SymbolParser(resolver, psiCompanion, logger).parse()
        val validatedData = FrontendValidator(logger).validate(parsedData)
        results += IrLowering(options, logger).lower(validatedData)
        return emptyList()
    }

    override fun finish() {
        val descriptorImpls = results.flatMap { it.descriptorImpls }
        val rootMixins = results.flatMap { it.rootMixins }
        BackendValidator(options.minecraftJars, logger).validate(descriptorImpls, rootMixins)
        MixinGenerator(options, codeGenerator, logger).generate(descriptorImpls, rootMixins)
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
