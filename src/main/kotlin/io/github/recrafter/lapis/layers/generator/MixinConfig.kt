package io.github.recrafter.lapis.layers.generator

import io.github.recrafter.lapis.annotations.Side
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
    val dedicatedServerOnlyMixins: List<String>? = null,
) {
    @Serializable
    data class ExtrasConfig(val minVersion: String)

    @Serializable
    data class InjectorConfig(val defaultRequire: Int)

    @Serializable
    data class OverwriteConfig(val requireAnnotations: Boolean)

    companion object {
        fun of(mixinPackage: String, qualifiedNames: Map<Side, List<String>>): MixinConfig =
            MixinConfig(
                isRequired = true,
                minVersion = "0.5.7", // Unique
                extrasConfig = ExtrasConfig(
                    minVersion = "0.4.0" // WrapMethod
                ),
                mixinPackage = mixinPackage,
                javaVersion = "JAVA_8",
                injectorConfig = InjectorConfig(
                    defaultRequire = 1,
                ),
                overwriteConfig = OverwriteConfig(
                    requireAnnotations = true,
                ),
                commonMixins = qualifiedNames.getPackageRelativeClassNames(
                    Side.Common,
                    mixinPackage
                ),
                clientOnlyMixins = qualifiedNames.getPackageRelativeClassNames(
                    Side.ClientOnly,
                    mixinPackage
                ),
                dedicatedServerOnlyMixins = qualifiedNames.getPackageRelativeClassNames(
                    Side.DedicatedServerOnly,
                    mixinPackage
                ),
            )

        private fun Map<Side, List<String>>.getPackageRelativeClassNames(
            side: Side,
            packageName: String,
        ): List<String>? =
            get(side)
                ?.ifEmpty { null }
                ?.map { it.removePrefix("$packageName.") }
    }
}
