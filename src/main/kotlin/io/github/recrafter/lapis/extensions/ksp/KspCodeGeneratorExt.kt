package io.github.recrafter.lapis.extensions.ksp

import java.io.File

fun KspCodeGenerator.createResourceFile(
    path: String,
    contents: String,
    aggregating: Boolean = false,
) {
    val file = File(path)
    createNewFile(
        dependencies = KspDependencies(aggregating),
        packageName = file.parent?.replace(File.separator, ".").orEmpty(),
        fileName = file.nameWithoutExtension,
        extensionName = file.extension
    ).bufferedWriter().use { it.write(contents) }
}
