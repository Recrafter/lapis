package io.github.recrafter.lapis

import kotlinx.serialization.Serializable

@Serializable
data class LapisOptions(
    val modId: String,
    private val packageName: String,
    val isUnobfuscated: Boolean,
    val mixinConfig: String = "$modId.mixins.json",
    val accessWidenerConfig: String? = null,
    val accessTransformerConfig: String? = null,
) {
    val generatedPackageName: String get() = "$packageName.generated"
    val mixinPackageName: String get() = "$generatedPackageName.mixin"
}
