package io.github.recrafter.lapis.phases.generator.models

import io.github.recrafter.lapis.phases.generator.builders.KPEntity
import io.github.recrafter.lapis.phases.lowering.models.IrParameter

class GenDescriptorWrapperImplResult(
    val constructorParameters: List<IrParameter>,
    val extensionPackEntities: List<KPEntity>,
)
