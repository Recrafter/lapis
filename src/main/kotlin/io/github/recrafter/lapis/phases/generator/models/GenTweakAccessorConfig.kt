package io.github.recrafter.lapis.phases.generator.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.phases.lowering.models.IrResourceBlueprint

class GenTweakAccessorConfig(
    override val originatingFiles: List<KSFile>,
    path: String,
) : IrResourceBlueprint(path)
