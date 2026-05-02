package io.github.recrafter.lapis

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import io.github.recrafter.lapis.phases.LapisPhase
import io.github.recrafter.lapis.phases.builtins.Builtins
import io.github.recrafter.lapis.phases.generator.MixinGenerator
import io.github.recrafter.lapis.phases.lowering.MixinLowering
import io.github.recrafter.lapis.phases.lowering.models.IrPatch
import io.github.recrafter.lapis.phases.lowering.models.IrSchema
import io.github.recrafter.lapis.phases.parser.KSTypes
import io.github.recrafter.lapis.phases.parser.SymbolParser
import io.github.recrafter.lapis.phases.validator.FrontendValidator
import java.util.*

class LapisProcessor(
    private val options: Options,
    private val codeGenerator: CodeGenerator,
    private val logger: LapisLogger,
) : SymbolProcessor {

    private val builtins: Builtins = Builtins(options.generatedPackageName, codeGenerator)
    private val mixinLowering: MixinLowering = MixinLowering(options, builtins, logger)

    private val schemas: MutableList<IrSchema> = mutableListOf()
    private val patches: SortedMap<String, IrPatch> = sortedMapOf()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val types = KSTypes(resolver.builtIns)
        val parser = SymbolParser(resolver, types, logger)
        if (!builtins.isExternalGenerated) {
            logger.setPhase(LapisPhase.BUILTINS)
            builtins.generateExternal()
            return parser.prepare().run { schemaClassDeclarations + patchClassDeclarations }
        }

        logger.setPhase(LapisPhase.PARSING)
        val parserResult = parser.parse()

        logger.setPhase(LapisPhase.VALIDATION)
        val validatorResult = FrontendValidator(logger, options, builtins, types).validate(parserResult)

        logger.setPhase(LapisPhase.TRANSFORMATION)
        val irResult = mixinLowering.lower(validatorResult)
        irResult.schemas.forEach { schemas += it }
        irResult.patches.forEach { patches[it.className.qualifiedName] = it }

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
        MixinGenerator(options, builtins, codeGenerator, logger).generate(schemas, patches.values.toList())
        builtins.generateInternal()
    }
}
