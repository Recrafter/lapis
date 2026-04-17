package io.github.recrafter.lapis.extensions.kp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.AnnotationSpec.UseSiteTarget
import io.github.recrafter.lapis.phases.generator.KSuppressWarning
import io.github.recrafter.lapis.phases.generator.builders.Builder

inline fun <reified A : Annotation> KPFileBuilder.addAnnotation(
    useSiteTarget: UseSiteTarget? = null,
    builder: Builder<KPAnnotationBuilder> = {}
) {
    addAnnotation(buildKotlinAnnotation<A>(useSiteTarget, builder))
}

fun KPFile.writeTo(codeGenerator: CodeGenerator, dependencies: Dependencies) {
    codeGenerator.createNewFile(dependencies, packageName, name).writer().use { writeTo(it) }
}

fun KPFileBuilder.suppressWarnings(warnings: List<KSuppressWarning>) {
    addAnnotation<Suppress> {
        setStringVarargMember(
            Suppress::names,
            *warnings.map { it.suppressionKey }.toTypedArray()
        )
    }
}

fun KPFileBuilder.suppressWarnings(vararg warnings: KSuppressWarning) {
    suppressWarnings(warnings.toList())
}
