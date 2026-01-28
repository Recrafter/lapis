package io.github.recrafter.lapis.layers.generator

import io.github.recrafter.lapis.annotations.enums.LapisPatchSide
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MixinConfig(
    @SerialName("required")
    val isRequired: Boolean,

    @SerialName("package")
    val mixinPackage: String,

    @SerialName("compatibilityLevel")
    val javaVersion: String,

    @SerialName("injectors")
    val injectorConfig: InjectorConfig,

    @SerialName("overwrites")
    val overwriteConfig: OverwriteConfig,

    @SerialName("refmap")
    val refmapFileName: String,

    @SerialName("mixins")
    val commonMixins: List<String>? = null,

    @SerialName("client")
    val clientOnlyMixins: List<String>? = null,

    @SerialName("server")
    val dedicatedServerOnlyMixins: List<String>? = null,
) {
    @Serializable
    data class InjectorConfig(val defaultRequire: Int)

    @Serializable
    data class OverwriteConfig(val requireAnnotations: Boolean)

    companion object {
        fun of(
            mixinPackage: String,
            refmapFileName: String,
            qualifiedNames: Map<LapisPatchSide, List<String>>,
        ): MixinConfig =
            MixinConfig(
                isRequired = true,
                mixinPackage = mixinPackage,
                javaVersion = "JAVA_8",
                injectorConfig = InjectorConfig(
                    defaultRequire = 1,
                ),
                overwriteConfig = OverwriteConfig(
                    requireAnnotations = true,
                ),
                refmapFileName = refmapFileName,
                commonMixins = qualifiedNames.getPackageRelativeClassNames(
                    LapisPatchSide.Common,
                    mixinPackage
                ),
                clientOnlyMixins = qualifiedNames.getPackageRelativeClassNames(
                    LapisPatchSide.ClientOnly,
                    mixinPackage
                ),
                dedicatedServerOnlyMixins = qualifiedNames.getPackageRelativeClassNames(
                    LapisPatchSide.DedicatedServerOnly,
                    mixinPackage
                ),
            )

        private fun Map<LapisPatchSide, List<String>>.getPackageRelativeClassNames(
            side: LapisPatchSide,
            packageName: String,
        ): List<String>? =
            get(side)?.ifEmpty { null }?.map { it.removePrefix("$packageName.").replace(".", "$") }
    }
}
