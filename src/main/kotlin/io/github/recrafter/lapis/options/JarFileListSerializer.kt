package io.github.recrafter.lapis.options

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.File

object JarFileListSerializer : KSerializer<List<File>> {

    override val descriptor: SerialDescriptor by lazy {
        PrimitiveSerialDescriptor(
            requireNotNull(JarFileListSerializer::class.qualifiedName),
            PrimitiveKind.STRING
        )
    }

    override fun deserialize(decoder: Decoder): List<File> =
        decoder.decodeString()
            .split(File.pathSeparator)
            .map { File(it.trim()) }
            .filter { it.isFile && it.extension == "jar" }

    override fun serialize(encoder: Encoder, value: List<File>) {}
}
