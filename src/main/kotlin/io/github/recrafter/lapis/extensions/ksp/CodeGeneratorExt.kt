package io.github.recrafter.lapis.extensions.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import java.io.File

fun CodeGenerator.createResourceFile(
    path: String,
    contents: String,
    aggregating: Boolean = false,
) {
    val file = File(path)
    createNewFile(
        dependencies = Dependencies(aggregating),
        packageName = file.parent?.replace(File.separatorChar, '.').orEmpty(),
        fileName = file.nameWithoutExtension,
        extensionName = file.extension,
    ).bufferedWriter().use { it.write(contents.trimEnd() + "\n") }
}
