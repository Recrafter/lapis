package io.github.recrafter.lapis.phases.validator.models.patches

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import io.github.recrafter.lapis.annotations.InitStrategy
import io.github.recrafter.lapis.annotations.Side
import io.github.recrafter.lapis.phases.validator.models.common.SourceFile
import io.github.recrafter.lapis.phases.validator.models.patches.hooks.PatchHook
import io.github.recrafter.lapis.phases.validator.models.schemas.Schema

class Patch(
    symbol: KSNode,
    classDeclaration: KSClassDeclaration,
    val name: String,
    val side: Side,
    val initStrategy: InitStrategy,
    val isImplRequired: Boolean,
    val schema: Schema,
    val constructorParameters: List<PatchConstructorParameter>,
    val extensionSources: List<PatchExtensionSource>,
    val shadowSources: List<PatchShadowSource>,
    val hooks: List<PatchHook>,
) : SourceFile(symbol, classDeclaration)
