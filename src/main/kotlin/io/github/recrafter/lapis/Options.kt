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
    val mixinPackageName: String get() = "$packageName.mixin"
    val generatedPackageName: String get() = "$packageName.generated"
}
