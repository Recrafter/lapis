package io.github.recrafter.lapis.config

import io.github.recrafter.lapis.api.LapisPatchSide
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
    val packageName: String,

    @SerialName("compatibilityLevel")
    val jvmTargetVersion: String,

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
    class ExtrasConfig(val minVersion: String)

    @Serializable
    data class InjectorConfig(val defaultRequire: Int)

    @Serializable
    data class OverwriteConfig(val requireAnnotations: Boolean)

    companion object {
        fun of(
            packageName: String,
            refmapFileName: String,
            mixinQualifiedNames: Map<LapisPatchSide, List<String>>,
        ): MixinConfig =
            MixinConfig(
                isRequired = true,
                packageName = packageName,
                minVersion = "0.8.7",
                extrasConfig = ExtrasConfig(
                    minVersion = "0.5.3"
                ),
                jvmTargetVersion = "JAVA_8",
                injectorConfig = InjectorConfig(
                    defaultRequire = 1,
                ),
                overwriteConfig = OverwriteConfig(
                    requireAnnotations = true,
                ),
                refmapFileName = refmapFileName,
                commonMixins = mixinQualifiedNames[LapisPatchSide.Common]?.ifEmpty { null }
                    ?.map { it.removePrefix("$packageName.") },
                clientOnlyMixins = mixinQualifiedNames[LapisPatchSide.ClientOnly]?.ifEmpty { null }
                    ?.map { it.removePrefix("$packageName.") },
                dedicatedServerOnlyMixins = mixinQualifiedNames[LapisPatchSide.DedicatedServerOnly]?.ifEmpty { null }
                    ?.map { it.removePrefix("$packageName.") },
            )
    }
}