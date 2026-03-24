package io.github.recrafter.lapis.extensions.ksp

import java.io.File

fun KSPCodeGenerator.createResourceFile(
    path: String,
    contents: String,
    aggregating: Boolean = false,
    containingFiles: List<KSPFile> = emptyList(),
) {
    val file = File(path)
    createNewFile(
        dependencies = containingFiles.toDependencies(aggregating),
        packageName = file.parent?.replace(File.separatorChar, '.').orEmpty(),
        fileName = file.nameWithoutExtension,
        extensionName = file.extension,
    ).bufferedWriter().use { it.write(contents) }
}
