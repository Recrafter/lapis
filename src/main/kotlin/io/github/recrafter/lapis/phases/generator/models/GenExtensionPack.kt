package io.github.recrafter.lapis.phases.generator.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.phases.lowering.models.IrKotlinFileBlueprint

class GenExtensionPack(
    override val originatingFiles: List<KSFile>,
    packageName: String,
    fileName: String,
) : IrKotlinFileBlueprint(packageName, fileName)
