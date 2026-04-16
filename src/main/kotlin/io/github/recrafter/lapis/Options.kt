package io.github.recrafter.lapis

import kotlinx.serialization.Serializable

@Serializable
data class Options(
    val modId: String,
    private val packageName: String,
    val isUnobfuscated: Boolean,
    val mixinConfigName: String = "$modId.mixins.json",
    val accessWidenerConfigName: String? = null,
    val accessTransformerConfigName: String? = null,
) {
    val generatedPackageName: String get() = "$packageName.generated"
    val mixinPackageName: String get() = "$generatedPackageName.mixin"
}
