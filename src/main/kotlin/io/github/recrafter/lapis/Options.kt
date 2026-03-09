package io.github.recrafter.lapis

import io.github.recrafter.lapis.extensions.common.unsafeLazy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Options(
    val modId: String,
    private val packageName: String,
    val refmapFileName: String,

    @SerialName("minecraftJars")
    private val jarPaths: String?,
) {
    val mixinPackageName: String
        get() = "$packageName.mixin"

    val generatedPackageName: String
        get() = "$packageName.generated"

    val jarFiles: List<File> by unsafeLazy {
        jarPaths
            ?.split(File.pathSeparator)
            ?.map { File(it.trim()) }
            ?.filter { it.isFile && it.extension == "jar" }
            .orEmpty()
    }
}
