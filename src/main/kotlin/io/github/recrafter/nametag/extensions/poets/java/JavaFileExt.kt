package io.github.recrafter.nametag.extensions.poets.java

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.palantir.javapoet.JavaFile
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

fun JavaFile.writeTo(codeGenerator: CodeGenerator, dependencies: Dependencies) {
    val file = codeGenerator.createNewFile(dependencies, packageName(), typeSpec().name(), "java")
    OutputStreamWriter(file, StandardCharsets.UTF_8).use { writeTo(it) }
}
