package io.github.recrafter.lapis.options

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Options(
    val modId: String,
    private val packageName: String,
    val refmapFileName: String,

    @Serializable(with = JarFileListSerializer::class)
    val minecraftJars: List<File> = emptyList(),
) {
    val mixinPackageName: String
        get() = "$packageName.mixin"

    val generatedPackageName: String
        get() = "$packageName.generated"
}
