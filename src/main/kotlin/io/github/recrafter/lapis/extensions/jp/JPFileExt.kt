package io.github.recrafter.lapis.extensions.jp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSFile
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

fun JPFile.writeTo(codeGenerator: CodeGenerator, aggregating: Boolean, originatingFiles: Iterable<KSFile>) {
    val dependencies = Dependencies(aggregating, *originatingFiles.toList().toTypedArray())
    val file = codeGenerator.createNewFile(dependencies, packageName(), typeSpec().name(), "java")
    OutputStreamWriter(file, StandardCharsets.UTF_8).use(::writeTo)
}
