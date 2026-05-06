package io.github.recrafter.lapis

import kotlinx.serialization.Serializable

@Serializable
data class LapisOptions(
    val modId: String,
    private val modPackage: String,
    private val mixinPackage: String,
    val isUnobfuscated: Boolean,
    val mixinConfig: String = "$modId.mixins.json",
    val accessWidenerConfig: String? = null,
    val accessTransformerConfig: String? = null,
) {
    val generatedModPackageName: String get() = "$modPackage.generated"
    val generatedMixinPackageName: String get() = "$mixinPackage.generated"
}
