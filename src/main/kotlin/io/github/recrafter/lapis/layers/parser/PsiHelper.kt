package io.github.recrafter.lapis.layers.parser

import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.common.unsafeLazy
import io.github.recrafter.lapis.extensions.ksp.KSPFileLocation
import io.github.recrafter.lapis.extensions.ksp.KSPSymbol
import io.github.recrafter.lapis.extensions.ksp.file
import io.github.recrafter.lapis.extensions.psi.PSIFactory
import io.github.recrafter.lapis.extensions.psi.PSIFile
import io.github.recrafter.lapis.extensions.quoted
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration

object PsiHelper {

    @OptIn(K1Deprecation::class)
    private val factory: PSIFactory by unsafeLazy {
        val environment = KotlinCoreEnvironment.createForTests(
            Disposer.newDisposable(),
            CompilerConfiguration(),
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
        PSIFactory(environment.project)
    }

    private val cache: MutableMap<String, PSIFile> = mutableMapOf()

    fun findPsiFile(symbol: KSPSymbol): Pair<PSIFile, Int> {
        val location = symbol.location.castOrNull<KSPFileLocation>()
        val file = location?.file?.takeIf { it.isFile }
            ?: lapisError("Symbol ${symbol.toString().quoted()} does not have a valid file location.")
        return cache.getOrPut(file.canonicalPath) {
            runCatching {
                factory.createFile(file.name, file.readText())
            }.getOrElse { error ->
                lapisError("Failed to create PSI file for ${file.path.quoted()}: ${error.message}")
            }
        } to location.lineNumber
    }
}
