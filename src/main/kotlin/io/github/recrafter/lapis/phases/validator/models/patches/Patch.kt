package io.github.recrafter.lapis.phases.validator.models.patches

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import io.github.recrafter.lapis.annotations.InitStrategy
import io.github.recrafter.lapis.annotations.Side
import io.github.recrafter.lapis.common.JvmClassName
import io.github.recrafter.lapis.phases.validator.models.common.MixinAnnotation
import io.github.recrafter.lapis.phases.validator.models.common.SourceFile
import io.github.recrafter.lapis.phases.validator.models.patches.hooks.PatchHook

class Patch(
    symbol: KSNode,
    classDeclaration: KSClassDeclaration,
    val name: String,
    val side: Side,
    val initStrategy: InitStrategy,
    val isImplRequired: Boolean,
    val originClassDeclaration: KSClassDeclaration?,
    val constructorParameters: List<PatchConstructorParameter>,
    val extensionSources: List<PatchExtensionSource>,
    val shadowSources: List<PatchShadowSource>,
    val hooks: List<PatchHook>,
    val targetJvmClassName: JvmClassName?,
    val mixinAnnotations: List<MixinAnnotation>,
) : SourceFile(symbol, classDeclaration)
