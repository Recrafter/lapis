package io.github.recrafter.lapis.phases.generator

import io.github.recrafter.lapis.annotations.Side
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MixinConfig(
    @SerialName("required")
    val isRequired: Boolean,

    val minVersion: String,

    @SerialName("mixinextras")
    val extrasConfig: ExtrasConfig,

    @SerialName("package")
    val mixinPackage: String,

    @SerialName("compatibilityLevel")
    val javaVersion: String,

    @SerialName("injectors")
    val injectorConfig: InjectorConfig,

    @SerialName("overwrites")
    val overwriteConfig: OverwriteConfig,

    @SerialName("mixins")
    val commonMixins: List<String>? = null,

    @SerialName("client")
    val clientOnlyMixins: List<String>? = null,

    @SerialName("server")
    val serverOnlyMixins: List<String>? = null,
) {
    @Serializable
    data class ExtrasConfig(val minVersion: String)

    @Serializable
    data class InjectorConfig(val defaultRequire: Int)

    @Serializable
    data class OverwriteConfig(val requireAnnotations: Boolean)

    companion object {
        fun of(mixinPackage: String, qualifiedNames: Map<Side, List<IrClassName>>): MixinConfig =
            MixinConfig(
                isRequired = true,
                minVersion = "0.8.6",
                extrasConfig = ExtrasConfig(minVersion = "0.4.0"),
                mixinPackage = mixinPackage,
                javaVersion = "JAVA_8",
                injectorConfig = InjectorConfig(defaultRequire = 1),
                overwriteConfig = OverwriteConfig(requireAnnotations = true),
                commonMixins = qualifiedNames.getRelativeNames(Side.Common, mixinPackage),
                clientOnlyMixins = qualifiedNames.getRelativeNames(Side.ClientOnly, mixinPackage),
                serverOnlyMixins = qualifiedNames.getRelativeNames(Side.ServerOnly, mixinPackage),
            )

        private fun Map<Side, List<IrClassName>>.getRelativeNames(side: Side, basePackage: String): List<String>? =
            get(side)?.ifEmpty { null }?.map { it.qualifiedName.removePrefix("$basePackage.") }
    }
}
