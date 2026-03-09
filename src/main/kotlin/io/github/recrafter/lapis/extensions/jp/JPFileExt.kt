package io.github.recrafter.lapis.extensions.jp

import com.google.devtools.ksp.processing.CodeGenerator
import io.github.recrafter.lapis.extensions.ksp.KSPDependencies
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

fun JPFile.writeTo(codeGenerator: CodeGenerator, dependencies: KSPDependencies) {
    val file = codeGenerator.createNewFile(dependencies, packageName(), typeSpec().name(), "java")
    OutputStreamWriter(file, StandardCharsets.UTF_8).use { writeTo(it) }
}
