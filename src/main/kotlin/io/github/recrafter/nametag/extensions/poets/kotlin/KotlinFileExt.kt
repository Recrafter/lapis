package io.github.recrafter.nametag.extensions.poets.kotlin

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import io.github.recrafter.nametag.accessors.processor.KotlinFile
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

fun KotlinFile.writeTo(codeGenerator: CodeGenerator, dependencies: Dependencies) {
    val file = codeGenerator.createNewFile(dependencies, packageName, name)
    OutputStreamWriter(file, StandardCharsets.UTF_8).use { writeTo(it) }
}
