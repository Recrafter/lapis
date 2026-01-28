package io.github.recrafter.lapis.utils

import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.common.unsafeLazy
import io.github.recrafter.lapis.extensions.ksp.KspFileLocation
import io.github.recrafter.lapis.extensions.ksp.KspSymbol
import io.github.recrafter.lapis.extensions.ksp.file
import io.github.recrafter.lapis.extensions.psi.PsiFactory
import io.github.recrafter.lapis.extensions.psi.PsiFile
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration

class PsiCompanion {

    private val factory: PsiFactory by unsafeLazy {
        val environment = KotlinCoreEnvironment.createForTests(
            disposable,
            CompilerConfiguration(),
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
        PsiFactory(environment.project)
    }
    private val disposable: Disposable by unsafeLazy { Disposer.newDisposable() }
    private val cache: MutableMap<String, PsiFile> = mutableMapOf()

    private var isInitialized: Boolean = false

    fun findPsiFile(symbol: KspSymbol?): PsiFile? {
        val file = symbol?.location?.castOrNull<KspFileLocation>()?.file ?: return null
        if (!file.isFile) {
            return null
        }
        return cache.getOrPut(file.canonicalPath) {
            val contents = file.readText().trim()
            if (contents.isEmpty()) {
                return null
            }
            isInitialized = true
            factory.createFile(file.name, contents)
        }
    }

    fun destroy() {
        if (!isInitialized) {
            return
        }
        Disposer.dispose(disposable)
        cache.clear()
    }
}
