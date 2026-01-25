package io.github.recrafter.lapis.utils

import io.github.recrafter.lapis.extensions.ksp.KspFileLocation
import io.github.recrafter.lapis.extensions.ksp.KspLogger
import io.github.recrafter.lapis.extensions.ksp.KspNode
import io.github.recrafter.lapis.extensions.ksp.file
import io.github.recrafter.lapis.extensions.psi.PsiFactory
import io.github.recrafter.lapis.extensions.psi.PsiFile
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class PsiCompanion(val logger: KspLogger) {

    private val factory: PsiFactory by lazy {
        val environment = KotlinCoreEnvironment.createForTests(
            disposable,
            CompilerConfiguration(),
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
        PsiFactory(environment.project)
    }

    private val disposable: Disposable = Disposer.newDisposable()
    private val cache: MutableMap<String, PsiFile> = mutableMapOf()

    @OptIn(UnsafeCastFunction::class)
    fun loadPsiFile(logger: KspLogger, node: KspNode): PsiFile {
        val file = node.location.safeAs<KspFileLocation>()?.file ?: resolvingError(logger, node)
        if (!file.exists()) {
            resolvingError(logger, node)
        }
        return cache.getOrPut(file.canonicalPath) {
            val contents = file.readText()
            if (contents.trim().isEmpty()) {
                resolvingError(logger, node)
            }
            factory.createFile(file.name, contents)
        }
    }

    fun destroy() {
        Disposer.dispose(disposable)
        cache.clear()
    }

    fun resolvingError(logger: KspLogger, node: KspNode): Nothing {
        val message = "Unable to resolve KSP node in PSI model."
        logger.error(message, node)
        throw IllegalStateException(message)
    }
}
