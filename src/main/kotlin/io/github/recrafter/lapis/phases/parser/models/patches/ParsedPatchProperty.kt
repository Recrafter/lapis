package io.github.recrafter.lapis.phases.parser.models.patches

import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.extensions.jp.JPModifier
import io.github.recrafter.lapis.phases.parser.models.common.ParsedAnnotation
import io.github.recrafter.lapis.phases.parser.models.common.SymbolSource

class ParsedPatchProperty(
    override val symbol: KSNode,
    val name: String,
    val type: KSType?,
    val isPublic: Boolean,
    val isOpen: Boolean,
    val isAbstract: Boolean,
    val hasExtensionReceiver: Boolean,
    val explicitMappingName: String?,
    val hasExtensionAnnotation: Boolean,
    val hasShadowAnnotation: Boolean,
    val shadowModifiers: List<JPModifier>,
    val getter: ParsedPatchPropertyGetter?,
    val setter: ParsedPatchPropertySetter?,
) : SymbolSource

class ParsedPatchPropertyGetter(
    val jvmName: String?,
    val annotations: List<ParsedAnnotation>,
)

class ParsedPatchPropertySetter(
    val jvmName: String?,
)
